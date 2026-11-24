import type { TimestampLike } from "./types.ts";
import type { Weekday } from "./weekday.ts";

// Request records (#35, docs/data-model.md §2.12, ADR 0010). PROVISIONAL: the per-weekday
// status values and the timestamp fields are settled by #26 (request lifecycle); #35 only
// ever writes `pending`. No names or other personal data are stored here.

export type RequestDayStatus = "pending";

export interface RequestDayState {
  status: RequestDayStatus;
}

/** `requests/{seriesId}_{riderUid}`. Backend written; its rider and the series' host may read. */
export interface RequestDoc {
  seriesId: string;
  riderId: string;
  /** Copied from the series so rules can check the host without a second read. */
  hostId: string;
  /** The weekdays asked for, Monday-first. */
  weekdays: Weekday[];
  days: Partial<Record<Weekday, RequestDayState>>;
  createdAt: TimestampLike;
}

/** Deterministic id: one request per rider and series is enforced by this id (RC-15). */
export function requestId(seriesId: string, riderUid: string): string {
  if (!/^[A-Za-z0-9_-]+$/.test(seriesId)) throw new Error("seriesId must be a non-empty id");
  if (!/^[A-Za-z0-9_-]+$/.test(riderUid)) throw new Error("riderUid must be a non-empty id");
  return `${seriesId}_${riderUid}`;
}
