import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteField, doc, getDoc, serverTimestamp, setDoc, setLogLevel, writeBatch } from "firebase/firestore";
import { INTEREST_OPTIONS, MAJOR_OPTIONS, TRAIT_OPTIONS } from "../../src/shared/schema/index.ts";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import { authedStudent, hostStudent, rulesContext } from "../helpers/personas.ts";
import { ADDRESS, seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const OWNER = authedStudent();
const UID = OWNER.uid as string;
const NEW_ADDRESS = { text: "200 Sample Road, Riverside, CA", placeId: "test-place-2", lat: 33.95, lng: -117.45 };

/**
 * Edit Profile (#27) saves in one batch: a partial profile update (changed fields, `deleteField()` for removed
 * optional fields, `updatedAt`) and, only when the address changed, a privateDetails set. The student already
 * has a full profile and address.
 */
describe("edit profile batch (US-02, RC-30, RC-22)", () => {
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
    // A student with every optional field set.
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "profiles", UID), {
        firstName: "Test",
        lastName: "Student",
        age: 20,
        gender: "woman",
        year: "junior",
        major: MAJOR_OPTIONS[0]!.id,
        interests: [INTEREST_OPTIONS[0]!.id, INTEREST_OPTIONS[1]!.id],
        traits: [TRAIT_OPTIONS[0]!.id],
        aboutMe: "hello",
        updatedAt: new Date("2026-09-01T00:00:00Z"),
      });
    });
  });

  const run = (profile: Record<string, unknown> | null, address?: Record<string, unknown>, who = OWNER, target = UID) => {
    const db = rulesContext(env, who).firestore();
    const batch = writeBatch(db);
    if (profile) batch.update(doc(db, "profiles", target), { ...profile, updatedAt: serverTimestamp() });
    if (address) batch.set(doc(db, "privateDetails", target), { startingAddress: address, updatedAt: serverTimestamp() });
    return batch.commit();
  };
  const stored = async (col: string) => {
    let data: Record<string, unknown> | undefined;
    await env.withSecurityRulesDisabled(async (ctx) => {
      data = (await getDoc(doc(ctx.firestore(), col, UID))).data();
    });
    return data;
  };

  it("allows changing both names", async () => {
    await assertSucceeds(run({ firstName: "Casey", lastName: "Sample" }));
    assert.equal((await stored("profiles"))?.firstName, "Casey");
  });

  it("allows removing every optional field by deleting its key", async () => {
    await assertSucceeds(
      run({
        age: deleteField(),
        gender: deleteField(),
        year: deleteField(),
        major: deleteField(),
        interests: deleteField(),
        traits: deleteField(),
        aboutMe: deleteField(),
      }),
    );
    const p = await stored("profiles");
    for (const k of ["age", "gender", "year", "major", "interests", "traits", "aboutMe"]) assert.equal(k in (p ?? {}), false, k);
    assert.equal(p?.firstName, "Test");
  });

  it("allows replacing a list with a different valid list", async () => {
    await assertSucceeds(run({ interests: [INTEREST_OPTIONS[2]!.id] }));
  });

  it("allows an address change with no profile change", async () => {
    await assertSucceeds(run(null, NEW_ADDRESS));
    assert.deepEqual((await stored("privateDetails"))?.startingAddress, NEW_ADDRESS);
  });

  it("allows a profile change and an address change together", async () => {
    await assertSucceeds(run({ firstName: "Casey", aboutMe: deleteField() }, NEW_ADDRESS));
  });

  it("denies a blank, untrimmed or 31-character name, and saves nothing", async () => {
    for (const v of ["", "   ", " Casey", "a".repeat(31)]) {
      await assertFails(run({ firstName: v }, NEW_ADDRESS));
      await assertFails(run({ lastName: v }, NEW_ADDRESS));
    }
    assert.deepEqual((await stored("privateDetails"))?.startingAddress, ADDRESS);
    assert.equal((await stored("profiles"))?.firstName, "Test");
  });

  it("denies removing firstName or lastName", async () => {
    await assertFails(run({ firstName: deleteField() }));
    await assertFails(run({ lastName: deleteField() }));
  });

  it("denies writing an empty or blank value instead of deleting", async () => {
    await assertFails(run({ interests: [] }));
    await assertFails(run({ traits: [] }));
    await assertFails(run({ aboutMe: null }));
    await assertFails(run({ age: null }));
  });

  it("denies deleting the starting address", async () => {
    const db = rulesContext(env, OWNER).firestore();
    const batch = writeBatch(db);
    batch.delete(doc(db, "privateDetails", UID));
    await assertFails(batch.commit());
    assert.deepEqual((await stored("privateDetails"))?.startingAddress, ADDRESS);
  });

  it("denies clearing the starting address (missing, empty or without coordinates)", async () => {
    const db = rulesContext(env, OWNER).firestore();
    const ref = doc(db, "privateDetails", UID);
    await assertFails(setDoc(ref, { updatedAt: serverTimestamp() }));
    await assertFails(setDoc(ref, { startingAddress: { text: "", placeId: "p", lat: 1, lng: 2 }, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(ref, { startingAddress: { text: "x", placeId: "p" }, updatedAt: serverTimestamp() }));
  });

  it("denies another student editing the profile or address", async () => {
    await assertFails(run({ firstName: "Casey" }, undefined, hostStudent()));
    await assertFails(run(null, NEW_ADDRESS, hostStudent()));
    assert.equal((await stored("profiles"))?.firstName, "Test");
  });

  it("lets the owner read their own email, and nobody else", async () => {
    await assertSucceeds(getDoc(doc(rulesContext(env, OWNER).firestore(), "users", UID)));
    await assertFails(getDoc(doc(rulesContext(env, hostStudent()).firestore(), "users", UID)));
  });
});
