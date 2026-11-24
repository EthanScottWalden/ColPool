import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  ROUTES_ENDPOINT,
  ROUTES_FIELD_MASK,
  RoutesRejectedError,
  RoutesUnavailableError,
  buildComputeRoutesRequest,
  createHttpRoutesClient,
  parseComputeRoutesResponse,
  type FetchLike,
} from "../../src/routePreview/routesClient.ts";

const A = { lat: 33.9, lng: -117.4 };
const S1 = { lat: 33.91, lng: -117.41 };
const S2 = { lat: 33.92, lng: -117.42 };
const C = { lat: 33.929, lng: -117.427 };

describe("Compute Routes request format (layer 2)", () => {
  it("builds the documented body: origin, intermediates in order, destination, DRIVE, TRAFFIC_UNAWARE, no departureTime", () => {
    const body = buildComputeRoutesRequest([A, S1, S2, C]);
    assert.deepEqual(body, {
      origin: { location: { latLng: { latitude: 33.9, longitude: -117.4 } } },
      destination: { location: { latLng: { latitude: 33.929, longitude: -117.427 } } },
      intermediates: [
        { location: { latLng: { latitude: 33.91, longitude: -117.41 } } },
        { location: { latLng: { latitude: 33.92, longitude: -117.42 } } },
      ],
      travelMode: "DRIVE",
      routingPreference: "TRAFFIC_UNAWARE",
      computeAlternativeRoutes: false,
    });
    assert.ok(!("departureTime" in body));
  });

  it("sends endpoint, key header, exact field mask and a 10 s abort signal", async () => {
    let seen: { url: string; init: RequestInit } | undefined;
    const fetchImpl: FetchLike = async (url, init) => {
      seen = { url, init };
      return new Response(JSON.stringify({ routes: [{ legs: [{ distanceMeters: 10, duration: "60s" }] }] }), { status: 200 });
    };
    await createHttpRoutesClient("test-key-not-real", fetchImpl).computeRoute([A, C]);
    assert.equal(seen!.url, "https://routes.googleapis.com/directions/v2:computeRoutes");
    assert.equal(ROUTES_ENDPOINT, seen!.url);
    assert.equal(seen!.init.method, "POST");
    assert.deepEqual(seen!.init.headers, {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": "test-key-not-real",
      "X-Goog-FieldMask": "routes.legs.distanceMeters,routes.legs.duration",
    });
    assert.equal(ROUTES_FIELD_MASK, "routes.legs.distanceMeters,routes.legs.duration");
    assert.ok(seen!.init.signal instanceof AbortSignal);
    assert.deepEqual(JSON.parse(seen!.init.body as string), buildComputeRoutesRequest([A, C]));
  });

  it("maps statuses: 429 and 5xx unavailable, other 4xx rejected, network error unavailable", async () => {
    const withStatus = (status: number) => createHttpRoutesClient("k", async () => new Response("{}", { status })).computeRoute([A, C]);
    await assert.rejects(withStatus(429), RoutesUnavailableError);
    await assert.rejects(withStatus(503), RoutesUnavailableError);
    await assert.rejects(withStatus(403), (e: RoutesRejectedError) => e instanceof RoutesRejectedError && e.status === 403);
    await assert.rejects(createHttpRoutesClient("k", async () => { throw new Error("boom with key k"); }).computeRoute([A, C]), (e: Error) => {
      assert.ok(e instanceof RoutesUnavailableError);
      assert.ok(!e.message.includes("boom"));
      return true;
    });
  });
});

describe("Compute Routes response parsing (layer 2)", () => {
  const fixtureText = readFileSync(new URL("../fixtures/routes/riverside-1stop.json", import.meta.url), "utf8");

  it("parses the saved response fixture", () => {
    const r = parseComputeRoutesResponse(JSON.parse(fixtureText), 2);
    assert.ok(r.ok);
    assert.deepEqual(r.legs, [
      { meters: 5230, durationSec: 612 },
      { meters: 3118, durationSec: 447 },
    ]);
  });

  it("the fixture holds no key", () => {
    assert.ok(!fixtureText.includes("AIza"));
    assert.ok(!/key/i.test(fixtureText));
  });

  it("no routes means no route; missing fields are 0; fractional seconds parse", () => {
    assert.deepEqual(parseComputeRoutesResponse({}, 1), { ok: false });
    assert.deepEqual(parseComputeRoutesResponse({ routes: [] }, 1), { ok: false });
    assert.deepEqual(parseComputeRoutesResponse({ routes: [{ legs: [{}] }] }, 1), { ok: true, legs: [{ meters: 0, durationSec: 0 }] });
    assert.deepEqual(parseComputeRoutesResponse({ routes: [{ legs: [{ distanceMeters: 5, duration: "123.4s" }] }] }, 1), {
      ok: true,
      legs: [{ meters: 5, durationSec: 123.4 }],
    });
  });

  it("an unexpected shape is rejected, not treated as no route", () => {
    assert.throws(() => parseComputeRoutesResponse({ routes: [{ legs: [{}, {}] }] }, 1), RoutesRejectedError);
    assert.throws(() => parseComputeRoutesResponse({ routes: [{}] }, 1), RoutesRejectedError);
    assert.throws(() => parseComputeRoutesResponse({ routes: [{ legs: [{ duration: "12m" }] }] }, 1), RoutesRejectedError);
    assert.throws(() => parseComputeRoutesResponse("x", 1), RoutesRejectedError);
  });
});
