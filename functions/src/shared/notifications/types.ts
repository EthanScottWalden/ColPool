// Notification records and events (contract #21, docs/data-model.md §2.9, ADR 0005).
// Events carry only what the text and the id need: names, weekday ids, display strings,
// an end date string and (where an id needs it) a rider uid. No addresses, no emails,
// no free text. Callers never build text; `buildNotificationText` does.

import type { Weekday } from "../schema/weekday.ts";

/** Subcollection under `users/{uid}`. Defined here, not in the #13 schema. */
export const NOTIFICATIONS_SUBCOLLECTION = "notifications";

export const NOTIFICATION_TYPES = [
  "request_new",
  "day_accepted",
  "day_declined",
  "day_auto_declined_full",
  "day_auto_declined_conflict",
  "rider_removed",
  "rider_left",
  "request_withdrawn",
  "series_cancelled",
  "series_ended",
] as const;

export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

export interface PersonName {
  firstName: string;
  lastName: string;
}

/** What the text needs to name the series (frozen at event time). */
export interface SeriesDisplay {
  hostName: PersonName;
  pickupSpotName: string;
}

interface Base {
  seriesId: string;
  series: SeriesDisplay;
}

/** Host is told a rider asked to join. One per rider x series. */
export interface RequestNewEvent extends Base {
  type: "request_new";
  riderUid: string;
  rider: PersonName;
  weekdays: Weekday[];
}

/** Rider is told about one weekday decision. One notification per weekday. */
export interface DayDecisionEvent extends Base {
  type: "day_accepted" | "day_declined" | "day_auto_declined_full" | "day_auto_declined_conflict";
  weekday: Weekday;
}

export interface RiderRemovedEvent extends Base {
  type: "rider_removed";
  weekday: Weekday;
}

/** Host is told a rider left an accepted weekday or withdrew a pending one. One per weekday. */
export interface RiderLeftEvent extends Base {
  type: "rider_left" | "request_withdrawn";
  riderUid: string;
  rider: PersonName;
  weekday: Weekday;
}

export interface SeriesCancelledEvent extends Base {
  type: "series_cancelled";
}

export interface SeriesEndedEvent extends Base {
  type: "series_ended";
  audience: "host" | "rider";
  /** `YYYY-MM-DD`, America/Los_Angeles calendar date. Formatted by splitting, no time zone math. */
  endDate: string;
}

export type NotificationEvent =
  | RequestNewEvent
  | DayDecisionEvent
  | RiderRemovedEvent
  | RiderLeftEvent
  | SeriesCancelledEvent
  | SeriesEndedEvent;

export interface NotificationText {
  title: string;
  body: string;
  seriesLine: string;
}

/** The stored document (client read shape). `createdAt` is a server timestamp. */
export interface NotificationDoc extends NotificationText {
  type: NotificationType;
  seriesId: string;
  weekdays?: Weekday[];
  read: boolean;
}
