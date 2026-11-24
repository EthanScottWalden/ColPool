import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import * as functionsLogger from "firebase-functions/logger";
import { FieldValue, getFirestore, type Firestore } from "firebase-admin/firestore";
import { adminApp } from "./admin.ts";
import { requireStudent } from "./shared/auth/requireStudent.ts";
import { writeNotification } from "./shared/notifications/index.ts";
import { COLLECTIONS } from "./shared/schema/collections.ts";
import { reqReason, scheduleEntryKey } from "./shared/schema/keys.ts";
import { requestId, type RequestDayState } from "./shared/schema/request.ts";
import type { SeriesDoc } from "./shared/schema/series.ts";
import type { ProfileDoc, ScheduleDoc, ScheduleEntry } from "./shared/schema/types.ts";
import { WEEKDAYS, isWeekday, type Weekday } from "./shared/schema/weekday.ts";
import { hasTripsLeft, pacificDateOf } from "./shared/time/pacific.ts";
import { conflictingWeekdays } from "./shared/validation/conflicts.ts";
import { DRAFT_ID_PATTERN } from "./routePreview.ts";

// Callable `sendRequest` (#35, TMP-26, ADR 0010): one pending request per rider and series,
// created with its schedule entries, profile access and host notification in one transaction.
// Seats are not touched here: the host's accept (#40) takes them.

export interface SendRequestResult {
  requestId: string;
}

export type WeekdayFailure = "full" | "no-trips-left" | "conflict";

export interface SendRequestLogger {
  info(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}

export interface SendRequestDeps {
  db?: Firestore;
  now?: () => Date;
  logger?: SendRequestLogger;
}

function internal(): HttpsError {
  return new HttpsError("internal", "Something went wrong. Try again.");
}

export function createSendRequestHandler(deps: SendRequestDeps = {}) {
  const logger: SendRequestLogger = deps.logger ?? functionsLogger;
  const nowFn = deps.now ?? (() => new Date());

  return async (request: CallableRequest<unknown>): Promise<SendRequestResult> => {
    const db = deps.db ?? getFirestore(adminApp());
    const uid = await requireStudent(request, db);
    const now = nowFn();

    const data = (typeof request.data === "object" && request.data !== null ? request.data : {}) as Record<string, unknown>;
    const seriesId = data["seriesId"];
    if (typeof seriesId !== "string" || !DRAFT_ID_PATTERN.test(seriesId)) {
      throw new HttpsError("invalid-argument", "series-id-invalid", { reason: "series-id-invalid" });
    }
    const rawWeekdays = data["weekdays"];
    if (
      !Array.isArray(rawWeekdays) ||
      rawWeekdays.length === 0 ||
      !rawWeekdays.every(isWeekday) ||
      new Set(rawWeekdays).size !== rawWeekdays.length
    ) {
      throw new HttpsError("invalid-argument", "weekdays-invalid", { reason: "weekdays-invalid" });
    }
    const chosen = WEEKDAYS.filter((wd) => (rawWeekdays as Weekday[]).includes(wd));

    const seriesRef = db.collection(COLLECTIONS.series).doc(seriesId);
    const requestRef = db.collection(COLLECTIONS.requests).doc(requestId(seriesId, uid));
    const scheduleRef = db.collection(COLLECTIONS.schedules).doc(uid);

    const id = await db.runTransaction(async (tx): Promise<string> => {
      // All reads first.
      const seriesSnap = await tx.get(seriesRef);
      if (!seriesSnap.exists) throw new HttpsError("not-found", "Series not found.");
      const series = seriesSnap.data() as SeriesDoc;
      const hostId = series.hostId;
      const [requestSnap, scheduleSnap, riderSnap, hostSnap] = await Promise.all([
        tx.get(requestRef),
        tx.get(scheduleRef),
        tx.get(db.collection(COLLECTIONS.profiles).doc(uid)),
        tx.get(db.collection(COLLECTIONS.profiles).doc(hostId)),
      ]);

      if (hostId === uid) throw new HttpsError("failed-precondition", "You can't request your own series.", { reason: "own-series" });
      if (series.status !== "active") throw new HttpsError("failed-precondition", "This series isn't active.", { reason: "series-not-active" });
      if (!chosen.every((wd) => series.weekdays.includes(wd))) {
        throw new HttpsError("invalid-argument", "weekdays-invalid", { reason: "weekdays-invalid" });
      }
      if (requestSnap.exists) throw new HttpsError("already-exists", "You already requested this series.", { reason: "already-requested" });

      // Per weekday: seat, trips left, conflict with an accepted or hosted weekday (pending is ignored).
      const today = pacificDateOf(now);
      const entries = Object.values((scheduleSnap.data() as ScheduleDoc | undefined)?.entries ?? {}).filter(
        (e: ScheduleEntry) => e.state === "accepted" || e.state === "hosting",
      );
      const conflicts = new Set<Weekday>();
      for (const e of entries) {
        for (const wd of conflictingWeekdays(
          { weekdays: chosen, startDate: series.startDate, endDate: series.endDate },
          { weekdays: [e.weekday], startDate: e.startDate, endDate: e.endDate },
          today,
        )) conflicts.add(wd);
      }
      const failures: { weekday: Weekday; reason: WeekdayFailure }[] = [];
      for (const wd of chosen) {
        const last = series.lastDepartureAt?.[wd];
        const lastDate = last && "toDate" in last && typeof last.toDate === "function" ? (last.toDate() as Date) : null;
        if (!((series.openSeats?.[wd] ?? 0) > 0)) failures.push({ weekday: wd, reason: "full" });
        else if (!hasTripsLeft(lastDate, now)) failures.push({ weekday: wd, reason: "no-trips-left" });
        else if (conflicts.has(wd)) failures.push({ weekday: wd, reason: "conflict" });
      }
      if (failures.length > 0) {
        throw new HttpsError("failed-precondition", "Some days aren't available.", { reason: "weekdays-unavailable", weekdays: failures });
      }

      const riderProfile = riderSnap.data() as ProfileDoc | undefined;
      const hostProfile = hostSnap.data() as ProfileDoc | undefined;
      if (!riderProfile || !hostProfile) {
        logger.error("send-request: profile missing", { uid, seriesId });
        throw internal();
      }

      const days: Partial<Record<Weekday, RequestDayState>> = {};
      const newEntries: Record<string, ScheduleEntry> = {};
      for (const wd of chosen) {
        days[wd] = { status: "pending" };
        newEntries[scheduleEntryKey(seriesId, wd)] = {
          seriesId, weekday: wd, role: "rider", state: "pending", startDate: series.startDate, endDate: series.endDate,
        };
      }
      const rid = requestRef.id;
      tx.create(requestRef, { seriesId, riderId: uid, hostId, weekdays: chosen, days, createdAt: FieldValue.serverTimestamp() });
      tx.set(scheduleRef, { entries: newEntries }, { merge: true });
      // RC-01 cases 2 and 3 (symmetric): host sees the rider, rider sees the host.
      tx.set(db.collection(COLLECTIONS.profileAccess).doc(uid), { viewers: { [hostId]: { [reqReason(seriesId)]: true } } }, { merge: true });
      tx.set(db.collection(COLLECTIONS.profileAccess).doc(hostId), { viewers: { [uid]: { [reqReason(seriesId)]: true } } }, { merge: true });
      writeNotification(tx, db, hostId, {
        type: "request_new",
        seriesId,
        series: {
          hostName: { firstName: hostProfile.firstName, lastName: hostProfile.lastName },
          pickupSpotName: series.pickupSpot.text,
        },
        riderUid: uid,
        rider: { firstName: riderProfile.firstName, lastName: riderProfile.lastName },
        weekdays: chosen,
      });
      return rid;
    });

    logger.info("send-request complete", { uid, seriesId });
    return { requestId: id };
  };
}

export const sendRequest = onCall({ region: "us-west2" }, createSendRequestHandler());
