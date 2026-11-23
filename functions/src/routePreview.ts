import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as functionsLogger from "firebase-functions/logger";
import { FieldValue, getFirestore, type Firestore } from "firebase-admin/firestore";
import { adminApp } from "./admin.ts";
import { requireStudent } from "./shared/auth/requireStudent.ts";
import { COLLECTIONS } from "./shared/schema/collections.ts";
import { hashSeriesInput } from "./shared/schema/seriesHash.ts";
import type { PlaceAddress, RouteResult, SeriesDraftDoc, SeriesInput } from "./shared/schema/series.ts";
import { type Weekday } from "./shared/schema/weekday.ts";
import {
  seriesInvalidError,
  validateCampusTimes,
  validateSeriesInput,
  type SeriesValidationError,
} from "./shared/validation/index.ts";
import { loadValidationContext } from "./shared/validation/context.ts";
import {
  FakeRoutesClient,
  RoutesRejectedError,
  RoutesUnavailableError,
  createHttpRoutesClient,
  dedupeRoutesClient,
  type LatLng,
  type RoutesClient,
} from "./routePreview/routesClient.ts";
import { buildRouteResult } from "./routePreview/times.ts";

// Callable `routePreview` (#28, TMP-16, ADR 0008): validates a series, calls the Routes API once per
// weekday and direction, computes expected times, stores a "reviewed" server-side draft.

export const ROUTES_API_KEY = defineSecret("ROUTES_API_KEY");

/** Error code added by this module. Not part of #23's SERIES_ERROR_CODES. */
export const ROUTE_NOT_FOUND = "routeNotFound";
export const ROUTE_FAILED_MESSAGE = "route-failed";
export const DRAFT_ID_PATTERN = /^[A-Za-z0-9]{20}$/;

export interface RoutePreviewResult {
  draftId: string;
  inputHash: string;
  route: SeriesDraftDoc["route"];
}

export interface RoutePreviewLogger {
  info(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}

export interface RoutePreviewDeps {
  db?: Firestore;
  /** Tests inject a fake. Default: HTTP client with the secret key (emulator + demo project: straight-line fake). */
  routesClient?: RoutesClient;
  now?: () => Date;
  logger?: RoutePreviewLogger;
}

type Direction = "toCampus" | "fromCampus";

interface Point {
  ll: LatLng;
  /** Path of the host-entered address (#23 grammar). Absent for campus points. */
  path?: string;
}

function ll(a: { lat: number; lng: number }): LatLng {
  return { lat: a.lat, lng: a.lng };
}

function addrPoint(a: PlaceAddress, path: string): Point {
  return { ll: ll(a), path };
}

function useEmulatorFake(): boolean {
  const project = process.env["GCLOUD_PROJECT"] ?? process.env["GOOGLE_CLOUD_PROJECT"] ?? "";
  return process.env["FUNCTIONS_EMULATOR"] === "true" && project.startsWith("demo-");
}

function routeFailed(paths: readonly string[]): HttpsError {
  return new HttpsError("invalid-argument", ROUTE_FAILED_MESSAGE, {
    errors: paths.map((path) => ({ code: ROUTE_NOT_FOUND, path })),
  });
}

function notFoundDraft(): HttpsError {
  return new HttpsError("not-found", "Draft not found.");
}

function draftPosted(): HttpsError {
  return new HttpsError("failed-precondition", "This draft is already posted.", { reason: "draft-posted" });
}

/** Missing and not-yours give the same answer. */
function checkDraft(snap: { exists: boolean; data(): unknown }, uid: string): void {
  if (!snap.exists) throw notFoundDraft();
  const d = snap.data() as Partial<SeriesDraftDoc>;
  if (d.hostId !== uid) throw notFoundDraft();
  if (d.status !== "reviewed") throw draftPosted();
}

export function createRoutePreviewHandler(deps: RoutePreviewDeps = {}) {
  const logger: RoutePreviewLogger = deps.logger ?? functionsLogger;
  const nowFn = deps.now ?? (() => new Date());

  return async (request: CallableRequest<unknown>): Promise<RoutePreviewResult> => {
    const db = deps.db ?? getFirestore(adminApp());
    const uid = await requireStudent(request, db);
    const now = nowFn();

    const data = (typeof request.data === "object" && request.data !== null ? request.data : {}) as Record<string, unknown>;
    const draftIdRaw = data["draftId"];
    if (draftIdRaw !== undefined && (typeof draftIdRaw !== "string" || !DRAFT_ID_PATTERN.test(draftIdRaw))) {
      throw new HttpsError("invalid-argument", "draft-id-invalid");
    }
    const draftId = draftIdRaw as string | undefined;

    const ctx = await loadValidationContext(db, uid, now);
    const parsed = validateSeriesInput(data["series"], ctx);
    if (!parsed.ok) throw seriesInvalidError(parsed.errors);
    const input: SeriesInput = parsed.input;

    // Check the draft before any (paid) Routes call.
    if (draftId !== undefined) checkDraft(await db.collection(COLLECTIONS.seriesDrafts).doc(draftId).get(), uid);

    // Campus point coordinates (validation only returns ids).
    const campusIds = [...new Set(input.weekdays.flatMap((wd) => [input.days[wd]!.toCampus.campusPointId, input.days[wd]!.fromCampus.campusPointId]))];
    const campusSnaps = await db.getAll(...campusIds.map((id) => db.collection(COLLECTIONS.campusPoints).doc(id)));
    const campus = new Map<string, LatLng>();
    for (const s of campusSnaps) {
      const c = s.data() as { lat?: number; lng?: number } | undefined;
      if (!s.exists || typeof c?.lat !== "number" || typeof c.lng !== "number") {
        logger.error("route-preview: campus point unreadable", { uid });
        throw new HttpsError("internal", "Something went wrong. Try again.");
      }
      campus.set(s.id, { lat: c.lat, lng: c.lng });
    }

    const client = dedupeRoutesClient(
      deps.routesClient ?? (useEmulatorFake() ? new FakeRoutesClient() : createHttpRoutesClient(ROUTES_API_KEY.value())),
    );

    const route: SeriesDraftDoc["route"] = {};
    const failedPaths: string[] = [];
    // RC-28: attribution calls happen only on failure, and at most MAX_ATTRIBUTION_CALLS per review.
    const attributionBudget = { left: MAX_ATTRIBUTION_CALLS };

    for (const wd of input.weekdays) {
      const day = input.days[wd]!;
      const results: Partial<Record<Direction, RouteResult>> = {};
      for (const dir of ["toCampus", "fromCampus"] as const) {
        const legInput = day[dir];
        const legPath = `days.${wd}.${dir}`;
        const cp: Point = { ll: campus.get(legInput.campusPointId) as LatLng };
        const stopPoints = legInput.stops.map((s, i) => addrPoint(s.address, `${legPath}.stops.${i}.address`));
        const pickup = addrPoint(input.pickupSpot, "pickupSpot");
        // To campus: pickup, stops, campus point. Return: campus point, stops, pickup (own stop list, not reversed).
        const points: Point[] = dir === "toCampus" ? [pickup, ...stopPoints, cp] : [cp, ...stopPoints, pickup];

        let outcome;
        try {
          outcome = await client.computeRoute(points.map((p) => p.ll));
        } catch (e) {
          const status = (e as { status?: number }).status;
          logger.error("route-preview: Routes call failed", { uid, weekday: wd, direction: dir, status });
          if (e instanceof RoutesUnavailableError) {
            throw new HttpsError("unavailable", "Route calculation is busy. Try again.", { reason: "routes-unavailable" });
          }
          throw new HttpsError("internal", "Something went wrong. Try again.");
        }

        if (!outcome.ok) {
          failedPaths.push(await attribute(client, points, dir, legPath, uid, wd, logger, attributionBudget));
          continue;
        }
        const departMin = toMinutes(legInput.departTime);
        results[dir] = buildRouteResult(
          departMin,
          outcome.legs,
          legInput.stops.map((s) => s.minutes),
        );
      }
      if (results.toCampus && results.fromCampus) route[wd] = { toCampus: results.toCampus, fromCampus: results.fromCampus };
    }

    if (failedPaths.length > 0) throw routeFailed([...new Set(failedPaths)]);

    const timeErrors: SeriesValidationError[] = validateCampusTimes(input, route);
    if (timeErrors.length > 0) throw seriesInvalidError(timeErrors);

    const inputHash = hashSeriesInput(input);
    const draftsRef = db.collection(COLLECTIONS.seriesDrafts);
    const ref = draftId === undefined ? draftsRef.doc() : draftsRef.doc(draftId);
    await db.runTransaction(async (tx) => {
      if (draftId === undefined) {
        tx.create(ref, {
          hostId: uid,
          input,
          inputHash,
          route,
          status: "reviewed",
          reviewedAt: FieldValue.serverTimestamp(),
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      } else {
        checkDraft(await tx.get(ref), uid);
        tx.update(ref, {
          input,
          inputHash,
          route,
          reviewedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    });

    logger.info("route-preview complete", { uid, weekdays: input.weekdays.length });
    return { draftId: ref.id, inputHash, route };
  };
}

function toMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number) as [number, number];
  return h * 60 + m;
}

/**
 * Only on failure: test each host-entered address on the failed leg as a single hop to or from the
 * leg's campus point (the client dedupes, so results are shared across weekdays). Returns the path of
 * the first address with no route, or the leg path when none is at fault alone or the review's
 * attribution budget is spent (a hostile input can't multiply Routes calls; RC-28).
 */
export const MAX_ATTRIBUTION_CALLS = 10;

async function attribute(
  client: RoutesClient,
  points: Point[],
  dir: Direction,
  legPath: string,
  uid: string,
  wd: Weekday,
  logger: RoutePreviewLogger,
  budget: { left: number },
): Promise<string> {
  const cp = (dir === "toCampus" ? points[points.length - 1] : points[0]) as Point;
  for (const p of points) {
    if (p.path === undefined) continue;
    if (budget.left <= 0) return legPath;
    budget.left--;
    try {
      const hop = await client.computeRoute(dir === "toCampus" ? [p.ll, cp.ll] : [cp.ll, p.ll]);
      if (!hop.ok) return p.path;
    } catch (e) {
      logger.error("route-preview: attribution call failed", { uid, weekday: wd, direction: dir, status: (e as { status?: number }).status });
      if (e instanceof RoutesUnavailableError) {
        throw new HttpsError("unavailable", "Route calculation is busy. Try again.", { reason: "routes-unavailable" });
      }
      throw new HttpsError("internal", "Something went wrong. Try again.");
    }
  }
  return legPath;
}

export const routePreview = onCall({ region: "us-west2", secrets: [ROUTES_API_KEY] }, createRoutePreviewHandler());

