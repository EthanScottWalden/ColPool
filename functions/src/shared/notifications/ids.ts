// Deterministic notification ids. A repeated event produces the same id, so `create()` fails
// with ALREADY_EXISTS instead of writing a duplicate (ADR 0005).

import type { NotificationEvent } from "./types.ts";
import { assertId, assertWeekday } from "./validate.ts";

export function notificationId(event: NotificationEvent): string {
  const series = assertId("series id", event.seriesId);
  switch (event.type) {
    case "request_new":
      return `request_new_${series}_${assertId("rider uid", event.riderUid)}`;
    case "day_accepted":
    case "day_declined":
    case "day_auto_declined_full":
    case "day_auto_declined_conflict":
    case "rider_removed":
      return `${event.type}_${series}_${assertWeekday(event.weekday)}`;
    case "rider_left":
    case "request_withdrawn":
      return `${event.type}_${series}_${assertId("rider uid", event.riderUid)}_${assertWeekday(event.weekday)}`;
    case "series_cancelled":
      return `series_cancelled_${series}`;
    case "series_ended":
      // One doc per series and recipient: each recipient has their own subcollection.
      return `series_ended_${series}`;
    default: {
      const unknown: never = event;
      throw new Error(`Unknown notification type: ${String((unknown as { type?: unknown }).type)}`);
    }
  }
}
