import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { createSendRequestHandler } from "../../src/sendRequest.ts";
import { COLLECTIONS } from "../../src/shared/schema/collections.ts";
import { reqReason, scheduleEntryKey } from "../../src/shared/schema/keys.ts";
import { requestId } from "../../src/shared/schema/request.ts";
import type { Weekday } from "../../src/shared/schema/weekday.ts";

const db = getFirestore(adminApp());
// Fixed clock: Monday 2026-11-23 12:00 PST.
const NOW = new Date("2026-11-23T20:00:00Z");
const silent = { info() {}, error() {} };
const send = createSendRequestHandler({ db, now: () => NOW, logger: silent });

type Auth = CallableRequest<unknown>["auth"];
let n = 0;
const nextId = (prefix: string) => `${prefix}${Date.now()}x${n++}`;
// Series ids must be 20 alphanumerics (DRAFT_ID_PATTERN).
const seriesIdOf = () => (String(Date.now()) + String(n++).padStart(7, "0") + "abcdefghijklmnopqrst").slice(0, 20);

async function student(first = "Test"): Promise<Auth> {
  const uid = nextId("sr-");
  await db.collection(COLLECTIONS.consents).doc(uid).set({ agreedAt: Timestamp.now() });
  await db.collection(COLLECTIONS.profiles).doc(uid).set({ firstName: first, lastName: "Person" });
  return { uid, token: {} as never, rawToken: "x" };
}
const req = (auth: Auth, data: unknown): CallableRequest<unknown> => ({ data, auth, rawRequest: {} as CallableRequest["rawRequest"], acceptsStreaming: false });

async function rejects(p: Promise<unknown>, code: string, check?: (details: any) => void): Promise<void> {
  await assert.rejects(p, (e: { code?: string; details?: any }) => {
    assert.equal(e.code, code);
    check?.(e.details);
    return true;
  });
}

interface SeriesOpts {
  status?: string;
  weekdays?: Weekday[];
  openSeats?: Partial<Record<Weekday, number>>;
  lastDepartureAt?: Partial<Record<Weekday, string>>;
  startDate?: string;
  endDate?: string;
}
async function postedSeries(hostUid: string, o: SeriesOpts = {}): Promise<string> {
  const id = seriesIdOf();
  const weekdays = o.weekdays ?? ["mon", "wed"];
  const openSeats: Record<string, number> = {};
  const last: Record<string, Timestamp> = {};
  for (const wd of weekdays) {
    openSeats[wd] = o.openSeats?.[wd] ?? 2;
    last[wd] = Timestamp.fromDate(new Date(o.lastDepartureAt?.[wd] ?? "2026-12-16T16:00:00Z"));
  }
  await db.collection(COLLECTIONS.series).doc(id).set({
    hostId: hostUid, status: o.status ?? "active", pickupSpot: { text: "1 Pickup Way", placeId: "p", lat: 1, lng: 2 },
    startDate: o.startDate ?? "2026-11-16", endDate: o.endDate ?? "2026-12-18", priceDollars: 5, vehicle: "Car", maxRiders: 2,
    weekdays, days: {}, openSeats, openWeekdays: weekdays.filter((w) => openSeats[w]! > 0), lastDepartureAt: last,
  });
  return id;
}

const snap = async (path: string) => (await db.doc(path).get()).data();
async function counts(uid: string, hostUid: string, id: string) {
  return {
    request: await snap(`requests/${requestId(id, uid)}`),
    schedule: await snap(`schedules/${uid}`),
    access: await snap(`profileAccess/${uid}`),
    hostAccess: await snap(`profileAccess/${hostUid}`),
    notifs: (await db.collection(`users/${hostUid}/notifications`).get()).size,
  };
}
async function setSchedule(uid: string, entries: Record<string, unknown>) {
  await db.doc(`schedules/${uid}`).set({ entries });
}
const entry = (seriesId: string, weekday: Weekday, state: string, startDate: string, endDate: string) =>
  ({ seriesId, weekday, role: state === "hosting" ? "host" : "rider", state, startDate, endDate });

describe("sendRequest", () => {
  it("creates the request, pending entries, both profile grants and one host notification", async () => {
    const host = await student("Hosty");
    const rider = await student("Ryder");
    const id = await postedSeries(host!.uid);
    const out = await send(req(rider, { seriesId: id, weekdays: ["wed", "mon"] }));
    assert.deepEqual(out, { requestId: `${id}_${rider!.uid}` });

    const s = await counts(rider!.uid, host!.uid, id);
    const r = s.request as any;
    assert.deepEqual({ ...r, createdAt: undefined }, {
      seriesId: id, riderId: rider!.uid, hostId: host!.uid, weekdays: ["mon", "wed"],
      days: { mon: { status: "pending" }, wed: { status: "pending" } }, createdAt: undefined,
    });
    assert.ok(r.createdAt);
    for (const wd of ["mon", "wed"] as Weekday[]) {
      assert.deepEqual((s.schedule as any).entries[scheduleEntryKey(id, wd)], {
        seriesId: id, weekday: wd, role: "rider", state: "pending", startDate: "2026-11-16", endDate: "2026-12-18",
      });
    }
    assert.deepEqual(s.access, { viewers: { [host!.uid]: { [reqReason(id)]: true } } });
    assert.deepEqual(s.hostAccess, { viewers: { [rider!.uid]: { [reqReason(id)]: true } } });
    const notif = await snap(`users/${host!.uid}/notifications/request_new_${id}_${rider!.uid}`) as any;
    assert.equal(notif.type, "request_new");
    assert.deepEqual(notif.weekdays, ["mon", "wed"]);
    assert.equal(s.notifs, 1);
    // Seats are untouched.
    assert.deepEqual(((await snap(`series/${id}`)) as any).openSeats, { mon: 2, wed: 2 });
  });

  it("rejects a bad series id or weekday list", async () => {
    const rider = await student();
    await rejects(send(req(rider, { seriesId: "short", weekdays: ["mon"] })), "invalid-argument", (d) => assert.equal(d.reason, "series-id-invalid"));
    const id = seriesIdOf();
    for (const weekdays of [[], ["mon", "mon"], ["xyz"], "mon", undefined]) {
      await rejects(send(req(rider, { seriesId: id, weekdays })), "invalid-argument", (d) => assert.equal(d.reason, "weekdays-invalid"));
    }
  });

  it("rejects a missing series, own series, a cancelled series and a weekday not in the series", async () => {
    const host = await student();
    const rider = await student();
    await rejects(send(req(rider, { seriesId: seriesIdOf(), weekdays: ["mon"] })), "not-found");
    const id = await postedSeries(host!.uid);
    await rejects(send(req(host, { seriesId: id, weekdays: ["mon"] })), "failed-precondition", (d) => assert.equal(d.reason, "own-series"));
    const cancelled = await postedSeries(host!.uid, { status: "cancelled" });
    await rejects(send(req(rider, { seriesId: cancelled, weekdays: ["mon"] })), "failed-precondition", (d) => assert.equal(d.reason, "series-not-active"));
    await rejects(send(req(rider, { seriesId: id, weekdays: ["fri"] })), "invalid-argument", (d) => assert.equal(d.reason, "weekdays-invalid"));
  });

  it("rejects a second request, even with different weekdays", async () => {
    const host = await student();
    const rider = await student();
    const id = await postedSeries(host!.uid);
    await send(req(rider, { seriesId: id, weekdays: ["mon"] }));
    await rejects(send(req(rider, { seriesId: id, weekdays: ["mon"] })), "already-exists", (d) => assert.equal(d.reason, "already-requested"));
    await rejects(send(req(rider, { seriesId: id, weekdays: ["wed"] })), "already-exists", (d) => assert.equal(d.reason, "already-requested"));
    assert.equal((await counts(rider!.uid, host!.uid, id)).notifs, 1);
  });

  it("parallel duplicate requests create exactly one request and one notification", async () => {
    const host = await student();
    const rider = await student();
    const id = await postedSeries(host!.uid);
    const results = await Promise.allSettled(Array.from({ length: 5 }, () => send(req(rider, { seriesId: id, weekdays: ["mon"] }))));
    assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
    for (const r of results) if (r.status === "rejected") assert.equal(r.reason.code, "already-exists");
    assert.equal((await counts(rider!.uid, host!.uid, id)).notifs, 1);
  });

  it("rejects the whole request when a weekday is full, and writes nothing", async () => {
    const host = await student();
    const rider = await student();
    const id = await postedSeries(host!.uid, { openSeats: { wed: 0 } });
    await rejects(send(req(rider, { seriesId: id, weekdays: ["mon", "wed"] })), "failed-precondition", (d) => {
      assert.equal(d.reason, "weekdays-unavailable");
      assert.deepEqual(d.weekdays, [{ weekday: "wed", reason: "full" }]);
    });
    const s = await counts(rider!.uid, host!.uid, id);
    assert.deepEqual([s.request, s.schedule, s.access, s.hostAccess, s.notifs], [undefined, undefined, undefined, undefined, 0]);
  });

  describe("trips left", () => {
    const check = async (lastIso: string, now: Date, ok: boolean) => {
      const host = await student();
      const rider = await student();
      const id = await postedSeries(host!.uid, { weekdays: ["mon"], lastDepartureAt: { mon: lastIso }, startDate: "2026-10-12" });
      const call = createSendRequestHandler({ db, now: () => now, logger: silent })(req(rider, { seriesId: id, weekdays: ["mon"] }));
      if (ok) await call;
      else await rejects(call, "failed-precondition", (d) => assert.deepEqual(d.weekdays, [{ weekday: "mon", reason: "no-trips-left" }]));
    };
    // Last Monday departure Mon 2026-11-02 07:30 PST = 15:30Z (daylight time ended Sun 11-01).
    it("DST-end week: one minute before the last departure is allowed, one minute after is not", async () => {
      await check("2026-11-02T15:30:00Z", new Date("2026-11-02T15:29:00Z"), true);
      await check("2026-11-02T15:30:00Z", new Date("2026-11-02T15:31:00Z"), false);
    });
    // Spring forward Sun 2027-03-14; Mon 2027-03-08 is still PST, 07:30 = 15:30Z.
    it("spring-forward week", async () => {
      await check("2027-03-08T15:30:00Z", new Date("2027-03-08T15:29:00Z"), true);
      await check("2027-03-08T15:30:00Z", new Date("2027-03-08T15:31:00Z"), false);
    });
    it("a weekday with no last departure has no trips left", async () => {
      const host = await student();
      const rider = await student();
      const id = await postedSeries(host!.uid, { weekdays: ["mon"] });
      await db.doc(`series/${id}`).update({ lastDepartureAt: {} });
      await rejects(send(req(rider, { seriesId: id, weekdays: ["mon"] })), "failed-precondition", (d) =>
        assert.deepEqual(d.weekdays, [{ weekday: "mon", reason: "no-trips-left" }]));
    });
  });

  describe("conflicts (RC-32)", () => {
    it("rejects a weekday the rider is accepted on with overlapping dates", async () => {
      const host = await student();
      const rider = await student();
      const id = await postedSeries(host!.uid);
      await setSchedule(rider!.uid, { other_mon: entry("other", "mon", "accepted", "2026-11-16", "2026-12-04") });
      await rejects(send(req(rider, { seriesId: id, weekdays: ["mon", "wed"] })), "failed-precondition", (d) =>
        assert.deepEqual(d.weekdays, [{ weekday: "mon", reason: "conflict" }]));
      const s = await counts(rider!.uid, host!.uid, id);
      assert.equal(s.request, undefined);
      assert.equal(s.notifs, 0);
      // The other weekday alone is fine.
      await send(req(rider, { seriesId: id, weekdays: ["wed"] }));
    });

    it("rejects a weekday the rider hosts", async () => {
      const host = await student();
      const rider = await student();
      const id = await postedSeries(host!.uid);
      await setSchedule(rider!.uid, { mine_wed: entry("mine", "wed", "hosting", "2026-11-23", "2026-12-18") });
      await rejects(send(req(rider, { seriesId: id, weekdays: ["wed"] })), "failed-precondition", (d) =>
        assert.deepEqual(d.weekdays, [{ weekday: "wed", reason: "conflict" }]));
    });

    it("allows back-to-back dates (no shared ride date)", async () => {
      const host = await student();
      const rider = await student();
      const id = await postedSeries(host!.uid, { startDate: "2026-12-07" });
      await setSchedule(rider!.uid, { other_mon: entry("other", "mon", "accepted", "2026-11-16", "2026-12-06") });
      await send(req(rider, { seriesId: id, weekdays: ["mon"] }));
    });

    it("a pending weekday on another series does not block", async () => {
      const host = await student();
      const rider = await student();
      const id = await postedSeries(host!.uid);
      await setSchedule(rider!.uid, { other_mon: entry("other", "mon", "pending", "2026-11-16", "2026-12-18") });
      await send(req(rider, { seriesId: id, weekdays: ["mon"] }));
    });
  });

  it("a series that already started can be requested", async () => {
    const host = await student();
    const rider = await student();
    const id = await postedSeries(host!.uid, { startDate: "2026-11-02" });
    await send(req(rider, { seriesId: id, weekdays: ["mon"] }));
  });

  it("uses the student gate", async () => {
    await rejects(send(req(undefined, { seriesId: seriesIdOf(), weekdays: ["mon"] })), "unauthenticated");
    const uid = nextId("sr-noconsent-");
    await rejects(send(req({ uid, token: {} as never, rawToken: "x" }, { seriesId: seriesIdOf(), weekdays: ["mon"] })), "permission-denied");
  });
});
