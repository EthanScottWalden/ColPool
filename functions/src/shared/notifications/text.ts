// Server-rendered notification text. Pure: no clock, no Firestore, no reads.
// Wording follows docs/backlog-proposal.md Appendix B and POLICY §11 (ADR 0005).

import type { Weekday } from "../schema/weekday.ts";
import type { NotificationEvent, NotificationText } from "./types.ts";
import { assertWeekday, checkedSeries, normalizeWeekdays, personName } from "./validate.ts";

const DAY_NAMES: Record<Weekday, string> = {
  mon: "Monday",
  tue: "Tuesday",
  wed: "Wednesday",
  thu: "Thursday",
  fri: "Friday",
  sat: "Saturday",
  sun: "Sunday",
};

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function dayName(day: Weekday): string {
  return DAY_NAMES[assertWeekday(day)];
}

/** "Tuesday", "Tuesday and Thursday", "Monday, Tuesday and Thursday". Input is made Monday-first. */
export function formatDayList(days: Weekday[]): string {
  const names = normalizeWeekdays(days).map((d) => DAY_NAMES[d]);
  if (names.length === 1) return names[0] as string;
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1] as string}`;
}

/** `2026-10-09` -> "Oct 9". Splits the string; no Date, so no time zone or DST effect. */
export function formatEndDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(typeof iso === "string" ? iso : "");
  if (m === null) throw new Error("Invalid end date for notification");
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
  const lengths = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (month < 1 || month > 12 || day < 1 || day > (lengths[month - 1] as number)) {
    throw new Error("Invalid end date for notification");
  }
  return `${MONTHS[month - 1] as string} ${day}`;
}

export function buildNotificationText(event: NotificationEvent): NotificationText {
  const s = checkedSeries(event.series);
  const seriesLine = `${s.hostFirst}'s series from ${s.pickupSpot}`;
  const make = (title: string, body: string): NotificationText => ({ title, body, seriesLine });

  switch (event.type) {
    case "request_new":
      return make(
        "New ride request",
        `${personName(event.rider)} wants to join your series on ${formatDayList(event.weekdays)}.`,
      );
    case "day_accepted":
      return make("Day accepted", `${s.hostName} accepted your request for ${dayName(event.weekday)}.`);
    case "day_declined":
      return make("Day declined", `${s.hostName} declined your request for ${dayName(event.weekday)}.`);
    case "day_auto_declined_full":
      return make(
        "Day declined",
        `${dayName(event.weekday)} in ${s.hostName}'s series filled up, so that day of your request was declined automatically.`,
      );
    case "day_auto_declined_conflict":
      return make(
        "Day declined",
        `${dayName(event.weekday)} in ${s.hostName}'s series was declined automatically because you were accepted on ${dayName(event.weekday)} in another series.`,
      );
    case "rider_removed":
      return make(
        `Removed from ${dayName(event.weekday)}`,
        `${s.hostName} removed you from their series on ${dayName(event.weekday)}.`,
      );
    case "rider_left":
      return make(
        "Rider left",
        `${personName(event.rider)} left your series on ${dayName(event.weekday)}. A seat opened up.`,
      );
    case "request_withdrawn":
      return make(
        "Request withdrawn",
        `${personName(event.rider)} withdrew their request for ${dayName(event.weekday)}.`,
      );
    case "series_cancelled":
      return make("Series cancelled", `${s.hostName} cancelled a series you requested to join.`);
    case "series_ended": {
      const date = formatEndDate(event.endDate);
      if (event.audience === "host") return make("Series ended", `Your series reached its end date on ${date}.`);
      if (event.audience === "rider") {
        return make("Series ended", `${s.hostName}'s series reached its end date on ${date}.`);
      }
      throw new Error("Invalid audience for notification");
    }
    default: {
      const unknown: never = event;
      throw new Error(`Unknown notification type: ${String((unknown as { type?: unknown }).type)}`);
    }
  }
}
