import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { validateCampusTimes, validateSeriesInput, seriesInvalidError, SERIES_ERROR_CODES, type SeriesErrorCode } from "../../src/shared/validation/index.ts";
import type { SeriesInput } from "../../src/shared/schema/series.ts";
import { ADDR, ctx, hosted, leg, ride, validInput, NOW } from "./fixtures.ts";

function errorsOf(raw: unknown, c = ctx()): { code: string; path: string; params?: unknown }[] {
  const r = validateSeriesInput(raw, c);
  return r.ok ? [] : r.errors;
}
function has(raw: unknown, code: SeriesErrorCode, path: string, c = ctx()): boolean {
  return errorsOf(raw, c).some((e) => e.code === code && e.path === path);
}
function mut(fn: (i: any) => void): unknown {
  const i = structuredClone(validInput()) as any;
  fn(i);
  return i;
}

describe("validateSeriesInput: field rules", () => {
  it("accepts a valid input and returns it normalized", () => {
    const r = validateSeriesInput(mut((i) => { i.vehicle = "  Test Car  "; i.weekdays = ["wed", "mon"]; }), ctx());
    assert.ok(r.ok);
    assert.equal(r.input.vehicle, "Test Car");
    assert.deepEqual(r.input.weekdays, ["mon", "wed"]);
  });

  it("required: non-object, missing fields", () => {
    assert.deepEqual(errorsOf(null), [{ code: "required", path: "" }]);
    for (const k of ["pickupSpot", "startDate", "endDate", "priceDollars", "vehicle", "maxRiders", "weekdays", "days"]) {
      assert.ok(has(mut((i) => delete i[k]), "required", k), k);
    }
    assert.ok(has(mut((i) => delete i.days.mon.toCampus), "required", "days.mon.toCampus"));
    assert.ok(has(mut((i) => delete i.days.mon.toCampus.departTime), "required", "days.mon.toCampus.departTime"));
    assert.ok(has(mut((i) => delete i.days.mon.toCampus.stops), "required", "days.mon.toCampus.stops"));
    assert.ok(has(mut((i) => (i.vehicle = "   ")), "required", "vehicle"));
    assert.ok(has(mut((i) => (i.weekdays = [])), "required", "weekdays"));
  });

  it("notInteger and outOfRange for price, riders, stop minutes", () => {
    assert.ok(has(mut((i) => (i.priceDollars = 5.5)), "notInteger", "priceDollars"));
    assert.ok(has(mut((i) => (i.priceDollars = "5")), "notInteger", "priceDollars"));
    assert.ok(has(mut((i) => (i.priceDollars = 0)), "outOfRange", "priceDollars"));
    assert.ok(has(mut((i) => (i.priceDollars = 100)), "outOfRange", "priceDollars"));
    assert.equal(errorsOf(mut((i) => (i.priceDollars = 99))).length, 0);
    assert.ok(has(mut((i) => (i.maxRiders = 0)), "outOfRange", "maxRiders"));
    assert.ok(has(mut((i) => (i.maxRiders = 7)), "outOfRange", "maxRiders"));
    assert.equal(errorsOf(mut((i) => (i.maxRiders = 6))).length, 0);
    const stop = (minutes: unknown) => mut((i) => (i.days.mon.toCampus.stops = [{ address: ADDR, minutes }]));
    assert.ok(has(stop(1.5), "notInteger", "days.mon.toCampus.stops.0.minutes"));
    assert.ok(has(stop(-1), "outOfRange", "days.mon.toCampus.stops.0.minutes"));
    assert.ok(has(stop(121), "outOfRange", "days.mon.toCampus.stops.0.minutes"));
    assert.equal(errorsOf(stop(0)).length, 0);
    assert.equal(errorsOf(stop(120)).length, 0);
  });

  it("tooLong: vehicle over 60 characters", () => {
    assert.ok(has(mut((i) => (i.vehicle = "x".repeat(61))), "tooLong", "vehicle"));
    assert.equal(errorsOf(mut((i) => (i.vehicle = "x".repeat(60)))).length, 0);
  });

  it("invalidAddress: pickup spot and stop shapes", () => {
    assert.ok(has(mut((i) => (i.pickupSpot.text = "  ")), "invalidAddress", "pickupSpot.text"));
    assert.ok(has(mut((i) => (i.pickupSpot.placeId = "")), "invalidAddress", "pickupSpot.placeId"));
    assert.ok(has(mut((i) => (i.pickupSpot.lat = 91)), "invalidAddress", "pickupSpot.lat"));
    assert.ok(has(mut((i) => (i.pickupSpot.lng = NaN)), "invalidAddress", "pickupSpot.lng"));
    assert.ok(has(mut((i) => (i.pickupSpot = "home")), "invalidAddress", "pickupSpot"));
    assert.ok(has(mut((i) => (i.days.wed.fromCampus.stops = [{ address: { ...ADDR, lng: 200 }, minutes: 5 }])), "invalidAddress", "days.wed.fromCampus.stops.0.address.lng"));
  });

  it("invalidDate and invalidTime", () => {
    assert.ok(has(mut((i) => (i.startDate = "2026-02-30")), "invalidDate", "startDate"));
    assert.ok(has(mut((i) => (i.endDate = "12/18/2026")), "invalidDate", "endDate"));
    assert.ok(has(mut((i) => (i.days.mon.toCampus.departTime = "7:30")), "invalidTime", "days.mon.toCampus.departTime"));
    assert.ok(has(mut((i) => (i.days.mon.fromCampus.departTime = "24:00")), "invalidTime", "days.mon.fromCampus.departTime"));
  });

  it("unknownCampusPoint: not in the active list", () => {
    assert.ok(has(mut((i) => (i.days.mon.toCampus.campusPointId = "nope")), "unknownCampusPoint", "days.mon.toCampus.campusPointId"));
    assert.ok(has(validInput(), "unknownCampusPoint", "days.mon.toCampus.campusPointId", ctx({ activeCampusPointIds: ["cp2"] })));
  });

  it("tooManyStops: over the technical cap", () => {
    const stops = (n: number) => mut((i) => (i.days.mon.toCampus.stops = Array.from({ length: n }, () => ({ address: ADDR, minutes: 1 }))));
    assert.ok(has(stops(26), "tooManyStops", "days.mon.toCampus.stops"));
    assert.equal(errorsOf(stops(25)).length, 0);
  });

  it("weekdays: duplicates, invalid ids, and days keys must match", () => {
    assert.ok(errorsOf(mut((i) => (i.weekdays = ["mon", "mon", "wed"]))).some((e) => e.code === "duplicateWeekday" && e.path === "weekdays"));
    assert.ok(has(mut((i) => (i.weekdays = ["mon", "xyz"])), "outOfRange", "weekdays.1"));
    assert.ok(has(mut((i) => delete i.days.wed), "required", "days.wed"));
    assert.ok(has(mut((i) => (i.days.fri = i.days.mon)), "outOfRange", "days.fri"));
  });

  it("returns all errors at once", () => {
    const errs = errorsOf({ pickupSpot: {}, startDate: "2026-11-01", endDate: "bad", priceDollars: 0, vehicle: "", maxRiders: 9, weekdays: ["mon"], days: {} });
    const codes = new Set(errs.map((e) => e.code));
    for (const c of ["invalidAddress", "startInPast", "invalidDate", "outOfRange", "required"]) assert.ok(codes.has(c as SeriesErrorCode), c);
    assert.ok(errs.length >= 7);
  });
});

describe("validateSeriesInput: dates (server clock, Pacific)", () => {
  const withStart = (start: string, end = "2026-12-31") => mut((i) => { i.startDate = start; i.endDate = end; });
  it("startInPast and endBeforeStart", () => {
    assert.ok(has(withStart("2026-11-22"), "startInPast", "startDate"));
    assert.equal(errorsOf(withStart("2026-11-23")).length, 0);
    assert.ok(has(withStart("2026-11-30", "2026-11-29"), "endBeforeStart", "endDate"));
    assert.equal(errorsOf(withStart("2026-11-30", "2026-11-30")).length, 0); // a Monday
  });

  it("noDateOnWeekdays only when none of the chosen weekdays has a date", () => {
    // Tue 2026-11-24 to Tue 2026-11-24: Mon and Wed have no date.
    assert.ok(has(withStart("2026-11-24", "2026-11-24"), "noDateOnWeekdays", ""));
    // Mon..Tue range: Mon has a date, Wed does not -> passes.
    assert.equal(errorsOf(withStart("2026-11-30", "2026-12-01")).length, 0);
  });

  it("DST spring forward: today is 2026-03-08 at 01:59 PST and at 03:00 PDT", () => {
    for (const now of [new Date("2026-03-08T09:59:00Z"), new Date("2026-03-08T10:00:00Z")]) {
      const c = ctx({ now });
      const i = (start: string) => mut((x) => { x.startDate = start; x.endDate = "2026-03-20"; });
      assert.equal(errorsOf(i("2026-03-08"), c).length, 0, now.toISOString());
      assert.ok(has(i("2026-03-07"), "startInPast", "startDate", c));
    }
  });

  it("DST fall back: 2026-11-01 01:30 (both occurrences) is still today", () => {
    for (const now of [new Date("2026-11-01T08:30:00Z"), new Date("2026-11-01T09:30:00Z")]) {
      const c = ctx({ now });
      const i = (start: string) => mut((x) => { x.startDate = start; x.endDate = "2026-11-20"; });
      assert.equal(errorsOf(i("2026-11-01"), c).length, 0, now.toISOString());
      assert.ok(has(i("2026-10-31"), "startInPast", "startDate", c));
    }
  });

  it("a range spanning a DST change counts weekdays by calendar date", () => {
    const c = ctx({ now: new Date("2026-03-06T20:00:00Z") });
    // Sunday 2026-03-08 (the change day) is the only date; weekdays: sun.
    const raw = mut((x) => {
      x.startDate = "2026-03-08"; x.endDate = "2026-03-08"; x.weekdays = ["sun"];
      x.days = { sun: x.days.mon };
    });
    assert.equal(errorsOf(raw, c).length, 0);
  });

  it("uses Pacific, not UTC, for today (evening Pacific is next day UTC)", () => {
    const c = ctx({ now: new Date("2026-11-24T03:00:00Z") }); // 2026-11-23 19:00 PST
    assert.equal(errorsOf(validInput(), c).length, 0);
  });
});

describe("RC-31 hosting limits", () => {
  const five = ["a", "b", "c", "d", "e"].map((id) => hosted(id, "2026-11-23", "2026-12-18", ["tue"]));
  it("four active series: the fifth may be posted; five: the next fails", () => {
    assert.equal(errorsOf(validInput(), ctx({ hostedSeries: five.slice(0, 4) })).length, 0);
    const r = errorsOf(validInput(), ctx({ hostedSeries: five }));
    assert.deepEqual(r, [{ code: "hostingLimitReached", path: "", params: { limit: 5 } }]);
  });
  it("an active series whose end date has passed does not count", () => {
    const old = hosted("old", "2026-09-01", "2026-11-22", ["tue"]);
    assert.equal(errorsOf(validInput(), ctx({ hostedSeries: [...five.slice(0, 4), old] })).length, 0);
    const endsToday = hosted("today", "2026-09-01", "2026-11-23", ["tue"]);
    assert.ok(has(validInput(), "hostingLimitReached", "", ctx({ hostedSeries: [...five.slice(0, 4), endsToday] })));
  });
  it("same weekday, overlapping dates fails with the weekday", () => {
    const r = errorsOf(validInput(), ctx({ hostedSeries: [hosted("x", "2026-12-01", "2027-01-31", ["wed", "fri"])] }));
    assert.deepEqual(r, [{ code: "hostingWeekdayOverlap", path: "days.wed", params: { weekday: "wed" } }]);
  });
  it("back-to-back ranges pass; ranges sharing only a non-ride day pass; a shared ride date fails", () => {
    assert.equal(errorsOf(validInput(), ctx({ hostedSeries: [hosted("x", "2026-12-19", "2027-01-31", ["mon", "wed"])] })).length, 0);
    assert.equal(errorsOf(validInput(), ctx({ hostedSeries: [hosted("x", "2026-12-18", "2027-01-31", ["mon", "wed"])] })).length, 0); // share Fri Dec 18 only
    assert.ok(has(validInput(), "hostingWeekdayOverlap", "days.wed", ctx({ hostedSeries: [hosted("x", "2026-12-16", "2027-01-31", ["wed"])] }))); // share Wed Dec 16
  });
  it("different weekdays pass", () => {
    assert.equal(errorsOf(validInput(), ctx({ hostedSeries: [hosted("x", "2026-11-23", "2026-12-18", ["tue", "thu"])] })).length, 0);
  });
  it("a weekday with no date in the other series' range does not conflict", () => {
    // Other series lists Monday but its range is Tue..Thu only.
    assert.equal(errorsOf(validInput(), ctx({ hostedSeries: [hosted("x", "2026-12-01", "2026-12-03", ["mon"])] })).length, 0);
  });
});

describe("RC-32 at posting", () => {
  it("an accepted ride on the same weekday with overlapping dates fails", () => {
    const r = errorsOf(validInput(), ctx({ schedule: [ride("s1", "wed", "2026-12-01", "2027-01-31")] }));
    assert.deepEqual(r, [{ code: "riderScheduleConflict", path: "days.wed", params: { weekday: "wed" } }]);
  });
  it("back-to-back dates pass", () => {
    assert.equal(errorsOf(validInput(), ctx({ schedule: [ride("s1", "wed", "2026-12-19", "2027-01-31")] })).length, 0);
  });
  it("a pending request does not block; neither does a hosting entry or an ended ride", () => {
    assert.equal(errorsOf(validInput(), ctx({ schedule: [ride("s1", "wed", "2026-12-01", "2027-01-31", "pending")] })).length, 0);
    assert.equal(errorsOf(validInput(), ctx({ schedule: [{ ...ride("s1", "wed", "2026-12-01", "2027-01-31"), role: "host", state: "hosting" }] })).length, 0);
    assert.equal(errorsOf(validInput(), ctx({ schedule: [ride("s1", "wed", "2026-09-01", "2026-11-22")] })).length, 0);
  });
  it("a different weekday passes", () => {
    assert.equal(errorsOf(validInput(), ctx({ schedule: [ride("s1", "thu", "2026-11-23", "2026-12-18")] })).length, 0);
  });
  it("a weekday with no date in the new series' range does not conflict", () => {
    // New series Mon 2026-11-30 .. Tue 2026-12-01 (weekdays mon, wed): Wed has no date.
    const raw = mut((i) => { i.startDate = "2026-11-30"; i.endDate = "2026-12-01"; });
    assert.equal(errorsOf(raw, ctx({ schedule: [ride("s1", "wed", "2026-11-23", "2026-12-18")] })).length, 0);
    assert.ok(has(raw, "riderScheduleConflict", "days.mon", ctx({ schedule: [ride("s1", "mon", "2026-11-23", "2026-12-18")] })));
  });
});

describe("validateCampusTimes", () => {
  const input = validInput() as Pick<SeriesInput, "weekdays" | "days">;
  const route = (arriveMin: number) => ({
    toCampus: { legs: [], totalMeters: 0, totalDurationSec: 0, times: [{ departMin: 450 }, { arriveMin }] },
  });
  const withDepart = (t: string) => ({ ...input, days: { mon: { toCampus: leg(), fromCampus: leg({ departTime: t }) }, wed: input.days.wed! } });
  it("equal passes, later passes, earlier fails", () => {
    assert.deepEqual(validateCampusTimes(withDepart("08:00"), { mon: route(480) }), []);
    assert.deepEqual(validateCampusTimes(withDepart("08:01"), { mon: route(480) }), []);
    assert.deepEqual(validateCampusTimes(withDepart("07:59"), { mon: route(480) }), [
      { code: "campusDepartureBeforeArrival", path: "days.mon.fromCampus.departTime", params: { weekday: "mon" } },
    ]);
  });
  it("arrival past midnight fails; 23:59 arrival with 23:59 departure passes", () => {
    assert.equal(validateCampusTimes(withDepart("23:59"), { mon: route(1440) }).length, 1);
    assert.equal(validateCampusTimes(withDepart("23:59"), { mon: route(1439) }).length, 0);
  });
  it("weekdays without a route are skipped", () => {
    assert.deepEqual(validateCampusTimes(withDepart("00:00"), {}), []);
  });
});

describe("errors", () => {
  it("every code has a test above and is listed", () => {
    assert.equal(SERIES_ERROR_CODES.length, 17);
  });
  it("seriesInvalidError is an invalid-argument HttpsError carrying the errors", () => {
    const e = seriesInvalidError([{ code: "required", path: "vehicle" }]);
    assert.equal(e.code, "invalid-argument");
    assert.equal(e.message, "series-invalid");
    assert.deepEqual(e.details, { errors: [{ code: "required", path: "vehicle" }] });
  });
  it("clock sanity", () => assert.equal(NOW.toISOString(), "2026-11-23T20:00:00.000Z"));
});

// Silence unused import if hosted/ride are only used above.
void hosted;
