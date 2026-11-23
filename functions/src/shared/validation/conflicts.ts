import { WEEKDAYS, type Weekday } from "../schema/weekday.ts";
import type { PacificDate } from "../schema/types.ts";
import { lastDateOnWeekday } from "../time/pacific.ts";

// The one weekday-conflict test shared by posting (RC-31, RC-32), request (#35) and
// accept (#40). OWNER DECISION 2026-11-23 (ADR 0007): two series conflict on weekday W
// only when they share an actual ride date: some date on weekday W, today or later
// (Pacific, server clock), inside BOTH date ranges. Ranges that overlap only on days
// neither series rides do not conflict, a weekday with no date in range never conflicts,
// and once all shared dates are past it stops blocking. Trips left (RC-33) is not used.

export interface DateSpan {
  startDate: PacificDate;
  endDate: PacificDate;
}

export interface WeekdaySpan extends DateSpan {
  weekdays: readonly Weekday[];
}

/** Weekdays (Monday-first) on which the two series share a ride date on or after `today`. */
export function conflictingWeekdays(a: WeekdaySpan, b: WeekdaySpan, today: PacificDate): Weekday[] {
  const start = [a.startDate, b.startDate, today].reduce((x, y) => (x > y ? x : y));
  const end = a.endDate < b.endDate ? a.endDate : b.endDate;
  if (start > end) return [];
  return WEEKDAYS.filter(
    (wd) => a.weekdays.includes(wd) && b.weekdays.includes(wd) && lastDateOnWeekday(start, end, wd) !== null,
  );
}
