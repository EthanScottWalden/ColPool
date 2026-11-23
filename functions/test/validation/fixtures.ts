import type { LegInput, SeriesInput } from "../../src/shared/schema/series.ts";
import type { ScheduleEntry } from "../../src/shared/schema/types.ts";
import type { Weekday } from "../../src/shared/schema/weekday.ts";
import type { HostedSeriesSummary, ValidationContext } from "../../src/shared/validation/context.ts";

// Fixed clock: Monday 2026-11-23 12:00 PST (20:00Z). Made-up data only.
export const NOW = new Date("2026-11-23T20:00:00Z");
export const TODAY = "2026-11-23";

export const ADDR = { text: "1 Test Way, Riverside, CA", placeId: "test-place-1", lat: 33.93, lng: -117.4 };

export function leg(overrides: Partial<LegInput> = {}): LegInput {
  return { departTime: "07:30", campusPointId: "cp1", stops: [], ...overrides };
}

/** A valid two-weekday (Mon, Wed) input, 2026-11-23 to 2026-12-18. */
export function validInput(overrides: Partial<SeriesInput> = {}): SeriesInput {
  return {
    pickupSpot: ADDR,
    startDate: "2026-11-23",
    endDate: "2026-12-18",
    priceDollars: 5,
    vehicle: "Test Car",
    maxRiders: 3,
    weekdays: ["mon", "wed"],
    days: {
      mon: { toCampus: leg(), fromCampus: leg({ departTime: "15:00" }) },
      wed: { toCampus: leg(), fromCampus: leg({ departTime: "15:00" }) },
    },
    ...overrides,
  };
}

export function ctx(overrides: Partial<ValidationContext> = {}): ValidationContext {
  return { now: NOW, activeCampusPointIds: ["cp1", "cp2"], hostedSeries: [], schedule: [], ...overrides };
}

export function hosted(id: string, startDate: string, endDate: string, weekdays: Weekday[]): HostedSeriesSummary {
  return { id, startDate, endDate, weekdays };
}

export function ride(seriesId: string, weekday: Weekday, startDate: string, endDate: string, state: ScheduleEntry["state"] = "accepted"): ScheduleEntry {
  return { seriesId, weekday, role: "rider", state, startDate, endDate };
}
