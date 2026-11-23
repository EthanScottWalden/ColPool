import { canonicalJson } from "../shared/schema/seriesHash.ts";

// Google Routes API (Compute Routes) client for the route-preview function (#28, ADR 0008).
// The only place in the repo that calls the Routes API (RC-28). The key is passed in; it is
// never logged and never part of an error.

export interface LatLng {
  lat: number;
  lng: number;
}

export interface RouteLeg {
  meters: number;
  durationSec: number;
}

export type RouteOutcome = { ok: true; legs: RouteLeg[] } | { ok: false };

/** One Compute Routes call: origin, optional stops, destination. `ok: false` means no driving route. */
export interface RoutesClient {
  computeRoute(points: LatLng[]): Promise<RouteOutcome>;
}

/** Routes answered 429 or 5xx, or did not answer in time. The caller may retry later. */
export class RoutesUnavailableError extends Error {
  status: number | undefined;
  constructor(status?: number) {
    super("routes-unavailable");
    this.status = status;
  }
}

/** Routes refused the request (other 4xx) or answered something unusable. A bug or a key problem, not the host's fault. */
export class RoutesRejectedError extends Error {
  status: number | undefined;
  constructor(status?: number) {
    super("routes-rejected");
    this.status = status;
  }
}

export const ROUTES_ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes";
export const ROUTES_FIELD_MASK = "routes.legs.distanceMeters,routes.legs.duration";
export const ROUTES_TIMEOUT_MS = 10_000;

interface Waypoint {
  location: { latLng: { latitude: number; longitude: number } };
}

function waypoint(p: LatLng): Waypoint {
  return { location: { latLng: { latitude: p.lat, longitude: p.lng } } };
}

/** The Compute Routes request body for `points` (at least two). No departureTime: no live traffic (RC-14). */
export function buildComputeRoutesRequest(points: readonly LatLng[]): Record<string, unknown> {
  if (points.length < 2) throw new Error("a route needs at least two points");
  return {
    origin: waypoint(points[0] as LatLng),
    destination: waypoint(points[points.length - 1] as LatLng),
    intermediates: points.slice(1, -1).map(waypoint),
    travelMode: "DRIVE",
    routingPreference: "TRAFFIC_UNAWARE",
    computeAlternativeRoutes: false,
  };
}

const DURATION_RE = /^(\d+(?:\.\d+)?)s$/;

/**
 * Parses a 200 response body. A body with no routes means no driving route. Missing
 * `distanceMeters` or `duration` is 0 (proto3 JSON omits zero values). A body that is not
 * the documented shape, or has the wrong number of legs, throws RoutesRejectedError.
 */
export function parseComputeRoutesResponse(body: unknown, expectedLegs: number): RouteOutcome {
  if (typeof body !== "object" || body === null) throw new RoutesRejectedError(200);
  const routes = (body as { routes?: unknown }).routes;
  if (routes === undefined || (Array.isArray(routes) && routes.length === 0)) return { ok: false };
  if (!Array.isArray(routes)) throw new RoutesRejectedError(200);
  const legsRaw = (routes[0] as { legs?: unknown } | null)?.legs;
  if (!Array.isArray(legsRaw) || legsRaw.length !== expectedLegs) throw new RoutesRejectedError(200);
  const legs: RouteLeg[] = legsRaw.map((l: unknown) => {
    const o = (typeof l === "object" && l !== null ? l : {}) as { distanceMeters?: unknown; duration?: unknown };
    const meters = o.distanceMeters === undefined ? 0 : o.distanceMeters;
    let durationSec = 0;
    if (o.duration !== undefined) {
      const m = typeof o.duration === "string" ? DURATION_RE.exec(o.duration) : null;
      if (!m) throw new RoutesRejectedError(200);
      durationSec = parseFloat(m[1] as string);
    }
    if (typeof meters !== "number" || !Number.isFinite(meters) || meters < 0) throw new RoutesRejectedError(200);
    return { meters, durationSec };
  });
  return { ok: true, legs };
}

export type FetchLike = (url: string, init: RequestInit) => Promise<Response>;

export function createHttpRoutesClient(apiKey: string, fetchImpl: FetchLike = fetch): RoutesClient {
  return {
    async computeRoute(points) {
      let res: Response;
      try {
        res = await fetchImpl(ROUTES_ENDPOINT, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": apiKey,
            "X-Goog-FieldMask": ROUTES_FIELD_MASK,
          },
          body: JSON.stringify(buildComputeRoutesRequest(points)),
          signal: AbortSignal.timeout(ROUTES_TIMEOUT_MS),
        });
      } catch {
        // Timeout or network failure. The error text is dropped on purpose.
        throw new RoutesUnavailableError();
      }
      if (res.status === 429 || res.status >= 500) throw new RoutesUnavailableError(res.status);
      if (!res.ok) throw new RoutesRejectedError(res.status);
      let body: unknown;
      try {
        body = await res.json();
      } catch {
        throw new RoutesRejectedError(res.status);
      }
      return parseComputeRoutesResponse(body, points.length - 1);
    },
  };
}

/** Within one callable invocation, identical point lists give identical results (ADR 0008, RC-28). */
export function dedupeRoutesClient(inner: RoutesClient): RoutesClient {
  const cache = new Map<string, Promise<RouteOutcome>>();
  return {
    computeRoute(points) {
      const key = canonicalJson(points);
      let hit = cache.get(key);
      if (!hit) {
        hit = inner.computeRoute(points);
        cache.set(key, hit);
      }
      return hit;
    },
  };
}

// ---- Fakes (tests and the Functions emulator; never used against production) ----

const EARTH_M = 6_371_000;

/** Straight-line leg: great-circle distance times 1.3, at 11 m/s (about 25 mph). Emulator only. */
export function straightLineLeg(a: LatLng, b: LatLng): RouteLeg {
  const rad = (d: number) => (d * Math.PI) / 180;
  const h =
    Math.sin(rad(b.lat - a.lat) / 2) ** 2 +
    Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(rad(b.lng - a.lng) / 2) ** 2;
  const meters = Math.round(2 * EARTH_M * Math.asin(Math.sqrt(h)) * 1.3);
  return { meters, durationSec: Math.round(meters / 11) };
}

export interface FakeRoutesOptions {
  /** One hop between consecutive points. Default: `straightLineLeg`. */
  leg?: (a: LatLng, b: LatLng) => RouteLeg;
  /** Return true to make the whole call answer "no route". */
  noRouteIf?: (points: LatLng[]) => boolean;
  /** Throw this instead of answering. */
  failWith?: Error;
}

/** Fixed-table stand-in for the Routes API. Records every call. */
export class FakeRoutesClient implements RoutesClient {
  calls: LatLng[][] = [];
  #opts: FakeRoutesOptions;
  constructor(opts: FakeRoutesOptions = {}) {
    this.#opts = opts;
  }
  async computeRoute(points: LatLng[]): Promise<RouteOutcome> {
    this.calls.push(points.map((p) => ({ lat: p.lat, lng: p.lng })));
    if (this.#opts.failWith) throw this.#opts.failWith;
    if (this.#opts.noRouteIf?.(points)) return { ok: false };
    const leg = this.#opts.leg ?? straightLineLeg;
    const legs: RouteLeg[] = [];
    for (let i = 1; i < points.length; i++) legs.push(leg(points[i - 1] as LatLng, points[i] as LatLng));
    return { ok: true, legs };
  }
}
