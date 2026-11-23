import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import * as functionsLogger from "firebase-functions/logger";
import { FieldValue, Timestamp, getFirestore, type Firestore } from "firebase-admin/firestore";
import { adminApp } from "./admin.ts";
import { requireStudent } from "./shared/auth/requireStudent.ts";
import { COLLECTIONS } from "./shared/schema/collections.ts";
import { scheduleEntryKey } from "./shared/schema/keys.ts";
import { hashSeriesInput } from "./shared/schema/seriesHash.ts";
import type { DayLegs, SeriesDraftDoc, SeriesDoc } from "./shared/schema/series.ts";
import type { ScheduleEntry } from "./shared/schema/types.ts";
import type { Weekday } from "./shared/schema/weekday.ts";
import { computeLastDepartureAt } from "./shared/time/pacific.ts";
import { loadValidationContext } from "./shared/validation/context.ts";
import { seriesInvalidError, validateCampusTimes, validateSeriesInput } from "./shared/validation/index.ts";
import { DRAFT_ID_PATTERN } from "./routePreview.ts";

// Callable `postSeries` (#30, TMP-17, ADR 0009): re-runs every check against current data, then
// saves exactly the reviewed draft as a series in one transaction. Idempotent on the draft id.

export const DRAFT_CHANGED_REASON = "draft-changed";

export interface PostSeriesResult {
  seriesId: string;
  alreadyPosted: boolean;
}

export interface PostSeriesLogger {
  info(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}

export interface PostSeriesDeps {
  db?: Firestore;
  now?: () => Date;
  logger?: PostSeriesLogger;
}

function notFoundDraft(): HttpsError {
  return new HttpsError("not-found", "Draft not found.");
}

function internal(): HttpsError {
  return new HttpsError("internal", "Something went wrong. Try again.");
}

export function createPostSeriesHandler(deps: PostSeriesDeps = {}) {
  const logger: PostSeriesLogger = deps.logger ?? functionsLogger;
  const nowFn = deps.now ?? (() => new Date());

  return async (request: CallableRequest<unknown>): Promise<PostSeriesResult> => {
    const db = deps.db ?? getFirestore(adminApp());
    const uid = await requireStudent(request, db);
    const now = nowFn();

    const data = (typeof request.data === "object" && request.data !== null ? request.data : {}) as Record<string, unknown>;
    const draftId = data["draftId"];
    if (typeof draftId !== "string" || !DRAFT_ID_PATTERN.test(draftId)) {
      throw new HttpsError("invalid-argument", "draft-id-invalid");
    }

    const draftRef = db.collection(COLLECTIONS.seriesDrafts).doc(draftId);
    const seriesRef = db.collection(COLLECTIONS.series).doc(draftId);
    const scheduleRef = db.collection(COLLECTIONS.schedules).doc(uid);
    const accessRef = db.collection(COLLECTIONS.profileAccess).doc(uid);

    const result = await db.runTransaction(async (tx): Promise<PostSeriesResult> => {
      // All reads first.
      const [draftSnap, seriesSnap] = await Promise.all([tx.get(draftRef), tx.get(seriesRef)]);
      const ctx = await loadValidationContext(db, uid, now, tx);

      // Missing and not-yours give the same answer.
      if (!draftSnap.exists) throw notFoundDraft();
      const draft = draftSnap.data() as SeriesDraftDoc;
      if (draft.hostId !== uid) throw notFoundDraft();

      // Idempotency first: a repeat tap returns the existing series and writes nothing.
      if (seriesSnap.exists) {
        if ((seriesSnap.data() as SeriesDoc).hostId !== uid) throw internal();
        return { seriesId: draftId, alreadyPosted: true };
      }
      if (draft.status !== "reviewed") {
        logger.error("post-series: inconsistent draft state", { uid, status: draft.status });
        throw internal();
      }

      // Re-check everything against current data (start date, matching date, RC-31, RC-32, campus points).
      const parsed = validateSeriesInput(data["series"], ctx);
      if (!parsed.ok) throw seriesInvalidError(parsed.errors);

      // The draft must still be exactly what the host reviewed.
      if (hashSeriesInput(parsed.input) !== draft.inputHash) {
        throw new HttpsError("failed-precondition", "The series changed after review. Review it again.", {
          reason: DRAFT_CHANGED_REASON,
        });
      }

      // Saved values come from the draft only (RC-12): the app never supplies routes.
      const input = draft.input;
      const timeErrors = validateCampusTimes(input, draft.route);
      if (timeErrors.length > 0) throw seriesInvalidError(timeErrors);

      const days: Partial<Record<Weekday, DayLegs>> = {};
      const openSeats: Partial<Record<Weekday, number>> = {};
      const departTimes: Partial<Record<Weekday, string>> = {};
      for (const wd of input.weekdays) {
        const legs = input.days[wd];
        const route = draft.route[wd];
        if (!legs || !route?.toCampus || !route.fromCampus) {
          logger.error("post-series: draft route incomplete", { uid, weekday: wd });
          throw internal();
        }
        days[wd] = {
          toCampus: { ...legs.toCampus, route: route.toCampus },
          fromCampus: { ...legs.fromCampus, route: route.fromCampus },
        };
        openSeats[wd] = input.maxRiders;
        departTimes[wd] = legs.toCampus.departTime;
      }

      const lastDepartureAt: Record<string, Timestamp> = {};
      for (const [wd, date] of Object.entries(computeLastDepartureAt({ ...input, departTimes }))) {
        lastDepartureAt[wd] = Timestamp.fromDate(date);
      }

      tx.create(seriesRef, {
        hostId: uid,
        status: "active",
        pickupSpot: input.pickupSpot,
        startDate: input.startDate,
        endDate: input.endDate,
        priceDollars: input.priceDollars,
        vehicle: input.vehicle,
        maxRiders: input.maxRiders,
        weekdays: input.weekdays,
        days,
        openSeats,
        openWeekdays: [...input.weekdays],
        lastDepartureAt,
        inputHash: draft.inputHash,
        postedAt: FieldValue.serverTimestamp(),
      });
      tx.update(draftRef, { status: "posted", postedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });

      const entries: Record<string, ScheduleEntry> = {};
      for (const wd of input.weekdays) {
        entries[scheduleEntryKey(draftId, wd)] = {
          seriesId: draftId,
          weekday: wd,
          role: "host",
          state: "hosting",
          startDate: input.startDate,
          endDate: input.endDate,
        };
      }
      // Every post writes the host's schedule, so posts by one host serialize (RC-31, RC-32).
      tx.set(scheduleRef, { entries }, { merge: true });
      // RC-01 case 1: every student may view the host's profile while a series is active.
      tx.set(accessRef, { activeHostedSeries: { [draftId]: true } }, { merge: true });
      return { seriesId: draftId, alreadyPosted: false };
    });

    logger.info("post-series complete", { uid, alreadyPosted: result.alreadyPosted });
    return result;
  };
}

export const postSeries = onCall({ region: "us-west2" }, createPostSeriesHandler());
