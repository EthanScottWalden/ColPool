import type { PacificDate, TimestampLike } from "./types.ts";
import type { Weekday } from "./weekday.ts";

// Series, draft and seat documents (contract #20, docs/data-model.md §2.10 and §2.11,
// ADR 0006). Backend-written; students read series, hosts read their own drafts.
// Optional fields are absent when not provided, never null.

/** Local 24-hour `HH:mm`. */
export type LocalTime = string;

/** A chosen Places suggestion (RC-13): place id and coordinates are stored with the text. */
export interface PlaceAddress {
  text: string;
  placeId: string;
  lat: number;
  lng: number;
}

/** A stop on a leg: an address and the wait there, in whole minutes (RC-30: 0 to 120). */
export interface Stop {
  address: PlaceAddress;
  minutes: number;
}

/** One computed route: per-hop distance and duration plus per-point times. */
export interface RouteResult {
  legs: { meters: number; durationSec: number }[];
  totalMeters: number;
  totalDurationSec: number;
  /**
   * One entry per route point, in order. Minutes after local midnight of the trip day;
   * may exceed 1439 when a trip crosses midnight. The first point has only `departMin`,
   * the last only `arriveMin`.
   */
  times: { arriveMin?: number; departMin?: number }[];
}

/** The input side of a leg: everything the host enters, no route values. */
export interface LegInput {
  departTime: LocalTime;
  campusPointId: string;
  stops: Stop[];
}

/** One direction on one weekday, with its stored route. */
export interface Leg extends LegInput {
  route: RouteResult;
}

export interface DayLegs {
  toCampus: Leg;
  fromCampus: Leg;
}

/** Everything the host enters for a series (hashed for `inputHash`). No route values. */
export interface SeriesInput {
  pickupSpot: PlaceAddress;
  startDate: PacificDate;
  endDate: PacificDate;
  priceDollars: number;
  vehicle: string;
  maxRiders: number;
  /** Monday-first. */
  weekdays: Weekday[];
  days: Partial<Record<Weekday, { toCampus: LegInput; fromCampus: LegInput }>>;
}

export type DraftStatus = "reviewed" | "posted";
export type SeriesStatus = "active" | "cancelled" | "ended";

/** `seriesDrafts/{draftId}`. Backend only; the host may get it. Never deleted. */
export interface SeriesDraftDoc {
  hostId: string;
  input: SeriesInput;
  /** SHA-256 of the canonical JSON of `input`: the "reviewed" marker. */
  inputHash: string;
  route: Partial<Record<Weekday, { toCampus: RouteResult; fromCampus: RouteResult }>>;
  reviewedAt: TimestampLike;
  createdAt: TimestampLike;
  updatedAt: TimestampLike;
  status: DraftStatus;
  postedAt?: TimestampLike;
}

/**
 * `series/{seriesId}`; `seriesId` equals the draft id (posting is idempotent).
 * Backend only; every student may read. `cancelledAt` / `endedAt` belong to #26.
 */
export interface SeriesDoc {
  hostId: string;
  status: SeriesStatus;
  pickupSpot: PlaceAddress;
  startDate: PacificDate;
  endDate: PacificDate;
  priceDollars: number;
  vehicle: string;
  maxRiders: number;
  /** Monday-first. */
  weekdays: Weekday[];
  days: Partial<Record<Weekday, DayLegs>>;
  /** Seat source of truth per weekday; starts at `maxRiders`. */
  openSeats: Partial<Record<Weekday, number>>;
  /** Weekdays with `openSeats > 0` (search field). Changes with `openSeats`. */
  openWeekdays: Weekday[];
  /** Computed at posting. A weekday with no date in range has no key. */
  lastDepartureAt: Partial<Record<Weekday, TimestampLike>>;
  inputHash: string;
  postedAt: TimestampLike;
}

/**
 * Fields a lifecycle function may change after posting. Everything else is immutable
 * (RC-12: routes never recalculate). #26 adds `cancelledAt` and `endedAt`.
 * Status moves only active -> cancelled and active -> ended; both are terminal.
 */
export const SERIES_MUTABLE_FIELDS = ["openSeats", "openWeekdays", "status"] as const;
