import { describe, it, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import {
  buildNotificationText,
  formatDayList,
  notificationId,
  writeNotification,
  NOTIFICATION_TYPES,
  type NotificationEvent,
  type SeriesDisplay,
} from "../../src/shared/notifications/index.ts";

const db = getFirestore(adminApp());
const SERIES_ID = "AbCdEfGhIjKlMnOpQrSt";
const RIDER_UID = "riderUid0123456789abcdef";
const series: SeriesDisplay = { hostName: { firstName: "Hana", lastName: "Host" }, pickupSpotName: "Test Lot" };
const rider = { firstName: "Rory", lastName: "Rider" };
const base = { seriesId: SERIES_ID, series };

const ALL_EVENTS: NotificationEvent[] = [
  { ...base, type: "request_new", riderUid: RIDER_UID, rider, weekdays: ["tue", "thu"] },
  { ...base, type: "day_accepted", weekday: "tue" },
  { ...base, type: "day_declined", weekday: "wed" },
  { ...base, type: "day_auto_declined_full", weekday: "thu" },
  { ...base, type: "day_auto_declined_conflict", weekday: "fri" },
  { ...base, type: "rider_removed", weekday: "mon" },
  { ...base, type: "rider_left", riderUid: RIDER_UID, rider, weekday: "tue" },
  { ...base, type: "request_withdrawn", riderUid: RIDER_UID, rider, weekday: "tue" },
  { ...base, type: "series_cancelled" },
  { ...base, type: "series_ended", audience: "host", endDate: "2026-10-09" },
  { ...base, type: "series_ended", audience: "rider", endDate: "2026-10-09" },
];

describe("notification text", () => {
  it("covers every type", () => {
    assert.deepEqual([...new Set(ALL_EVENTS.map((e) => e.type))].sort(), [...NOTIFICATION_TYPES].sort());
  });

  it("renders exact text per event", () => {
    const line = "Hana's series from Test Lot";
    const t = (i: number) => buildNotificationText(ALL_EVENTS[i] as NotificationEvent);
    assert.deepEqual(t(0), { title: "New ride request", body: "Rory Rider wants to join your series on Tuesday and Thursday.", seriesLine: line });
    assert.deepEqual(t(1), { title: "Day accepted", body: "Hana Host accepted your request for Tuesday.", seriesLine: line });
    assert.deepEqual(t(2), { title: "Day declined", body: "Hana Host declined your request for Wednesday.", seriesLine: line });
    assert.deepEqual(t(3), {
      title: "Day declined",
      body: "Thursday in Hana Host's series filled up, so that day of your request was declined automatically.",
      seriesLine: line,
    });
    assert.deepEqual(t(4), {
      title: "Day declined",
      body: "Friday in Hana Host's series was declined automatically because you were accepted on Friday in another series.",
      seriesLine: line,
    });
    assert.deepEqual(t(5), { title: "Removed from Monday", body: "Hana Host removed you from their series on Monday.", seriesLine: line });
    assert.deepEqual(t(6), { title: "Rider left", body: "Rory Rider left your series on Tuesday. A seat opened up.", seriesLine: line });
    assert.deepEqual(t(7), { title: "Request withdrawn", body: "Rory Rider withdrew their request for Tuesday.", seriesLine: line });
    assert.deepEqual(t(8), { title: "Series cancelled", body: "Hana Host cancelled a series you requested to join.", seriesLine: line });
    assert.deepEqual(t(9), { title: "Series ended", body: "Your series reached its end date on Oct 9.", seriesLine: line });
    assert.deepEqual(t(10), { title: "Series ended", body: "Hana Host's series reached its end date on Oct 9.", seriesLine: line });
  });

  it("lists days with commas and 'and', Monday first", () => {
    assert.equal(formatDayList(["tue"]), "Tuesday");
    assert.equal(formatDayList(["thu", "tue"]), "Tuesday and Thursday");
    assert.equal(formatDayList(["thu", "mon", "tue"]), "Monday, Tuesday and Thursday");
  });

  it("formats end dates in daylight-saving weeks without time zone math", () => {
    const end = (endDate: string) => buildNotificationText({ ...base, type: "series_ended", audience: "host", endDate }).body;
    assert.equal(end("2026-11-01"), "Your series reached its end date on Nov 1.");
    assert.equal(end("2027-03-14"), "Your series reached its end date on Mar 14.");
    assert.equal(end("2028-02-29"), "Your series reached its end date on Feb 29.");
    assert.throws(() => end("2027-02-29"));
    assert.throws(() => end("2026-1-01"));
  });

  it("rejects invalid input", () => {
    assert.throws(() => buildNotificationText({ ...base, type: "day_accepted", weekday: "xyz" as never }));
    assert.throws(() => buildNotificationText({ ...base, series: { ...series, pickupSpotName: "  " }, type: "series_cancelled" }));
    assert.throws(() => buildNotificationText({ ...base, type: "request_new", riderUid: RIDER_UID, rider, weekdays: [] }));
    assert.throws(() => buildNotificationText({ ...base, type: "series_ended", audience: "other" as never, endDate: "2026-10-09" }));
  });

  it("never leaks private sentinels", () => {
    const secrets = ["123 Secret Street", "secret@example.edu", "startingAddress", "@"];
    for (const e of ALL_EVENTS) {
      const t = buildNotificationText(e);
      const all = `${t.title} ${t.body} ${t.seriesLine}`;
      for (const s of secrets) assert.ok(!all.includes(s), `${e.type} leaked ${s}`);
    }
    // Extra fields on an event are never copied into the text.
    const sneaky = { ...ALL_EVENTS[1], startingAddress: "123 Secret Street", email: "secret@example.edu" } as unknown as NotificationEvent;
    const t = buildNotificationText(sneaky);
    assert.ok(!JSON.stringify(t).includes("Secret"));
  });
});

describe("notification ids", () => {
  it("are deterministic and shaped per type", () => {
    const ids = ALL_EVENTS.map(notificationId);
    assert.deepEqual(ids, [
      `request_new_${SERIES_ID}_${RIDER_UID}`,
      `day_accepted_${SERIES_ID}_tue`,
      `day_declined_${SERIES_ID}_wed`,
      `day_auto_declined_full_${SERIES_ID}_thu`,
      `day_auto_declined_conflict_${SERIES_ID}_fri`,
      `rider_removed_${SERIES_ID}_mon`,
      `rider_left_${SERIES_ID}_${RIDER_UID}_tue`,
      `request_withdrawn_${SERIES_ID}_${RIDER_UID}_tue`,
      `series_cancelled_${SERIES_ID}`,
      `series_ended_${SERIES_ID}`,
      `series_ended_${SERIES_ID}`,
    ]);
    assert.deepEqual(ALL_EVENTS.map(notificationId), ids);
  });

  it("reject unsafe ids", () => {
    assert.throws(() => notificationId({ ...base, seriesId: "a/b", type: "series_cancelled" }));
    assert.throws(() => notificationId({ ...base, seriesId: "", type: "series_cancelled" }));
  });
});

describe("writeNotification (emulator)", () => {
  let n = 0;
  let recipient = "";
  const col = () => db.collection("users").doc(recipient).collection("notifications");
  before(() => {
    assert.ok(process.env["FIRESTORE_EMULATOR_HOST"], "needs the Firestore emulator");
  });
  beforeEach(() => {
    recipient = `notif-test-${Date.now()}-${n++}`;
  });

  it("writes text, server createdAt, read=false and weekdays; omits optional fields", async () => {
    const before = Date.now();
    const accepted = ALL_EVENTS[1] as NotificationEvent;
    const batch = db.batch();
    const id = writeNotification(batch, db, recipient, accepted);
    await batch.commit();
    const data = (await col().doc(id).get()).data() as Record<string, unknown>;
    assert.equal(data["read"], false);
    assert.equal(data["type"], "day_accepted");
    assert.equal(data["seriesId"], SERIES_ID);
    assert.deepEqual(data["weekdays"], ["tue"]);
    assert.equal(data["title"], "Day accepted");
    const createdAt = (data["createdAt"] as Timestamp).toMillis();
    assert.ok(createdAt >= before - 5000 && createdAt <= Date.now() + 5000);
    assert.deepEqual(Object.keys(data).sort(), ["body", "createdAt", "read", "seriesId", "seriesLine", "title", "type", "weekdays"]);

    const cancelled = ALL_EVENTS[8] as NotificationEvent;
    const b2 = db.batch();
    const id2 = writeNotification(b2, db, recipient, cancelled);
    await b2.commit();
    const d2 = (await col().doc(id2).get()).data() as Record<string, unknown>;
    assert.ok(!("weekdays" in d2));
    assert.ok(Object.values(d2).every((v) => v !== null));
  });

  it("stores several weekdays Monday first for a new request", async () => {
    const e: NotificationEvent = { ...base, type: "request_new", riderUid: RIDER_UID, rider, weekdays: ["thu", "mon"] };
    const batch = db.batch();
    const id = writeNotification(batch, db, recipient, e);
    await batch.commit();
    assert.deepEqual((await col().doc(id).get()).get("weekdays"), ["mon", "thu"]);
  });

  it("works inside a transaction and rejects a duplicate event", async () => {
    const e = ALL_EVENTS[2] as NotificationEvent;
    await db.runTransaction(async (tx) => {
      writeNotification(tx, db, recipient, e);
    });
    assert.equal((await col().get()).size, 1);
    await assert.rejects(
      db.runTransaction(async (tx) => {
        writeNotification(tx, db, recipient, e);
      }),
      (err: { code?: number }) => err.code === 6,
    );
    const batch = db.batch();
    writeNotification(batch, db, recipient, e);
    await assert.rejects(batch.commit(), (err: { code?: number }) => err.code === 6);
    assert.equal((await col().get()).size, 1);
  });

  it("leaves nothing when the transaction throws after the write", async () => {
    await assert.rejects(
      db.runTransaction(async (tx) => {
        writeNotification(tx, db, recipient, ALL_EVENTS[1] as NotificationEvent);
        throw new Error("boom");
      }),
      /boom/,
    );
    assert.equal((await col().get()).size, 0);
  });

  it("writes nothing for invalid input", async () => {
    const batch = db.batch();
    assert.throws(() => writeNotification(batch, db, recipient, { ...base, type: "day_accepted", weekday: "xyz" as never }));
    assert.throws(() => writeNotification(batch, db, "bad/uid", ALL_EVENTS[1] as NotificationEvent));
    assert.throws(() => writeNotification(batch, db, recipient, { ...base, series: { ...series, pickupSpotName: "" }, type: "series_cancelled" }));
    await batch.commit();
    assert.equal((await col().get()).size, 0);
  });
});

describe("notifications module source", () => {
  it("never mentions private details, starting addresses or email", () => {
    const dir = new URL("../../src/shared/notifications/", import.meta.url);
    for (const f of readdirSync(dir)) {
      const src = readFileSync(new URL(f, dir), "utf8");
      for (const word of [/privateDetails/i, /startingAddress/i, /e-?mail/i]) {
        assert.ok(!word.test(src), `${f} mentions ${String(word)}`);
      }
    }
  });
});
