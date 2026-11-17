import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, serverTimestamp, setDoc, setLogLevel } from "firebase/firestore";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import { ADDRESS, HOST_UID, seedAccess, seedBase } from "../helpers/rulesSeed.ts";
import { institutionStringClaimUser, institutionUser, rulesContext, studentWithoutConsent, type Persona } from "../helpers/personas.ts";

setLogLevel("silent");

async function denyEverything(env: RulesTestEnvironment, p: Persona): Promise<void> {
  const db = rulesContext(env, p).firestore();
  const uid = p.uid as string;
  for (const [col, id] of [
    ["users", uid],
    ["profiles", uid],
    ["profiles", HOST_UID],
    ["privateDetails", uid],
    ["schedules", uid],
    ["consents", uid],
    ["profileAccess", HOST_UID],
    ["campusPoints", "cp1"],
    ["options", "profile"],
  ] as const) {
    await assertFails(getDoc(doc(db, col, id)));
  }
  await assertFails(getDocs(collection(db, "campusPoints")));
  await assertFails(
    setDoc(doc(db, "privateDetails", uid), { startingAddress: { ...ADDRESS }, updatedAt: serverTimestamp() }),
  );
  await assertFails(setDoc(doc(db, "profiles", uid), { firstName: "A", lastName: "B", updatedAt: serverTimestamp() }));
}

describe("institution accounts (RC-07) and accounts without consent (RC-06)", () => {
  let env: RulesTestEnvironment;
  before(async () => {
    env = await createRulesEnv();
  });
  after(async () => {
    await env.cleanup();
  });

  describe("institution account without a consent doc", () => {
    beforeEach(async () => {
      await env.clearFirestore();
      await seedBase(env);
      await seedAccess(env, HOST_UID, { activeHostedSeries: { s1: true } });
    });
    it("reads and writes nothing, including an active host's profile", async () => {
      await denyEverything(env, institutionUser());
    });
  });

  describe("institution account that also has a consent doc (defense in depth)", () => {
    beforeEach(async () => {
      await env.clearFirestore();
      await seedBase(env, [institutionUser()]);
      await seedAccess(env, HOST_UID, { activeHostedSeries: { s1: true } });
    });
    it("is still denied everything", async () => {
      await denyEverything(env, institutionUser());
    });
    it("is denied a profile it has viewer access to", async () => {
      const uid = institutionUser().uid as string;
      await seedAccess(env, HOST_UID, { viewers: { [uid]: { "req:s1": true } } });
      await assertFails(getDoc(doc(rulesContext(env, institutionUser()).firestore(), "profiles", HOST_UID)));
    });
  });

  describe("institution claim with a non-boolean value (fails closed)", () => {
    beforeEach(async () => {
      await env.clearFirestore();
      await seedBase(env, [institutionStringClaimUser()]);
      await seedAccess(env, HOST_UID, { activeHostedSeries: { s1: true } });
    });
    it("is denied everything even with a consent doc", async () => {
      await denyEverything(env, institutionStringClaimUser());
    });
  });

  describe("account with neither consent record nor claim (created directly in Auth)", () => {
    beforeEach(async () => {
      await env.clearFirestore();
      await seedBase(env);
      await seedAccess(env, HOST_UID, { activeHostedSeries: { s1: true } });
    });
    it("reads and writes nothing, including its own docs", async () => {
      await denyEverything(env, studentWithoutConsent());
    });
  });
});
