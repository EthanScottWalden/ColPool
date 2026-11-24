import { isWeekday, WEEKDAYS, type Weekday } from "../schema/weekday.ts";
import type { PersonName, SeriesDisplay } from "./types.ts";

const ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;

export function assertId(label: string, value: unknown): string {
  if (typeof value !== "string" || !ID_PATTERN.test(value)) {
    throw new Error(`Invalid ${label} for notification`);
  }
  return value;
}

export function assertWeekday(value: unknown): Weekday {
  if (!isWeekday(value)) throw new Error("Invalid weekday for notification");
  return value;
}

/** Non-empty, unique, valid weekdays, returned Monday-first. */
export function normalizeWeekdays(value: unknown): Weekday[] {
  if (!Array.isArray(value) || value.length === 0) throw new Error("Notification needs at least one weekday");
  const days = value.map(assertWeekday);
  if (new Set(days).size !== days.length) throw new Error("Duplicate weekday for notification");
  return WEEKDAYS.filter((d) => days.includes(d));
}

function assertText(label: string, value: unknown): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`Invalid ${label} for notification`);
  }
  return value.trim();
}

export function personName(p: PersonName): string {
  return `${assertText("first name", p?.firstName)} ${assertText("last name", p?.lastName)}`;
}

export function checkedSeries(s: SeriesDisplay): { hostName: string; hostFirst: string; pickupSpot: string } {
  return {
    hostName: personName(s?.hostName),
    hostFirst: assertText("first name", s.hostName.firstName),
    pickupSpot: assertText("pickup spot name", s.pickupSpotName),
  };
}
