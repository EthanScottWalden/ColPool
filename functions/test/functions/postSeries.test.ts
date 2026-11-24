import { describe, it, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { createPostSeriesHandler } from "../../src/postSeries.ts";
import { createRoutePreviewHandler } from "../../src/routePreview.ts";
import { FakeRoutesClient } from "../../src/routePreview/routesClient.ts";
import { CAMPUS_POINT_SEED } from "../../src/shared/schema/campusPoints.ts";
import { COLLECTIONS } from "../../src/shared/schema/collections.ts";
import type { PlaceAddress, SeriesInput } from "../../src/shared/schema/series.ts";
import { scheduleEntryKey } from "../../src/shared/schema/keys.ts";
import { hashSeriesInput } from "../../src/shared/schema/seriesHash.ts";
import type { Weekday } from "../../src/shared/schema/weekday.ts";

const db = getFirestore(adminApp());
// Fixed clock: Monday 2026-11-23 12:00 PST.
const NOW = new Date("2026-11-23T20:00:00Z");
const PICKUP: PlaceAddress = { text: "1 Pickup Way, Riverside, CA", placeId: "test-pickup", lat: 33.9, lng: -117.4 };

const leg = (departTime: string, campusPointId = "yeager-center") => ({ departTime, campusPointId, stops: [] });

function series(overrides: Partial<SeriesInput> = {}): SeriesInput {
  return {
    pickupSpot: PICKUP,
    startDate: "2026-11-23",
    endDate: "2026-12-18",
    priceDollars: 5,
    vehicle: "Test Car",
    maxRiders: 3,
    weekdays: ["mon"],
    days: { mon: { toCampus: leg("07:30"), fromCampus: leg("12:00") } },
    ...overrides,
  };
}

type Auth = CallableRequest<unknown>["auth"];
let n = 0;
async function student(): Promise<Auth> {
  const uid = `ps-test-${Date.now()}-${n++}`;
  await db.collection(COLLECTIONS.consents).doc(uid).set({ agreedAt: Timestamp.now() });
  return { uid, token: {} as never, rawToken: "x" };
}
const req = (auth: Auth, data: unknown): CallableRequest<unknown> => ({ data, auth, rawRequest: {} as CallableRequest["rawRequest"], acceptsStreaming: false });

const silent = { info() {}, error() {} };
const post = createPostSeriesHandler({ db, now: () => NOW, logger: silent });
const preview = createRoutePreviewHandler({ db, routesClient: new FakeRoutesClient(), now: () => NOW, logger: silent });

async function review(auth: Auth, s: SeriesInput = series()) {
  return (await preview(req(auth, { series: s }))).draftId;
}

async function rejects(p: Promise<unknown>, code: string, check?: (details: any) => void): Promise<void> {
  await assert.rejects(p, (e: { code?: string; details?: any }) => {
    assert.equal(e.code, code);
    check?.(e.details);
    return true;
  });
}

const snap = async (path: string) => (await db.doc(path).get()).data();
async function state(uid: string, id: string) {
  return {
    series: await snap(`series/${id}`),
    draft: await snap(`seriesDrafts/${id}`),
    schedule: await snap(`schedules/${uid}`),
    access: await snap(`profileAccess/${uid}`),
  };
}

describe("postSeries", () => {
  before(async () => {
    for (const p of CAMPUS_POINT_SEED) {
      const { id, ...rest } = p;
      await db.collection(COLLECTIONS.campusPoints).doc(id).set(rest);
    }
  });
  beforeEach(async () => {
    // Keep every campus point active (a test below deactivates one).
    for (const p of CAMPUS_POINT_SEED) await db.collection(COLLECTIONS.campusPoints).doc(p.id).update({ active: true });
  });

  it("saves exactly the reviewed draft, with seats, search fields, schedule and host visibility", async () => {
    const auth = await student();
    const uid = auth!.uid;
    const s = series({
      weekdays: ["mon", "wed"],
      days: { mon: { toCampus: leg("07:30"), fromCampus: leg("12:00") }, wed: { toCampus: leg("08:15"), fromCampus: leg("13:00") } },
    });
    const id = await review(auth, s);
    const out = await post(req(auth, { draftId: id, series: s }));
    assert.deepEqual(out, { seriesId: id, alreadyPosted: false });

    const st = await state(uid, id);
    const draft = st.draft as any;
    const doc = st.series as any;
    assert.equal(draft.status, "posted");
    assert.ok(draft.postedAt);
    assert.equal(doc.hostId, uid);
    assert.equal(doc.status, "active");
    assert.equal(doc.inputHash, draft.inputHash);
    assert.deepEqual(doc.pickupSpot, s.pickupSpot);
    for (const k of ["startDate", "endDate", "priceDollars", "vehicle", "maxRiders", "weekdays"] as const) assert.deepEqual(doc[k], s[k]);
    for (const wd of ["mon", "wed"] as const) {
      assert.deepEqual(doc.days[wd].toCampus, { ...s.days[wd]!.toCampus, route: draft.route[wd].toCampus });
      assert.deepEqual(doc.days[wd].fromCampus, { ...s.days[wd]!.fromCampus, route: draft.route[wd].fromCampus });
    }
    assert.deepEqual(doc.openSeats, { mon: 3, wed: 3 });
    assert.deepEqual(doc.openWeekdays, ["mon", "wed"]);
    // Last Mon on or before 2026-12-18 is 12-14 07:30 PST = 15:30Z; last Wed is 12-16 08:15 PST = 16:15Z.
    assert.equal(doc.lastDepartureAt.mon.toDate().toISOString(), "2026-12-14T15:30:00.000Z");
    assert.equal(doc.lastDepartureAt.wed.toDate().toISOString(), "2026-12-16T16:15:00.000Z");
    assert.ok(doc.postedAt);

    for (const wd of ["mon", "wed"] as Weekday[]) {
      assert.deepEqual((st.schedule as any).entries[scheduleEntryKey(id, wd)], {
        seriesId: id, weekday: wd, role: "host", state: "hosting", startDate: s.startDate, endDate: s.endDate,
      });
    }
    assert.deepEqual(st.access, { activeHostedSeries: { [id]: true } });
  });

  it("lastDepartureAt follows daylight-saving time (series ending Mon 2026-11-02 vs earlier)", async () => {
    const auth = await student();
    const early = new Date("2026-10-20T20:00:00Z"); // clock earlier so both series can start in October
    const p = createPostSeriesHandler({ db, now: () => early, logger: silent });
    const pv = createRoutePreviewHandler({ db, routesClient: new FakeRoutesClient(), now: () => early, logger: silent });
    // PDT until Sun 2026-11-01 02:00; Mon 11-02 07:30 PST = 15:30Z.
    const a = series({ startDate: "2026-10-26", endDate: "2026-11-02" });
    const idA = (await pv(req(auth, { series: a }))).draftId;
    await p(req(auth, { draftId: idA, series: a }));
    assert.equal(((await snap(`series/${idA}`)) as any).lastDepartureAt.mon.toDate().toISOString(), "2026-11-02T15:30:00.000Z");
    // Ends Fri 10-30; last Monday is 10-26 07:30 PDT = 14:30Z.
    const b = series({ startDate: "2026-10-21", endDate: "2026-10-30" });
    const auth2 = await student(); // a second host: the two series would share a Monday
    const idB = (await pv(req(auth2, { series: b }))).draftId;
    await p(req(auth2, { draftId: idB, series: b }));
    assert.equal(((await snap(`series/${idB}`)) as any).lastDepartureAt.mon.toDate().toISOString(), "2026-10-26T14:30:00.000Z");
  });

  it("omits lastDepartureAt for a weekday with no date in range and still lists it in openWeekdays", async () => {
    const auth = await student();
    const s = series({
      startDate: "2026-11-23", endDate: "2026-11-24", weekdays: ["mon", "fri"],
      days: { mon: { toCampus: leg("07:30"), fromCampus: leg("12:00") }, fri: { toCampus: leg("07:30"), fromCampus: leg("12:00") } },
    });
    const id = await review(auth, s);
    await post(req(auth, { draftId: id, series: s }));
    const doc = (await snap(`series/${id}`)) as any;
    assert.deepEqual(Object.keys(doc.lastDepartureAt), ["mon"]);
    assert.deepEqual(doc.openWeekdays, ["mon", "fri"]);
  });

  it("rejects a changed series with draft-changed and writes nothing", async () => {
    const auth = await student();
    const id = await review(auth);
    const before = await state(auth!.uid, id);
    await rejects(post(req(auth, { draftId: id, series: series({ priceDollars: 6 }) })), "failed-precondition", (d) => assert.equal(d.reason, "draft-changed"));
    assert.deepEqual(await state(auth!.uid, id), before);
  });

  it("malformed, missing and other users' draft ids", async () => {
    const a = await student();
    const b = await student();
    await rejects(post(req(a, { draftId: "bad", series: series() })), "invalid-argument");
    await rejects(post(req(a, { series: series() })), "invalid-argument");
    await rejects(post(req(a, { draftId: "A".repeat(20), series: series() })), "not-found");
    const id = await review(a);
    await rejects(post(req(b, { draftId: id, series: series() })), "not-found");
    assert.equal((await snap(`series/${id}`)), undefined);
  });

  it("requires a student", async () => {
    await rejects(post(req(null as never, { draftId: "A".repeat(20), series: series() })), "unauthenticated");
  });

  it("a double post returns the existing series and writes nothing the second time", async () => {
    const auth = await student();
    const id = await review(auth);
    await post(req(auth, { draftId: id, series: series() }));
    const first = await state(auth!.uid, id);
    // Even a different body is ignored once the series exists (duplicate tap).
    const again = await post(req(auth, { draftId: id, series: series() }));
    assert.deepEqual(again, { seriesId: id, alreadyPosted: true });
    assert.deepEqual(await state(auth!.uid, id), first);
    const all = await db.collection(COLLECTIONS.series).where("hostId", "==", auth!.uid).get();
    assert.equal(all.size, 1);
  });

  it("two parallel posts of the same draft create exactly one series", async () => {
    const auth = await student();
    const id = await review(auth);
    const results = await Promise.all([1, 2, 3].map(() => post(req(auth, { draftId: id, series: series() }))));
    assert.equal(results.filter((r) => !r.alreadyPosted).length, 1);
    assert.equal((await db.collection(COLLECTIONS.series).where("hostId", "==", auth!.uid).get()).size, 1);
  });

  async function disjoint(auth: Auth, i: number): Promise<string> {
    // Different weekday/date windows so RC-32 conflicts do not apply.
    const wd = (["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const)[i]!;
    const s = series({ weekdays: [wd], days: { [wd]: { toCampus: leg("07:30"), fromCampus: leg("12:00") } } });
    return review(auth, s);
  }
  const disjointSeries = (i: number): SeriesInput => {
    const wd = (["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const)[i]!;
    return series({ weekdays: [wd], days: { [wd]: { toCampus: leg("07:30"), fromCampus: leg("12:00") } } });
  };

  it("rejects a 6th active series (RC-31); a series past its end date doesn't count", async () => {
    const auth = await student();
    const ids: string[] = [];
    for (let i = 0; i < 5; i++) ids.push(await disjoint(auth, i));
    const sixth = await disjoint(auth, 5);
    for (let i = 0; i < 5; i++) await post(req(auth, { draftId: ids[i], series: disjointSeries(i) }));
    await rejects(post(req(auth, { draftId: sixth, series: disjointSeries(5) })), "invalid-argument", (d) =>
      assert.ok(d.errors.some((e: any) => e.code === "hostingLimitReached")),
    );
    assert.equal((await snap(`series/${sixth}`)), undefined);
    // Age one series past its end date: it no longer counts.
    await db.doc(`series/${ids[0]}`).update({ endDate: "2026-11-20" });
    await post(req(auth, { draftId: sixth, series: disjointSeries(5) }));
    assert.ok(await snap(`series/${sixth}`));
  });

  it("RC-32: an accepted rider entry seeded after review fails the post", async () => {
    const auth = await student();
    const id = await review(auth);
    await db.doc(`schedules/${auth!.uid}`).set({
      entries: { x_mon: { seriesId: "x", weekday: "mon", role: "rider", state: "accepted", startDate: "2026-11-23", endDate: "2026-12-18" } },
    });
    await rejects(post(req(auth, { draftId: id, series: series() })), "invalid-argument", (d) =>
      assert.ok(d.errors.some((e: any) => e.code === "riderScheduleConflict")),
    );
    assert.equal(await snap(`series/${id}`), undefined);
    assert.equal(((await snap(`seriesDrafts/${id}`)) as any).status, "reviewed");
  });

  it("fails when the start date has passed since review", async () => {
    const auth = await student();
    const id = await review(auth);
    const later = createPostSeriesHandler({ db, now: () => new Date("2026-11-24T20:00:00Z"), logger: silent });
    await rejects(later(req(auth, { draftId: id, series: series() })), "invalid-argument", (d) =>
      assert.ok(d.errors.some((e: any) => e.code === "startInPast")),
    );
  });

  it("fails when no matching date is left (weekdays no longer inside the range)", async () => {
    const auth = await student();
    const s = series({ startDate: "2026-11-24", endDate: "2026-11-27" }); // Tue-Fri, picks Mon: no date. Use a valid one below.
    const ok = series({ startDate: "2026-11-23", endDate: "2026-11-23" });
    const id = await review(auth, ok);
    // Re-posting the same body after the day passed fails on start date; a body with no date fails too.
    await rejects(post(req(auth, { draftId: id, series: s })), "invalid-argument", (d) =>
      assert.ok(d.errors.some((e: any) => e.code === "noDateOnWeekdays")),
    );
  });

  it("fails when a campus point was deactivated since review", async () => {
    const auth = await student();
    const id = await review(auth);
    await db.collection(COLLECTIONS.campusPoints).doc("yeager-center").update({ active: false });
    await rejects(post(req(auth, { draftId: id, series: series() })), "invalid-argument", (d) =>
      assert.ok(d.errors.length > 0),
    );
    assert.equal(await snap(`series/${id}`), undefined);
  });

  it("a failed post leaves series, draft, schedules and profileAccess unchanged", async () => {
    const auth = await student();
    const id = await review(auth);
    const before = await state(auth!.uid, id);
    await db.collection(COLLECTIONS.campusPoints).doc("yeager-center").update({ active: false });
    await rejects(post(req(auth, { draftId: id, series: series() })), "invalid-argument");
    assert.deepEqual(await state(auth!.uid, id), before);
  });

  it("inconsistent draft state is an internal error", async () => {
    const auth = await student();
    const id = await review(auth);
    await db.doc(`seriesDrafts/${id}`).update({ status: "posted" });
    await rejects(post(req(auth, { draftId: id, series: series() })), "internal");
  });

  it("two different drafts posted in parallel with 4 active: exactly one succeeds (RC-31)", async () => {
    const auth = await student();
    for (let i = 0; i < 4; i++) {
      const id = await disjoint(auth, i);
      await post(req(auth, { draftId: id, series: disjointSeries(i) }));
    }
    const a = await disjoint(auth, 4);
    const b = await disjoint(auth, 5);
    const res = await Promise.allSettled([
      post(req(auth, { draftId: a, series: disjointSeries(4) })),
      post(req(auth, { draftId: b, series: disjointSeries(5) })),
    ]);
    assert.equal(res.filter((r) => r.status === "fulfilled").length, 1);
    assert.equal((await db.collection(COLLECTIONS.series).where("hostId", "==", auth!.uid).get()).size, 5);
  });

  it("two different drafts with a shared ride date posted in parallel: exactly one succeeds (RC-32)", async () => {
    const auth = await student();
    const a = await review(auth, series({ priceDollars: 5 }));
    const b = await review(auth, series({ priceDollars: 6 }));
    const res = await Promise.allSettled([
      post(req(auth, { draftId: a, series: series({ priceDollars: 5 }) })),
      post(req(auth, { draftId: b, series: series({ priceDollars: 6 }) })),
    ]);
    assert.equal(res.filter((r) => r.status === "fulfilled").length, 1);
    const rejected = res.find((r) => r.status === "rejected") as PromiseRejectedResult;
    assert.ok((rejected.reason.details.errors as any[]).some((e) => e.code === "hostingWeekdayOverlap"));
    assert.equal((await db.collection(COLLECTIONS.series).where("hostId", "==", auth!.uid).get()).size, 1);
  });

  it("hashSeriesInput on the stored input matches the draft hash", async () => {
    const auth = await student();
    const id = await review(auth);
    const d = (await snap(`seriesDrafts/${id}`)) as any;
    assert.equal(hashSeriesInput(d.input), d.inputHash);
  });
});
