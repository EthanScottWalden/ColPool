// Pacific-time helpers for series dates and departures (RC-12 and trip counting).
// Intl only, no dependency. All "dates" are local America/Los_Angeles `YYYY-MM-DD`
// strings (PacificDate); all "times" are local `HH:mm`.
//
// DST rules (PROVISIONAL, ADR 0006; they match java.time's ZonedDateTime.of):
// - A local time that does not exist (spring forward gap) shifts forward by the gap.
//   02:30 on 2026-03-08 is 03:30 PDT = 10:30Z.
// - A local time that happens twice (fall back) uses the earlier instant, the PDT one.
//   01:30 on 2026-11-01 is 01:30 PDT = 08:30Z.

import { WEEKDAYS, type Weekday } from "../schema/weekday.ts";
import type { PacificDate } from "../schema/types.ts";

export const PACIFIC_TZ = "America/Los_Angeles";

const DATE_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)$/;
const DAY_MS = 86_400_000;
const HOUR_MS = 3_600_000;

const partsFormat = new Intl.DateTimeFormat("en-US", {
  timeZone: PACIFIC_TZ,
  hourCycle: "h23",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

interface LocalParts {
  y: number;
  m: number;
  d: number;
  hh: number;
  mm: number;
}

function localParts(ms: number): LocalParts {
  const p: Record<string, number> = {};
  for (const part of partsFormat.formatToParts(new Date(ms))) {
    if (part.type !== "literal") p[part.type] = Number(part.value);
  }
  return { y: p["year"] as number, m: p["month"] as number, d: p["day"] as number, hh: p["hour"] as number, mm: p["minute"] as number };
}

function pad(n: number, w = 2): string {
  return String(n).padStart(w, "0");
}

function parseDate(date: string): { y: number; m: number; d: number } {
  const m = DATE_RE.exec(date);
  if (!m) throw new Error(`not a YYYY-MM-DD date: "${date}"`);
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  const check = new Date(Date.UTC(y, mo - 1, d));
  if (check.getUTCFullYear() !== y || check.getUTCMonth() !== mo - 1 || check.getUTCDate() !== d) {
    throw new Error(`not a real calendar date: "${date}"`);
  }
  return { y, m: mo, d };
}

/** True for a valid local 24-hour `HH:mm` (00:00 to 23:59). */
export function isLocalTime(value: unknown): value is string {
  return typeof value === "string" && TIME_RE.test(value);
}

/** The Pacific calendar date of an instant. */
export function pacificDateOf(instant: Date | number): PacificDate {
  const ms = typeof instant === "number" ? instant : instant.getTime();
  const p = localParts(ms);
  return `${pad(p.y, 4)}-${pad(p.m)}-${pad(p.d)}`;
}

/** The instant of a Pacific local date and `HH:mm` time (DST rules in the file header). */
export function pacificInstant(date: PacificDate, time: string): Date {
  const { y, m, d } = parseDate(date);
  const t = TIME_RE.exec(time);
  if (!t) throw new Error(`not an HH:mm time: "${time}"`);
  const hh = Number(t[1]);
  const mm = Number(t[2]);
  const asUtc = Date.UTC(y, m - 1, d, hh, mm);
  const pdt = asUtc + 7 * HOUR_MS; // the earlier of the two candidate instants
  const pst = asUtc + 8 * HOUR_MS;
  const matches = (ms: number): boolean => {
    const p = localParts(ms);
    return p.y === y && p.m === m && p.d === d && p.hh === hh && p.mm === mm;
  };
  if (matches(pdt)) return new Date(pdt); // summer time, or the repeated hour (earlier one)
  if (matches(pst)) return new Date(pst); // winter time
  return new Date(pst); // gap: the PST reading lands after the gap, so it shifts forward
}

/** Weekday of a Pacific calendar date. */
export function weekdayOf(date: PacificDate): Weekday {
  const { y, m, d } = parseDate(date);
  const dow = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 = Sunday
  return WEEKDAYS[(dow + 6) % 7] as Weekday;
}

/** Calendar-date arithmetic (not instants), so DST never matters. */
export function addDays(date: PacificDate, n: number): PacificDate {
  const { y, m, d } = parseDate(date);
  const r = new Date(Date.UTC(y, m - 1, d) + n * DAY_MS);
  return `${pad(r.getUTCFullYear(), 4)}-${pad(r.getUTCMonth() + 1)}-${pad(r.getUTCDate())}`;
}

/** Inclusive overlap of two date ranges (RC-32: sharing one day overlaps). */
export function rangesOverlap(
  a: { startDate: PacificDate; endDate: PacificDate },
  b: { startDate: PacificDate; endDate: PacificDate },
): boolean {
  return a.startDate <= b.endDate && b.startDate <= a.endDate;
}

/** The latest date in [startDate, endDate] on `weekday`, or null when there is none. */
export function lastDateOnWeekday(startDate: PacificDate, endDate: PacificDate, weekday: Weekday): PacificDate | null {
  parseDate(startDate);
  parseDate(endDate);
  let d = endDate;
  for (let i = 0; i < 7 && d >= startDate; i++, d = addDays(d, -1)) {
    if (weekdayOf(d) === weekday) return d;
  }
  return null;
}

/**
 * Last departure instant per selected weekday: the last matching date in range at that
 * weekday's `toCampus` departure time. A weekday with no date in range has no key.
 */
export function computeLastDepartureAt(input: {
  startDate: PacificDate;
  endDate: PacificDate;
  weekdays: readonly Weekday[];
  departTimes: Partial<Record<Weekday, string>>;
}): Partial<Record<Weekday, Date>> {
  const out: Partial<Record<Weekday, Date>> = {};
  for (const wd of input.weekdays) {
    const time = input.departTimes[wd];
    if (time === undefined) throw new Error(`no departure time for ${wd}`);
    const date = lastDateOnWeekday(input.startDate, input.endDate, wd);
    if (date !== null) out[wd] = pacificInstant(date, time);
  }
  return out;
}

/** True while `now` is strictly before the last departure. A missing last departure: false. */
export function hasTripsLeft(lastDepartureAt: Date | null | undefined, now: Date): boolean {
  return lastDepartureAt != null && now.getTime() < lastDepartureAt.getTime();
}
