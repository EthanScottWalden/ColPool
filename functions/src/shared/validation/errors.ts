import { HttpsError } from "firebase-functions/v2/https";

// Field-level validation errors (contract #23, ADR 0007). The backend sends codes only,
// never display text (PROVISIONAL: wording lives in the Android Issue).

export const SERIES_ERROR_CODES = [
  "required",
  "notInteger",
  "outOfRange",
  "tooLong",
  "invalidAddress",
  "invalidDate",
  "invalidTime",
  "unknownCampusPoint",
  "tooManyStops",
  "duplicateWeekday",
  "startInPast",
  "endBeforeStart",
  "noDateOnWeekdays",
  "campusDepartureBeforeArrival",
  "hostingLimitReached",
  "hostingWeekdayOverlap",
  "riderScheduleConflict",
] as const;

export type SeriesErrorCode = (typeof SERIES_ERROR_CODES)[number];

/**
 * `path` is a dotted path into SeriesInput (`days.tue.toCampus.stops.2.minutes`,
 * `startDate`); `""` means the form as a whole. `params` holds only limits and weekday
 * ids, never other users' data or series details.
 */
export interface SeriesValidationError {
  code: SeriesErrorCode;
  path: string;
  params?: Record<string, string | number>;
}

export const SERIES_INVALID_MESSAGE = "series-invalid";

export function seriesInvalidError(errors: readonly SeriesValidationError[]): HttpsError {
  return new HttpsError("invalid-argument", SERIES_INVALID_MESSAGE, { errors: [...errors] });
}
