import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { conflictingWeekdays } from "../../src/shared/validation/conflicts.ts";

const MW = ["mon", "wed"] as const;
const span = (startDate: string, endDate: string, weekdays: readonly ("mon" | "tue" | "wed" | "thu" | "fri")[] = MW) => ({ startDate, endDate, weekdays });

describe("conflictingWeekdays (owner decision 2026-11-23: a shared ride date, today or later)", () => {
  const today = "2026-08-24";
  it("ranges overlapping only on days neither rides do not conflict", () => {
    // S1 Mon/Wed Aug 24 to Fri Dec 11; S2 Mon/Wed from Thu Dec 10: shared Thu 10 and Fri 11 only.
    assert.deepEqual(conflictingWeekdays(span("2026-08-24", "2026-12-11"), span("2026-12-10", "2027-03-01"), today), []);
  });
  it("sharing Wed Dec 9 conflicts on wed only", () => {
    assert.deepEqual(conflictingWeekdays(span("2026-08-24", "2026-12-11"), span("2026-12-09", "2027-03-01"), today), ["wed"]);
  });
  it("shared dates all before today do not conflict", () => {
    assert.deepEqual(conflictingWeekdays(span("2026-08-24", "2026-12-11"), span("2026-12-09", "2027-03-01"), "2026-12-10"), []);
    assert.deepEqual(conflictingWeekdays(span("2026-08-24", "2026-12-11"), span("2026-12-09", "2027-03-01"), "2026-12-09"), ["wed"]);
  });
  it("a weekday with no date in range never conflicts", () => {
    assert.deepEqual(conflictingWeekdays(span("2026-11-30", "2026-12-01"), span("2026-11-23", "2026-12-18"), "2026-11-23"), ["mon"]);
  });
  it("different weekdays do not conflict", () => {
    assert.deepEqual(conflictingWeekdays(span("2026-11-23", "2026-12-18", ["tue"]), span("2026-11-23", "2026-12-18"), "2026-11-23"), []);
  });
  it("a DST-week shared date conflicts (Sun 2026-03-08 spring forward, Mon 2026-03-09)", () => {
    assert.deepEqual(conflictingWeekdays(span("2026-03-02", "2026-03-09"), span("2026-03-09", "2026-03-20"), "2026-03-06"), ["mon"]);
    assert.deepEqual(conflictingWeekdays(span("2026-03-02", "2026-03-08"), span("2026-03-08", "2026-03-20"), "2026-03-06"), []);
  });
});
