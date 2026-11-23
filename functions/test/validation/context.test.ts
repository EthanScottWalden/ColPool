import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import { getFirestore } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { loadValidationContext } from "../../src/shared/validation/context.ts";
import { validateSeriesInput } from "../../src/shared/validation/index.ts";
import { NOW, leg, validInput } from "./fixtures.ts";

const db = getFirestore(adminApp());
const seriesDoc = (hostId: string, status: string, weekdays: string[], startDate = "2026-11-23", endDate = "2026-12-18") => ({
  hostId, status, weekdays, startDate, endDate,
});

describe("loadValidationContext (emulator)", () => {
  before(async () => {
    assert.ok(process.env["FIRESTORE_EMULATOR_HOST"], "run via emulators:exec");
    await db.doc("campusPoints/vctx-a").set({ name: "A", lat: 1, lng: 1, order: 1, active: true });
    await db.doc("campusPoints/vctx-b").set({ name: "B", lat: 1, lng: 1, order: 2, active: false });
    await db.doc("series/vctx-1").set(seriesDoc("vctx-host", "active", ["mon"]));
    await db.doc("series/vctx-2").set(seriesDoc("vctx-host", "cancelled", ["tue"]));
    await db.doc("series/vctx-3").set(seriesDoc("vctx-host", "ended", ["wed"]));
    await db.doc("series/vctx-4").set(seriesDoc("vctx-other", "active", ["thu"]));
    await db.doc("schedules/vctx-host").set({
      entries: {
        vctx_r_fri: { seriesId: "vctx_r", weekday: "fri", role: "rider", state: "accepted", startDate: "2026-11-23", endDate: "2026-12-18" },
      },
    });
  });

  it("loads active campus points, only the host's active series, and the schedule", async () => {
    const c = await loadValidationContext(db, "vctx-host", NOW);
    assert.ok(c.activeCampusPointIds.includes("vctx-a"));
    assert.ok(!c.activeCampusPointIds.includes("vctx-b"));
    assert.deepEqual(c.hostedSeries, [{ id: "vctx-1", startDate: "2026-11-23", endDate: "2026-12-18", weekdays: ["mon"] }]);
    assert.equal(c.schedule.length, 1);
    assert.equal(c.schedule[0]?.weekday, "fri");
    assert.equal(c.now, NOW);
  });

  it("a missing schedule doc is empty", async () => {
    const c = await loadValidationContext(db, "vctx-nobody", NOW);
    assert.deepEqual(c.schedule, []);
    assert.deepEqual(c.hostedSeries, []);
  });

  it("works inside a transaction and feeds the validator end to end", async () => {
    const result = await db.runTransaction(async (tx) => {
      const c = await loadValidationContext(db, "vctx-host", NOW, tx);
      const l = leg({ campusPointId: "vctx-a" });
      const day = { toCampus: l, fromCampus: l };
      const input = validInput({ weekdays: ["mon", "fri"], days: { mon: day, fri: day } });
      return validateSeriesInput(input, c);
    });
    assert.ok(!result.ok);
    assert.deepEqual(result.errors.map((e) => `${e.code}@${e.path}`).sort(), ["hostingWeekdayOverlap@days.mon", "riderScheduleConflict@days.fri"]);
  });
});
