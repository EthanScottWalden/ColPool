import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  setLogLevel,
  updateDoc,
  where,
} from "firebase/firestore";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent,
  hostStudent,
  institutionUser,
  rulesContext,
  studentWithoutConsent,
  unauthenticated,
  unrelatedStudent,
  type Persona,
} from "../helpers/personas.ts";
import { seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const HOST = hostStudent();
const route = { legs: [{ meters: 1000, durationSec: 120 }], totalMeters: 1000, totalDurationSec: 120, times: [{ departMin: 450 }, { arriveMin: 452 }] };
const place = { text: "1 Test Way, Riverside, CA", placeId: "test-place-1", lat: 33.93, lng: -117.4 };
const leg = { departTime: "07:30", campusPointId: "cp1", stops: [], route };

const SERIES = {
  hostId: HOST.uid,
  status: "active",
  pickupSpot: place,
  startDate: "2026-03-02",
  endDate: "2026-03-13",
  priceDollars: 5,
  vehicle: "Test Car",
  maxRiders: 3,
  weekdays: ["mon", "fri"],
  days: { mon: { toCampus: leg, fromCampus: leg }, fri: { toCampus: leg, fromCampus: leg } },
  openSeats: { mon: 3, fri: 3 },
  openWeekdays: ["mon", "fri"],
  lastDepartureAt: { mon: new Date("2026-03-09T14:30:00Z"), fri: new Date("2026-03-13T14:30:00Z") },
  inputHash: "0".repeat(64),
  postedAt: new Date("2026-02-20T00:00:00Z"),
};

const DRAFT = {
  hostId: HOST.uid,
  input: { startDate: "2026-03-02" },
  inputHash: "0".repeat(64),
  route: { mon: { toCampus: route, fromCampus: route } },
  reviewedAt: new Date("2026-02-19T00:00:00Z"),
  createdAt: new Date("2026-02-19T00:00:00Z"),
  updatedAt: new Date("2026-02-19T00:00:00Z"),
  status: "reviewed",
};

describe("series and seriesDrafts rules (#20)", () => {
  let env: RulesTestEnvironment;
  before(async () => {
    env = await createRulesEnv();
  });
  after(async () => {
    await env.cleanup();
  });
  beforeEach(async () => {
    await env.clearFirestore();
    await seedBase(env, [institutionUser()]);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "series", "s1"), SERIES);
      await setDoc(doc(ctx.firestore(), "seriesDrafts", "s1"), DRAFT);
    });
  });

  const dbOf = (p: Persona) => rulesContext(env, p).firestore();

  describe("series reads", () => {
    it("any consented student can get a series", async () => {
      await assertSucceeds(getDoc(doc(dbOf(authedStudent()), "series", "s1")));
      await assertSucceeds(getDoc(doc(dbOf(unrelatedStudent()), "series", "s1")));
    });
    it("a student can run the search query", async () => {
      const q = query(
        collection(dbOf(authedStudent()), "series"),
        where("status", "==", "active"),
        where("openWeekdays", "array-contains-any", ["mon", "tue"]),
      );
      const snap = await assertSucceeds(getDocs(q));
      if (snap.size !== 1) throw new Error(`expected 1 result, got ${snap.size}`);
    });
    it("the host can get their own series", async () => {
      await assertSucceeds(getDoc(doc(dbOf(HOST), "series", "s1")));
    });
    for (const p of [institutionUser(), studentWithoutConsent(), unauthenticated()]) {
      it(`${p.name} cannot get or list series`, async () => {
        await assertFails(getDoc(doc(dbOf(p), "series", "s1")));
        await assertFails(getDocs(query(collection(dbOf(p), "series"), where("status", "==", "active"))));
        await assertFails(
          getDocs(query(collection(dbOf(p), "series"), where("status", "==", "active"), where("openWeekdays", "array-contains-any", ["mon"]))),
        );
        await assertFails(getDocs(collection(dbOf(p), "series")));
      });
    }
  });

  describe("series client writes are denied, host included", () => {
    it("a student cannot create a series", async () => {
      await assertFails(setDoc(doc(dbOf(authedStudent()), "series", "s2"), SERIES));
      await assertFails(setDoc(doc(dbOf(HOST), "series", "s2"), SERIES));
    });
    it("the host cannot update openSeats, openWeekdays, status or days", async () => {
      const ref = doc(dbOf(HOST), "series", "s1");
      await assertFails(updateDoc(ref, { "openSeats.mon": 0 }));
      await assertFails(updateDoc(ref, { openWeekdays: ["fri"] }));
      await assertFails(updateDoc(ref, { status: "cancelled" }));
      await assertFails(updateDoc(ref, { "days.mon.toCampus.route": route }));
      await assertFails(updateDoc(ref, { "days.mon.toCampus.route.totalMeters": 1 }));
    });
    it("another student cannot update a series", async () => {
      await assertFails(updateDoc(doc(dbOf(authedStudent()), "series", "s1"), { status: "cancelled" }));
    });
    it("set with merge is denied", async () => {
      await assertFails(setDoc(doc(dbOf(HOST), "series", "s1"), { status: "cancelled" }, { merge: true }));
    });
    it("delete is denied", async () => {
      await assertFails(deleteDoc(doc(dbOf(HOST), "series", "s1")));
    });
    it("the doc is unchanged after the denied writes", async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        const snap = await getDoc(doc(ctx.firestore(), "series", "s1"));
        if (snap.get("status") !== "active") throw new Error("status changed");
      });
    });
  });

  describe("seriesDrafts", () => {
    it("the host can get their own draft", async () => {
      await assertSucceeds(getDoc(doc(dbOf(HOST), "seriesDrafts", "s1")));
    });
    for (const p of [unrelatedStudent(), authedStudent(), institutionUser(), studentWithoutConsent(), unauthenticated()]) {
      it(`${p.name} cannot get someone else's draft`, async () => {
        await assertFails(getDoc(doc(dbOf(p), "seriesDrafts", "s1")));
      });
    }
    it("an owner without consent is denied", async () => {
      // A draft owned by the no-consent account: the account is denied despite owning it.
      const p = studentWithoutConsent();
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), "seriesDrafts", "d-noconsent"), { ...DRAFT, hostId: p.uid });
      });
      await assertFails(getDoc(doc(dbOf(p), "seriesDrafts", "d-noconsent")));
    });
    it("an institution account that owns a draft is denied", async () => {
      const p = institutionUser();
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), "seriesDrafts", "d-inst"), { ...DRAFT, hostId: p.uid });
      });
      await assertFails(getDoc(doc(dbOf(p), "seriesDrafts", "d-inst")));
    });
    it("nobody can list drafts, not even the host filtered on hostId", async () => {
      await assertFails(getDocs(query(collection(dbOf(HOST), "seriesDrafts"), where("hostId", "==", HOST.uid))));
      await assertFails(getDocs(collection(dbOf(HOST), "seriesDrafts")));
      await assertFails(getDocs(collection(dbOf(authedStudent()), "seriesDrafts")));
      await assertFails(getDocs(collection(dbOf(institutionUser()), "seriesDrafts")));
    });
    it("the host cannot create a draft", async () => {
      await assertFails(setDoc(doc(dbOf(HOST), "seriesDrafts", "d-new"), DRAFT));
    });
    it("the host cannot update route, inputHash or input", async () => {
      const ref = doc(dbOf(HOST), "seriesDrafts", "s1");
      await assertFails(updateDoc(ref, { route: {} }));
      await assertFails(updateDoc(ref, { inputHash: "f".repeat(64) }));
      await assertFails(updateDoc(ref, { input: { startDate: "2026-04-01" } }));
      await assertFails(setDoc(ref, { status: "posted" }, { merge: true }));
    });
    it("the host cannot delete a draft", async () => {
      await assertFails(deleteDoc(doc(dbOf(HOST), "seriesDrafts", "s1")));
    });
  });

  it("series subcollections stay default deny", async () => {
    await assertFails(getDoc(doc(dbOf(HOST), "series", "s1", "requests", "r1")));
    await assertFails(getDoc(doc(dbOf(HOST), "seriesDrafts", "s1", "x", "y")));
  });
});
