import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc, setLogLevel } from "firebase/firestore";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent,
  hostStudent,
  institutionUser,
  rulesContext,
  studentWithoutConsent,
  unauthenticated,
} from "../helpers/personas.ts";
import { ADDRESS, seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const OWNER = authedStudent();
const OWNER_UID = OWNER.uid as string;

describe("privateDetails (RC-05): owner only", () => {
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
  });

  const ownerRef = () => doc(rulesContext(env, OWNER).firestore(), "privateDetails", OWNER_UID);
  const valid = () => ({ startingAddress: { ...ADDRESS }, updatedAt: serverTimestamp() });

  it("owner can read their address", async () => {
    await assertSucceeds(getDoc(ownerRef()));
  });

  it("owner can update with a valid address", async () => {
    await assertSucceeds(setDoc(ownerRef(), valid()));
  });

  it("owner can create a valid address when none exists", async () => {
    const p = hostStudent();
    const ref = doc(rulesContext(env, p).firestore(), "privateDetails", p.uid as string);
    await assertSucceeds(setDoc(ref, valid()));
  });

  it("accepts boundary coordinates", async () => {
    await assertSucceeds(setDoc(ownerRef(), { ...valid(), startingAddress: { ...ADDRESS, lat: 90, lng: -180 } }));
    await assertSucceeds(setDoc(ownerRef(), { ...valid(), startingAddress: { ...ADDRESS, lat: -90, lng: 180 } }));
  });

  for (const other of [hostStudent(), institutionUser(), studentWithoutConsent(), unauthenticated()]) {
    it(`${other.name} cannot read or write another student's address`, async () => {
      const ref = doc(rulesContext(env, other).firestore(), "privateDetails", OWNER_UID);
      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref, valid()));
      await assertFails(deleteDoc(ref));
    });
  }

  it("an institution account with a consent doc cannot read or write its own address", async () => {
    const p = institutionUser();
    const ref = doc(rulesContext(env, p).firestore(), "privateDetails", p.uid as string);
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, valid()));
  });

  it("an account without consent cannot read or write its own address", async () => {
    const p = studentWithoutConsent();
    const ref = doc(rulesContext(env, p).firestore(), "privateDetails", p.uid as string);
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, valid()));
  });

  const bad: Record<string, () => Record<string, unknown>> = {
    "extra top-level key": () => ({ ...valid(), note: "x" }),
    "missing updatedAt": () => ({ startingAddress: { ...ADDRESS } }),
    "missing startingAddress": () => ({ updatedAt: serverTimestamp() }),
    "wrong updatedAt": () => ({ ...valid(), updatedAt: new Date("2020-01-01T00:00:00Z") }),
    "extra address key": () => ({ ...valid(), startingAddress: { ...ADDRESS, unit: "4" } }),
    "missing placeId": () => ({ ...valid(), startingAddress: { text: ADDRESS.text, lat: 1, lng: 1 } }),
    "empty text": () => ({ ...valid(), startingAddress: { ...ADDRESS, text: "" } }),
    "empty placeId": () => ({ ...valid(), startingAddress: { ...ADDRESS, placeId: "" } }),
    "non-string text": () => ({ ...valid(), startingAddress: { ...ADDRESS, text: 5 } }),
    "lat above 90": () => ({ ...valid(), startingAddress: { ...ADDRESS, lat: 90.1 } }),
    "lat below -90": () => ({ ...valid(), startingAddress: { ...ADDRESS, lat: -90.1 } }),
    "lng above 180": () => ({ ...valid(), startingAddress: { ...ADDRESS, lng: 180.1 } }),
    "lng below -180": () => ({ ...valid(), startingAddress: { ...ADDRESS, lng: -180.1 } }),
    "string lat": () => ({ ...valid(), startingAddress: { ...ADDRESS, lat: "33" } }),
    "null lng": () => ({ ...valid(), startingAddress: { ...ADDRESS, lng: null } }),
    "address not a map": () => ({ ...valid(), startingAddress: "123 Test St" }),
  };
  for (const [name, make] of Object.entries(bad)) {
    it(`denies ${name}`, async () => {
      await assertFails(setDoc(ownerRef(), make()));
    });
  }

  it("allows a partial update that leaves a valid merged doc, and denies one that adds a key", async () => {
    await assertSucceeds(updateDoc(ownerRef(), { updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ownerRef(), { note: "x", updatedAt: serverTimestamp() }));
  });

  it("denies delete, even by the owner", async () => {
    await assertFails(deleteDoc(ownerRef()));
  });
});
