import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  PACIFIC_TZ,
  addDays,
  computeLastDepartureAt,
  hasTripsLeft,
  isLocalTime,
  lastDateOnWeekday,
  pacificDateOf,
  pacificInstant,
  rangesOverlap,
  weekdayOf,
} from "../../src/shared/time/index.ts";
import type { Weekday } from "../../src/shared/schema/index.ts";

const z = (iso: string) => new Date(iso);
const iso = (d: Date) => d.toISOString();

describe("isLocalTime", () => {
  it("accepts 00:00 to 23:59 and rejects the rest", () => {
    for (const ok of ["00:00", "07:30", "23:59"]) assert.equal(isLocalTime(ok), true, ok);
    for (const bad of ["24:00", "7:30", "07:60", "07:30:00", "", "ab:cd", 730, null]) assert.equal(isLocalTime(bad), false, String(bad));
  });
});

describe("pacificInstant around daylight saving", () => {
  it("is Pacific", () => assert.equal(PACIFIC_TZ, "America/Los_Angeles"));

  it("spring forward 2026-03-08", () => {
    assert.equal(iso(pacificInstant("2026-03-06", "07:30")), "2026-03-06T15:30:00.000Z"); // PST
    assert.equal(iso(pacificInstant("2026-03-09", "07:30")), "2026-03-09T14:30:00.000Z"); // PDT
    assert.equal(iso(pacificInstant("2026-03-08", "02:30")), "2026-03-08T10:30:00.000Z"); // gap shifts forward
    assert.equal(iso(pacificInstant("2026-03-08", "01:59")), "2026-03-08T09:59:00.000Z");
    assert.equal(iso(pacificInstant("2026-03-08", "03:00")), "2026-03-08T10:00:00.000Z");
  });

  it("fall back 2026-11-01", () => {
    assert.equal(iso(pacificInstant("2026-10-30", "07:30")), "2026-10-30T14:30:00.000Z"); // PDT
    assert.equal(iso(pacificInstant("2026-11-02", "07:30")), "2026-11-02T15:30:00.000Z"); // PST
    assert.equal(iso(pacificInstant("2026-11-01", "01:30")), "2026-11-01T08:30:00.000Z"); // repeated: earlier (PDT)
    assert.equal(iso(pacificInstant("2026-11-01", "02:00")), "2026-11-01T10:00:00.000Z"); // PST
  });

  it("rejects malformed input", () => {
    assert.throws(() => pacificInstant("2026-02-30", "07:30"));
    assert.throws(() => pacificInstant("2026-3-1", "07:30"));
    assert.throws(() => pacificInstant("2026-03-01", "7:30"));
  });
});

describe("pacificDateOf", () => {
  it("November (PST): midnight is 08:00Z", () => {
    assert.equal(pacificDateOf(z("2026-11-10T07:59:00Z")), "2026-11-09");
    assert.equal(pacificDateOf(z("2026-11-10T08:00:00Z")), "2026-11-10");
  });
  it("summer (PDT): midnight is 07:00Z", () => {
    assert.equal(pacificDateOf(z("2026-07-10T06:59:00Z")), "2026-07-09");
    assert.equal(pacificDateOf(z("2026-07-10T07:00:00Z")), "2026-07-10");
  });
  it("accepts epoch milliseconds", () => {
    assert.equal(pacificDateOf(z("2026-07-10T07:00:00Z").getTime()), "2026-07-10");
  });
});

describe("weekdayOf and addDays", () => {
  it("names weekdays Monday-first ids", () => {
    assert.equal(weekdayOf("2026-03-09"), "mon");
    assert.equal(weekdayOf("2026-03-08"), "sun");
    assert.equal(weekdayOf("2026-11-01"), "sun");
    assert.equal(weekdayOf("2026-11-06"), "fri");
  });
  it("adds days across DST, month and year ends", () => {
    assert.equal(addDays("2026-03-07", 2), "2026-03-09");
    assert.equal(addDays("2026-10-31", 2), "2026-11-02");
    assert.equal(addDays("2026-12-31", 1), "2027-01-01");
    assert.equal(addDays("2026-03-01", -1), "2026-02-28");
    assert.equal(addDays("2028-02-28", 1), "2028-02-29");
  });
});

describe("rangesOverlap", () => {
  const r = (startDate: string, endDate: string) => ({ startDate, endDate });
  it("is inclusive at both ends", () => {
    assert.equal(rangesOverlap(r("2026-03-01", "2026-03-10"), r("2026-03-10", "2026-03-20")), true);
    assert.equal(rangesOverlap(r("2026-03-10", "2026-03-20"), r("2026-03-01", "2026-03-10")), true);
  });
  it("is false for disjoint ranges and true for containment", () => {
    assert.equal(rangesOverlap(r("2026-03-01", "2026-03-09"), r("2026-03-10", "2026-03-20")), false);
    assert.equal(rangesOverlap(r("2026-03-01", "2026-03-31"), r("2026-03-10", "2026-03-11")), true);
  });
});

describe("lastDateOnWeekday", () => {
  it("uses the earlier matching date when the end date is another weekday", () => {
    // 2026-03-13 is a Friday. Last Monday in range is 03-09.
    assert.equal(lastDateOnWeekday("2026-03-02", "2026-03-13", "mon"), "2026-03-09");
    assert.equal(lastDateOnWeekday("2026-03-02", "2026-03-13", "fri"), "2026-03-13");
  });
  it("is null when the weekday does not occur in range", () => {
    assert.equal(lastDateOnWeekday("2026-03-09", "2026-03-10", "fri"), null);
    assert.equal(lastDateOnWeekday("2026-03-09", "2026-03-09", "tue"), null);
  });
  it("handles a one-day range on that weekday and start == end", () => {
    assert.equal(lastDateOnWeekday("2026-03-09", "2026-03-09", "mon"), "2026-03-09");
  });
  it("is null when the start date is after the end date", () => {
    assert.equal(lastDateOnWeekday("2026-03-10", "2026-03-09", "mon"), null);
  });
  it("rejects a malformed end date instead of returning null", () => {
    assert.throws(() => lastDateOnWeekday("2026-03-02", "2026-3-1", "mon"));
  });
});

describe("computeLastDepartureAt and hasTripsLeft", () => {
  const WD: Weekday[] = ["mon", "tue", "wed", "thu", "fri"];
  const times = Object.fromEntries(WD.map((w) => [w, "07:30"])) as Record<Weekday, string>;

  it("Mon-Fri series across the spring-forward week", () => {
    const out = computeLastDepartureAt({ startDate: "2026-03-02", endDate: "2026-03-13", weekdays: WD, departTimes: times });
    assert.equal(iso(out.mon as Date), "2026-03-09T14:30:00.000Z");
    assert.equal(iso(out.fri as Date), "2026-03-13T14:30:00.000Z"); // PDT after 03-08
  });

  it("Mon-Fri series that ends in the spring-forward week (Friday before the change)", () => {
    const out = computeLastDepartureAt({ startDate: "2026-03-02", endDate: "2026-03-06", weekdays: WD, departTimes: times });
    assert.equal(iso(out.fri as Date), "2026-03-06T15:30:00.000Z"); // PST
    assert.equal(iso(out.mon as Date), "2026-03-02T15:30:00.000Z");
  });

  it("Mon-Fri series across the fall-back week", () => {
    const out = computeLastDepartureAt({ startDate: "2026-10-26", endDate: "2026-11-06", weekdays: WD, departTimes: times });
    assert.equal(iso(out.mon as Date), "2026-11-02T15:30:00.000Z"); // PST
    assert.equal(iso(out.fri as Date), "2026-11-06T15:30:00.000Z");
    const before = computeLastDepartureAt({ startDate: "2026-10-26", endDate: "2026-10-30", weekdays: WD, departTimes: times });
    assert.equal(iso(before.fri as Date), "2026-10-30T14:30:00.000Z"); // PDT
  });

  it("a weekday with no date in range has no key and never has trips left", () => {
    const out = computeLastDepartureAt({ startDate: "2026-03-09", endDate: "2026-03-10", weekdays: ["mon", "fri"], departTimes: times });
    assert.deepEqual(Object.keys(out), ["mon"]);
    assert.equal("fri" in out, false);
    assert.equal(hasTripsLeft(out.fri, z("2000-01-01T00:00:00Z")), false);
  });

  it("throws when a selected weekday has no departure time", () => {
    assert.throws(() => computeLastDepartureAt({ startDate: "2026-03-09", endDate: "2026-03-10", weekdays: ["mon"], departTimes: {} }));
  });

  it("last departure Mon 2026-03-09 07:30 is true at 14:29Z and false at 14:31Z", () => {
    const out = computeLastDepartureAt({ startDate: "2026-03-02", endDate: "2026-03-09", weekdays: ["mon"], departTimes: times });
    assert.equal(hasTripsLeft(out.mon, z("2026-03-09T14:29:00Z")), true);
    assert.equal(hasTripsLeft(out.mon, z("2026-03-09T14:31:00Z")), false);
  });

  it("is strict: 1 ms before is true, exactly at is false", () => {
    const last = z("2026-03-09T14:30:00.000Z");
    assert.equal(hasTripsLeft(last, new Date(last.getTime() - 1)), true);
    assert.equal(hasTripsLeft(last, last), false);
    assert.equal(hasTripsLeft(last, new Date(last.getTime() + 1)), false);
    assert.equal(hasTripsLeft(undefined, last), false);
    assert.equal(hasTripsLeft(null, last), false);
  });

  it("the end date not on the weekday uses the earlier matching date", () => {
    // End Friday 03-13; Monday's last trip is 03-09, so on Wed 03-11 Monday has no trips left.
    const out = computeLastDepartureAt({ startDate: "2026-03-02", endDate: "2026-03-13", weekdays: ["mon", "fri"], departTimes: times });
    const wed = z("2026-03-11T19:00:00Z");
    assert.equal(hasTripsLeft(out.mon, wed), false);
    assert.equal(hasTripsLeft(out.fri, wed), true);
  });
});
