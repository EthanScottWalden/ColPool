import { isWeekday, type Weekday } from "./weekday.ts";

// Key builders for profileAccess reasons and schedule entries.
// Deterministic and pure: the same inputs always give the same key, so
// repeated writes are idempotent.

const ID_PATTERN = /^[A-Za-z0-9_-]+$/;

function checkId(name: string, value: string): void {
  if (!ID_PATTERN.test(value)) {
    throw new Error(`${name} must be a non-empty id of letters, digits, "_" or "-"`);
  }
}

function checkWeekday(value: string): asserts value is Weekday {
  if (!isWeekday(value)) {
    throw new Error(`weekday must be one of mon..sun, got "${value}"`);
  }
}

/** Reason key for RC-01 cases 2 and 3 (host and requester, symmetric): `req:<seriesId>`. */
export function reqReason(seriesId: string): string {
  checkId("seriesId", seriesId);
  return `req:${seriesId}`;
}

/** Reason key for RC-01 case 4 (co-riders accepted on one weekday): `co:<seriesId>:<weekday>`. */
export function coReason(seriesId: string, weekday: Weekday): string {
  checkId("seriesId", seriesId);
  checkWeekday(weekday);
  return `co:${seriesId}:${weekday}`;
}

/** Key of one entry in `schedules/{uid}.entries`: `<seriesId>_<weekday>`. */
export function scheduleEntryKey(seriesId: string, weekday: Weekday): string {
  checkId("seriesId", seriesId);
  checkWeekday(weekday);
  return `${seriesId}_${weekday}`;
}
