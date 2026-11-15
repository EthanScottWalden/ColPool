import type { Weekday } from "./weekday.ts";

// Document shapes for the core schema (contract #13). Optional fields are
// absent when not provided, never null.

/** Structural timestamp: satisfied by the Admin SDK and web SDK Timestamp classes. */
export interface TimestampLike {
  readonly seconds: number;
  readonly nanoseconds: number;
  toDate(): Date;
}

/** Local America/Los_Angeles calendar date, `YYYY-MM-DD`. */
export type PacificDate = string;

/** `users/{uid}`. Backend-written; owner read only. */
export interface UserDoc {
  /** Lowercased, copied from Auth. */
  email: string;
  createdAt: TimestampLike;
}

/** The starting address, chosen from a Places suggestion (RC-13). Private (RC-05). */
export interface StartingAddress {
  text: string;
  placeId: string;
  lat: number;
  lng: number;
}

/** `privateDetails/{uid}`. Owner read and write, limited to these keys. */
export interface PrivateDetailsDoc {
  startingAddress: StartingAddress;
  updatedAt: TimestampLike;
}

/**
 * `profiles/{uid}`. Option fields hold stable ids from `options/profile`,
 * never labels. Optional fields are absent when not provided; a present
 * array is non-empty.
 */
export interface ProfileDoc {
  firstName: string;
  lastName: string;
  age?: number;
  /** Id from `genderIds`. "Prefer not to say" is an id, distinct from absent. */
  gender?: string;
  /** Id from `yearIds`. */
  year?: string;
  /** Id from `majorIds`. */
  major?: string;
  /** Ids from `interestIds`, no duplicates. */
  interests?: string[];
  /** Ids from `traitIds`, no duplicates. */
  traits?: string[];
  aboutMe?: string;
  updatedAt: TimestampLike;
}

/** `consents/{uid}`. Immutable; no client write. Existence is the consent check (RC-06). */
export interface ConsentDoc {
  agreedAt: TimestampLike;
}

/** Set of reason keys that currently grant one viewer access. */
export type ReasonSet = Record<string, true>;

/**
 * `profileAccess/{ownerId}`. Backend only. Reasons are sets, not counters.
 * An emptied map means no access.
 */
export interface ProfileAccessDoc {
  /** RC-01 case 1: keys are seriesIds of the owner's active hosted series. */
  activeHostedSeries?: Record<string, true>;
  /** Cases 2 to 4: viewerUid -> reason keys (see keys.ts). */
  viewers?: Record<string, ReasonSet>;
}

export type ScheduleRole = "host" | "rider";
export type ScheduleState = "hosting" | "accepted" | "pending";

/** One weekday of one series in a student's schedule (RC-32 lookup). */
export interface ScheduleEntry {
  seriesId: string;
  weekday: Weekday;
  role: ScheduleRole;
  state: ScheduleState;
  startDate: PacificDate;
  endDate: PacificDate;
}

/** `schedules/{uid}`. Backend only; owner read. Key: `scheduleEntryKey(seriesId, weekday)`. */
export interface ScheduleDoc {
  entries?: Record<string, ScheduleEntry>;
}

/** `campusPoints/{pointId}`. Seeded; never deleted, deactivated with `active`. */
export interface CampusPointDoc {
  name: string;
  lat: number;
  lng: number;
  placeId?: string;
  order: number;
  active: boolean;
}

export interface OptionEntry {
  id: string;
  label: string;
  order: number;
}

/**
 * `options/profile`. Each list has a parallel id array so Security Rules can
 * check membership with one `get()`.
 */
export interface ProfileOptionsDoc {
  genders: OptionEntry[];
  genderIds: string[];
  years: OptionEntry[];
  yearIds: string[];
  majors: OptionEntry[];
  majorIds: string[];
  interests: OptionEntry[];
  interestIds: string[];
  traits: OptionEntry[];
  traitIds: string[];
}
