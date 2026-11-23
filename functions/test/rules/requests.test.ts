import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, setLogLevel, updateDoc, where } from "firebase/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { createSendRequestHandler } from "../../src/sendRequest.ts";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent, hostStudent, institutionUser, requesterStudent, rulesContext, studentWithoutConsent, unrelatedStudent, unauthenticated,
} from "../helpers/personas.ts";
import { seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const SID = "AbCdEfGhIjKlMnOpQrSt";
const HOST = hostStudent();
const RIDER = requesterStudent();
const rid = `${SID}_${RIDER.uid}`;

describe("requests (rules)", () => {
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
    // Created by the real function so the stored shape is what rules see.
    const db = getFirestore(adminApp());
    await db.doc(`series/${SID}`).set({
      hostId: HOST.uid, status: "active", pickupSpot: { text: "1 Pickup Way", placeId: "p", lat: 1, lng: 2 },
      startDate: "2026-11-16", endDate: "2026-12-18", weekdays: ["mon"], openSeats: { mon: 2 }, openWeekdays: ["mon"],
      lastDepartureAt: { mon: new Date("2026-12-14T15:30:00Z") },
    });
    const auth = { uid: RIDER.uid as string, token: {} as never, rawToken: "x" };
    const call = createSendRequestHandler({ db, now: () => new Date("2026-11-23T20:00:00Z"), logger: { info() {}, error() {} } });
    await call({ data: { seriesId: SID, weekdays: ["mon"] }, auth, rawRequest: {} as never, acceptsStreaming: false } as CallableRequest<unknown>);
  });

  const fs = (who: ReturnType<typeof hostStudent>) => rulesContext(env, who).firestore();

  it("allows the rider and the host to get it", async () => {
    await assertSucceeds(getDoc(doc(fs(RIDER), "requests", rid)));
    await assertSucceeds(getDoc(doc(fs(HOST), "requests", rid)));
  });

  it("allows list filtered by riderId == me, and by hostId == me and seriesId", async () => {
    await assertSucceeds(getDocs(query(collection(fs(RIDER), "requests"), where("riderId", "==", RIDER.uid))));
    await assertSucceeds(getDocs(query(collection(fs(HOST), "requests"), where("hostId", "==", HOST.uid), where("seriesId", "==", SID))));
  });

  it("denies an unrelated student, and unfiltered or foreign-filtered lists", async () => {
    const other = unrelatedStudent();
    await assertFails(getDoc(doc(fs(other), "requests", rid)));
    await assertFails(getDocs(query(collection(fs(other), "requests"), where("riderId", "==", RIDER.uid))));
    await assertFails(getDocs(query(collection(fs(other), "requests"), where("hostId", "==", HOST.uid))));
    await assertFails(getDocs(collection(fs(RIDER), "requests")));
    await assertFails(getDocs(collection(fs(HOST), "requests")));
    await assertFails(getDocs(query(collection(fs(RIDER), "requests"), where("riderId", "==", HOST.uid))));
  });

  it("denies institution, unconsented and signed-out readers", async () => {
    await assertFails(getDoc(doc(fs(institutionUser()), "requests", rid)));
    await assertFails(getDoc(doc(fs(studentWithoutConsent()), "requests", rid)));
    await assertFails(getDoc(doc(fs(unauthenticated()), "requests", rid)));
  });

  it("denies every client create, update and delete, rider and host included", async () => {
    for (const who of [RIDER, HOST, unrelatedStudent()]) {
      await assertFails(updateDoc(doc(fs(who), "requests", rid), { days: {} }));
      await assertFails(deleteDoc(doc(fs(who), "requests", rid)));
      await assertFails(setDoc(doc(fs(who), "requests", `${SID}_new${who.uid}`), { seriesId: SID, riderId: who.uid, hostId: HOST.uid }));
    }
  });

  it("after the function runs, the host can read the requester's profile and an unrelated student cannot", async () => {
    await assertSucceeds(getDoc(doc(fs(HOST), "profiles", RIDER.uid as string)));
    await assertSucceeds(getDoc(doc(fs(RIDER), "profiles", HOST.uid as string)));
    await assertFails(getDoc(doc(fs(unrelatedStudent()), "profiles", RIDER.uid as string)));
  });
});
