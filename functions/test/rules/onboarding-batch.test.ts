import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setLogLevel, writeBatch } from "firebase/firestore";
import { INTEREST_OPTIONS, MAJOR_OPTIONS, TRAIT_OPTIONS } from "../../src/shared/schema/index.ts";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import { authedStudent, hostStudent, rulesContext } from "../helpers/personas.ts";
import { ADDRESS, seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const OWNER = authedStudent();
const UID = OWNER.uid as string;

/**
 * Onboarding (#22) saves the profile fields and the private starting address in ONE batch.
 * These tests run that exact shape: a partial profile update (only the fields set) plus a
 * privateDetails create, for a student who has no privateDetails yet.
 */
describe("onboarding batch (US-02, RC-30, RC-22)", () => {
  let env: RulesTestEnvironment;
  before(async () => {
    env = await createRulesEnv();
  });
  after(async () => {
    await env.cleanup();
  });
  beforeEach(async () => {
    await env.clearFirestore();
    await seedBase(env, []);
    // A new student has no privateDetails yet.
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), "privateDetails", UID));
    });
  });

  const run = async (profile: Record<string, unknown>, address: Record<string, unknown> = { ...ADDRESS }) => {
    const db = rulesContext(env, OWNER).firestore();
    const batch = writeBatch(db);
    batch.update(doc(db, "profiles", UID), { ...profile, updatedAt: serverTimestamp() });
    batch.set(doc(db, "privateDetails", UID), { startingAddress: address, updatedAt: serverTimestamp() });
    await batch.commit();
  };
  const privateExists = async () => {
    let exists = false;
    await env.withSecurityRulesDisabled(async (ctx) => {
      exists = (await getDoc(doc(ctx.firestore(), "privateDetails", UID))).exists();
    });
    return exists;
  };

  it("allows address only (every optional field skipped)", async () => {
    await assertSucceeds(run({}));
    assert.equal(await privateExists(), true);
  });

  it("allows every optional field", async () => {
    await assertSucceeds(
      run({
        age: 18,
        gender: "woman",
        year: "junior",
        major: MAJOR_OPTIONS[0]!.id,
        interests: [INTEREST_OPTIONS[0]!.id],
        traits: [TRAIT_OPTIONS[0]!.id],
        aboutMe: "x".repeat(300),
      }),
    );
  });

  const bad: Record<string, Record<string, unknown>> = {
    "age 17": { age: 17 },
    "age not a whole number": { age: 18.5 },
    "age as text": { age: "21" },
    "aboutMe of 301 characters": { aboutMe: "x".repeat(301) },
    "an unknown interest id": { interests: ["not-a-real-interest"] },
    "an unknown gender id": { gender: "Woman" },
    "an empty list": { traits: [] },
  };
  for (const [name, profile] of Object.entries(bad)) {
    it(`denies ${name}, and no address is saved`, async () => {
      await assertFails(run(profile));
      assert.equal(await privateExists(), false);
    });
  }

  it("denies an address without coordinates, and no profile field is saved", async () => {
    await assertFails(run({ age: 30 }, { text: ADDRESS.text, placeId: "p" }));
    assert.equal(await privateExists(), false);
  });

  it("denies writing another student's profile and address", async () => {
    const db = rulesContext(env, hostStudent()).firestore();
    const batch = writeBatch(db);
    batch.update(doc(db, "profiles", UID), { age: 20, updatedAt: serverTimestamp() });
    batch.set(doc(db, "privateDetails", UID), { startingAddress: { ...ADDRESS }, updatedAt: serverTimestamp() });
    await assertFails(batch.commit());
  });
});
