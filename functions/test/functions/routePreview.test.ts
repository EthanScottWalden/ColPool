import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { createRoutePreviewHandler, type RoutePreviewLogger } from "../../src/routePreview.ts";
import { FakeRoutesClient, RoutesRejectedError, RoutesUnavailableError, type LatLng } from "../../src/routePreview/routesClient.ts";
import { buildRouteResult } from "../../src/routePreview/times.ts";
import { CAMPUS_POINT_SEED } from "../../src/shared/schema/campusPoints.ts";
import { COLLECTIONS } from "../../src/shared/schema/collections.ts";
import type { PlaceAddress, SeriesInput } from "../../src/shared/schema/series.ts";
import { STUDENT_GATE_REASONS } from "../../src/shared/auth/reasons.ts";
import { hashSeriesInput } from "../../src/shared/schema/seriesHash.ts";

const db = getFirestore(adminApp());
// Fixed clock: Monday 2026-11-23 12:00 PST.
const NOW = new Date("2026-11-23T20:00:00Z");
const YEAGER = CAMPUS_POINT_SEED[0]!;

const PICKUP: PlaceAddress = { text: "1 Pickup Way, Riverside, CA", placeId: "test-pickup", lat: 33.9, lng: -117.4 };
const STOP1: PlaceAddress = { text: "2 Stop One St, Riverside, CA", placeId: "test-stop1", lat: 33.91, lng: -117.41 };
const STOP2: PlaceAddress = { text: "3 Stop Two St, Riverside, CA", placeId: "test-stop2", lat: 33.92, lng: -117.42 };
const HONOLULU: PlaceAddress = { text: "9 Far Away Rd, Honolulu, HI", placeId: "test-hnl", lat: 21.3, lng: -157.85 };

const key = (p: LatLng) => String(p.lat);
// Fixed leg table (seconds). Unlisted hops: 600 s, 1000 m.
const TABLE: Record<string, number> = {
  [`${PICKUP.lat}>${STOP1.lat}`]: 330, // 5.5 min rounds up to 6
  [`${STOP1.lat}>${YEAGER.lat}`]: 449, // 7.48 min rounds to 7
  [`${YEAGER.lat}>${STOP2.lat}`]: 90, // 1.5 min rounds to 2
  [`${STOP2.lat}>${STOP1.lat}`]: 150, // 2.5 min rounds to 3
  [`${STOP1.lat}>${PICKUP.lat}`]: 600,
};
const leg = (a: LatLng, b: LatLng) => ({ meters: 1000, durationSec: TABLE[`${key(a)}>${key(b)}`] ?? 600 });

function leginput(departTime: string, stops: { address: PlaceAddress; minutes: number }[] = []) {
  return { departTime, campusPointId: "yeager-center", stops };
}

function series(overrides: Partial<SeriesInput> = {}): SeriesInput {
  return {
    pickupSpot: PICKUP,
    startDate: "2026-11-23",
    endDate: "2026-12-18",
    priceDollars: 5,
    vehicle: "Test Car",
    maxRiders: 3,
    weekdays: ["mon"],
    days: {
      mon: {
        toCampus: leginput("07:30", [{ address: STOP1, minutes: 3 }]),
        // 07:30 + 6 + 3 + 7 = 466 = 07:46
        fromCampus: leginput("07:46", [{ address: STOP2, minutes: 4 }, { address: STOP1, minutes: 0 }]),
      },
    },
    ...overrides,
  };
}

let n = 0;
async function student(consent = true, token: Record<string, unknown> = {}): Promise<CallableRequest<unknown>["auth"]> {
  const uid = `rp-test-${Date.now()}-${n++}`;
  if (consent) await db.collection(COLLECTIONS.consents).doc(uid).set({ agreedAt: Timestamp.now() });
  return { uid, token: token as never, rawToken: "x" };
}

function req(auth: CallableRequest<unknown>["auth"], data: unknown): CallableRequest<unknown> {
  return { data, auth, rawRequest: {} as CallableRequest["rawRequest"], acceptsStreaming: false };
}

function logs() {
  const lines: string[] = [];
  const rec = (level: string) => (message: string, data?: Record<string, unknown>) => void lines.push(JSON.stringify({ level, message, data }));
  const logger: RoutePreviewLogger = { info: rec("info"), error: rec("error") };
  return { logger, text: () => lines.join("\n") };
}

function setup(client: FakeRoutesClient, now = NOW) {
  const l = logs();
  return { handler: createRoutePreviewHandler({ db, routesClient: client, now: () => now, logger: l.logger }), l };
}

async function rejects(p: Promise<unknown>, code: string, check?: (details: any) => void): Promise<void> {
  await assert.rejects(p, (e: { code?: string; details?: unknown }) => {
    assert.equal(e.code, code);
    check?.(e.details);
    return true;
  });
}

describe("routePreview (layer 1: fake Routes client)", () => {
  before(async () => {
    for (const p of CAMPUS_POINT_SEED) {
      const { id, ...rest } = p;
      await db.collection(COLLECTIONS.campusPoints).doc(id).set(rest);
    }
  });

  it("orders waypoints per direction, each with its own stops (not reversed)", async () => {
    const fake = new FakeRoutesClient({ leg });
    const { handler } = setup(fake);
    await handler(req(await student(), { series: series() }));
    const campus = { lat: YEAGER.lat, lng: YEAGER.lng };
    assert.deepEqual(fake.calls, [
      [{ lat: PICKUP.lat, lng: PICKUP.lng }, { lat: STOP1.lat, lng: STOP1.lng }, campus],
      [campus, { lat: STOP2.lat, lng: STOP2.lng }, { lat: STOP1.lat, lng: STOP1.lng }, { lat: PICKUP.lat, lng: PICKUP.lng }],
    ]);
  });

  it("computes expected times per hop with .5 rounding up and stop minutes", async () => {
    const { handler } = setup(new FakeRoutesClient({ leg }));
    const out = await handler(req(await student(), { series: series() }));
    const to = out.route.mon!.toCampus;
    assert.deepEqual(to.times, [{ departMin: 450 }, { arriveMin: 456, departMin: 459 }, { arriveMin: 466 }]);
    assert.equal(to.totalMeters, 2000);
    assert.equal(to.totalDurationSec, 779);
    const from = out.route.mon!.fromCampus;
    // 07:46 = 466; +2 -> 468 arrive, +4 -> 472; +3 -> 475 arrive, +0 -> 475; +10 -> 485
    assert.deepEqual(from.times, [{ departMin: 466 }, { arriveMin: 468, departMin: 472 }, { arriveMin: 475, departMin: 475 }, { arriveMin: 485 }]);
  });

  it("buildRouteResult keeps values past midnight", () => {
    const r = buildRouteResult(1430, [{ meters: 1, durationSec: 1200 }], []);
    assert.deepEqual(r.times, [{ departMin: 1430 }, { arriveMin: 1450 }]);
  });

  it("a campus departure equal to the arrival passes", async () => {
    const { handler } = setup(new FakeRoutesClient({ leg }));
    await handler(req(await student(), { series: series() }));
  });

  it("a campus departure one minute early is a series-invalid error and writes no draft", async () => {
    const early = series();
    early.days.mon!.fromCampus.departTime = "07:45";
    const before = (await db.collection(COLLECTIONS.seriesDrafts).get()).size;
    const { handler } = setup(new FakeRoutesClient({ leg }));
    await rejects(handler(req(await student(), { series: early })), "invalid-argument", (d) => {
      assert.deepEqual(d.errors.map((e: any) => [e.code, e.path]), [["campusDepartureBeforeArrival", "days.mon.fromCampus.departTime"]]);
    });
    assert.equal((await db.collection(COLLECTIONS.seriesDrafts).get()).size, before);
  });

  it("rejects invalid series input with #23 errors and makes no Routes call", async () => {
    const fake = new FakeRoutesClient({ leg });
    const { handler } = setup(fake);
    await rejects(handler(req(await student(), { series: series({ vehicle: "" }) })), "invalid-argument", (d) => {
      assert.equal(d.errors[0].code, "required");
      assert.equal(d.errors[0].path, "vehicle");
    });
    assert.equal(fake.calls.length, 0);
  });

  it("names the pickup spot when it has no route", async () => {
    const fake = new FakeRoutesClient({ leg, noRouteIf: (pts) => pts.some((p) => p.lat === HONOLULU.lat) });
    const { handler, l } = setup(fake);
    await rejects(handler(req(await student(), { series: series({ pickupSpot: HONOLULU }) })), "invalid-argument", (d) => {
      assert.deepEqual(d.errors, [{ code: "routeNotFound", path: "pickupSpot" }]);
    });
    assert.ok(!l.text().includes("Honolulu"));
    assert.ok(!l.text().includes(String(HONOLULU.lat)));
  });

  it("names a stop when only that stop has no route", async () => {
    const s = series();
    s.days.mon!.toCampus.stops = [{ address: HONOLULU, minutes: 3 }];
    const fake = new FakeRoutesClient({ leg, noRouteIf: (pts) => pts.some((p) => p.lat === HONOLULU.lat) });
    const { handler } = setup(fake);
    await rejects(handler(req(await student(), { series: s })), "invalid-argument", (d) => {
      assert.deepEqual(d.errors, [{ code: "routeNotFound", path: "days.mon.toCampus.stops.0.address" }]);
    });
  });

  it("names the leg when no single address is at fault", async () => {
    const fake = new FakeRoutesClient({ leg, noRouteIf: (pts) => pts.length >= 3 });
    const { handler } = setup(fake);
    await rejects(handler(req(await student(), { series: series() })), "invalid-argument", (d) => {
      assert.deepEqual(d.errors, [{ code: "routeNotFound", path: "days.mon.toCampus" }, { code: "routeNotFound", path: "days.mon.fromCampus" }]);
    });
  });

  it("calls Routes once per distinct leg: Monday to Friday identical gives 2 calls", async () => {
    const day = series().days.mon!;
    const s = series({ weekdays: ["mon", "tue", "wed", "thu", "fri"], days: { mon: day, tue: day, wed: day, thu: day, fri: day } });
    const fake = new FakeRoutesClient({ leg });
    const { handler } = setup(fake);
    const out = await handler(req(await student(), { series: s }));
    assert.equal(fake.calls.length, 2);
    assert.deepEqual(Object.keys(out.route), ["mon", "tue", "wed", "thu", "fri"]);
  });

  it("creates a draft, then an overwrite keeps createdAt and the same id", async () => {
    const auth = await student();
    const { handler } = setup(new FakeRoutesClient({ leg }));
    const first = await handler(req(auth, { series: series() }));
    assert.match(first.draftId, /^[A-Za-z0-9]{20}$/);
    assert.equal(first.inputHash, hashSeriesInput(series()));
    const doc1 = (await db.collection(COLLECTIONS.seriesDrafts).doc(first.draftId).get()).data()!;
    assert.equal(doc1["hostId"], auth!.uid);
    assert.equal(doc1["status"], "reviewed");
    assert.equal(doc1["inputHash"], first.inputHash);

    const changed = series({ priceDollars: 7 });
    const second = await handler(req(auth, { series: changed, draftId: first.draftId }));
    assert.equal(second.draftId, first.draftId);
    const doc2 = (await db.collection(COLLECTIONS.seriesDrafts).doc(first.draftId).get()).data()!;
    assert.equal(doc2["input"].priceDollars, 7);
    assert.equal(doc2["inputHash"], hashSeriesInput(changed));
    assert.ok(doc2["createdAt"].isEqual(doc1["createdAt"]));
    assert.ok(doc2["updatedAt"].toMillis() >= doc1["updatedAt"].toMillis());
  });

  it("a draft that is missing or someone else's gives the same not-found, before any Routes call", async () => {
    const owner = await student();
    const { handler } = setup(new FakeRoutesClient({ leg }));
    const { draftId } = await handler(req(owner, { series: series() }));
    const fake = new FakeRoutesClient({ leg });
    const { handler: h2 } = setup(fake);
    await rejects(h2(req(await student(), { series: series(), draftId })), "not-found");
    await rejects(h2(req(owner, { series: series(), draftId: "A".repeat(20) })), "not-found");
    assert.equal(fake.calls.length, 0);
  });

  it("a posted draft is failed-precondition and is not overwritten", async () => {
    const auth = await student();
    const { handler } = setup(new FakeRoutesClient({ leg }));
    const { draftId } = await handler(req(auth, { series: series() }));
    await db.collection(COLLECTIONS.seriesDrafts).doc(draftId).update({ status: "posted" });
    const fake = new FakeRoutesClient({ leg });
    await rejects(setup(fake).handler(req(auth, { series: series({ priceDollars: 9 }), draftId })), "failed-precondition", (d) => {
      assert.equal(d.reason, "draft-posted");
    });
    assert.equal(fake.calls.length, 0);
    assert.equal((await db.collection(COLLECTIONS.seriesDrafts).doc(draftId).get()).data()!["input"].priceDollars, 5);
  });

  it("rejects a malformed draft id", async () => {
    await rejects(setup(new FakeRoutesClient({ leg })).handler(req(await student(), { series: series(), draftId: "../x" })), "invalid-argument");
  });

  it("Routes 5xx or timeout is unavailable; a refusal is internal; no draft is written", async () => {
    const before = (await db.collection(COLLECTIONS.seriesDrafts).get()).size;
    const auth = await student();
    const down = setup(new FakeRoutesClient({ failWith: new RoutesUnavailableError(503) }));
    await rejects(down.handler(req(auth, { series: series() })), "unavailable", (d) => assert.equal(d.reason, "routes-unavailable"));
    assert.ok(down.l.text().includes("503"));
    await rejects(setup(new FakeRoutesClient({ failWith: new RoutesRejectedError(403) })).handler(req(auth, { series: series() })), "internal");
    assert.equal((await db.collection(COLLECTIONS.seriesDrafts).get()).size, before);
  });

  it("gates: unauthenticated, no consent, institution account", async () => {
    const fake = new FakeRoutesClient({ leg });
    const { handler } = setup(fake);
    await rejects(handler(req(undefined, { series: series() })), "unauthenticated");
    await rejects(handler(req(await student(false), { series: series() })), "permission-denied", (d) => assert.equal(d.reason, STUDENT_GATE_REASONS.consentMissing));
    await rejects(handler(req(await student(true, { institution: true }), { series: series() })), "permission-denied", (d) => assert.equal(d.reason, STUDENT_GATE_REASONS.institutionAccount));
    assert.equal(fake.calls.length, 0);
  });

  it("gives the same wall-clock times in the week the clocks go back (2026-11-01)", async () => {
    const dst = series({ startDate: "2026-10-30", endDate: "2026-11-09" });
    const plain = series();
    const a = await setup(new FakeRoutesClient({ leg }), new Date("2026-10-30T19:00:00Z")).handler(req(await student(), { series: dst }));
    const b = await setup(new FakeRoutesClient({ leg })).handler(req(await student(), { series: plain }));
    assert.deepEqual(a.route, b.route);
  });
});
