import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc, setLogLevel } from "firebase/firestore";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent,
  consentedStudents,
  institutionUser,
  rulesContext,
  studentWithoutConsent,
  unauthenticated,
  unrelatedStudent,
} from "../helpers/personas.ts";
import { seedAccess, seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const OWNER = authedStudent();
const UID = OWNER.uid as string;

describe("owner reads and no client writes", () => {
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
    await seedAccess(env, UID, { activeHostedSeries: { s1: true }, viewers: {} });
  });

  const db = () => rulesContext(env, OWNER).firestore();

  for (const col of ["users", "schedules"]) {
    it(`owner can read their own ${col} doc`, async () => {
      await assertSucceeds(getDoc(doc(db(), col, UID)));
    });
    it(`another student cannot read ${col}/{uid}`, async () => {
      await assertFails(getDoc(doc(rulesContext(env, unrelatedStudent()).firestore(), col, UID)));
    });
    it(`denies listing ${col}`, async () => {
      await assertFails(getDocs(collection(db(), col)));
    });
  }

  for (const col of ["consents", "profileAccess", "schedules", "users", "series", "seriesDrafts"]) {
    it(`owner cannot write or delete ${col}/{uid}`, async () => {
      const ref = doc(db(), col, UID);
      await assertFails(setDoc(ref, { x: 1 }));
      await assertFails(updateDoc(ref, { x: 1 }));
      await assertFails(deleteDoc(ref));
    });
    it(`no other account can write ${col}`, async () => {
      for (const p of [unrelatedStudent(), institutionUser(), studentWithoutConsent(), unauthenticated()]) {
        const ref = doc(rulesContext(env, p).firestore(), col, p.uid ?? UID);
        await assertFails(setDoc(ref, { x: 1 }));
      }
    });
  }

  for (const col of ["consents", "profileAccess"]) {
    it(`no client can read ${col}, not even the owner`, async () => {
      for (const p of [...consentedStudents(), institutionUser(), studentWithoutConsent(), unauthenticated()]) {
        const c = rulesContext(env, p).firestore();
        await assertFails(getDoc(doc(c, col, UID)));
        await assertFails(getDoc(doc(c, col, p.uid ?? UID)));
        await assertFails(getDocs(collection(c, col)));
      }
    });
  }

  it("consented students can read campusPoints and options/profile", async () => {
    for (const p of consentedStudents()) {
      const c = rulesContext(env, p).firestore();
      await assertSucceeds(getDoc(doc(c, "campusPoints", "cp1")));
      await assertSucceeds(getDocs(collection(c, "campusPoints")));
      await assertSucceeds(getDoc(doc(c, "options", "profile")));
    }
  });

  it("consented students cannot write campusPoints or options", async () => {
    await assertFails(setDoc(doc(db(), "campusPoints", "cp1"), { name: "x" }));
    await assertFails(updateDoc(doc(db(), "campusPoints", "cp1"), { name: "x" }));
    await assertFails(setDoc(doc(db(), "campusPoints", "cp2"), { name: "x" }));
    await assertFails(deleteDoc(doc(db(), "campusPoints", "cp1")));
    await assertFails(setDoc(doc(db(), "options", "profile"), { genderIds: [] }));
    await assertFails(deleteDoc(doc(db(), "options", "profile")));
  });

  it("no other options document is readable", async () => {
    await assertFails(getDoc(doc(db(), "options", "other")));
  });

  for (const p of [studentWithoutConsent(), unauthenticated()]) {
    it(`${p.name} cannot read campusPoints or options`, async () => {
      const c = rulesContext(env, p).firestore();
      await assertFails(getDoc(doc(c, "campusPoints", "cp1")));
      await assertFails(getDoc(doc(c, "options", "profile")));
    });
  }
});
