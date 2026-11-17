import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, deleteField, doc, serverTimestamp, setDoc, updateDoc, setLogLevel } from "firebase/firestore";
import { INTEREST_OPTIONS, MAJOR_OPTIONS, PROFILE_LIMITS, TRAIT_OPTIONS } from "../../src/shared/schema/index.ts";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent,
  hostStudent,
  institutionUser,
  rulesContext,
  studentWithoutConsent,
  unauthenticated,
} from "../helpers/personas.ts";
import { seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const OWNER = authedStudent();
const UID = OWNER.uid as string;
const I1 = INTEREST_OPTIONS[0]!.id;
const T1 = TRAIT_OPTIONS[0]!.id;
const M1 = MAJOR_OPTIONS[0]!.id;

describe("profiles write (RC-30, RC-22)", () => {
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

  const ref = () => doc(rulesContext(env, OWNER).firestore(), "profiles", UID);
  const base = (): Record<string, unknown> => ({ firstName: "Test", lastName: "Student", updatedAt: serverTimestamp() });
  const put = (over: Record<string, unknown>) => setDoc(ref(), { ...base(), ...over });

  it("allows a minimal valid profile", async () => {
    await assertSucceeds(put({}));
  });

  it("allows a full valid profile", async () => {
    await assertSucceeds(
      put({
        age: 21,
        gender: "prefer-not-to-say",
        year: "junior",
        major: M1,
        interests: [I1],
        traits: [T1],
        aboutMe: "Hi there",
      }),
    );
  });

  it("allows an update that only touches non-list fields without reading options", async () => {
    await assertSucceeds(updateDoc(ref(), { aboutMe: "new", updatedAt: serverTimestamp() }));
  });

  it("boundaries: name max", async () => {
    await assertSucceeds(put({ firstName: "a".repeat(PROFILE_LIMITS.firstNameMax) }));
    await assertFails(put({ firstName: "a".repeat(PROFILE_LIMITS.firstNameMax + 1) }));
    await assertSucceeds(put({ lastName: "a".repeat(PROFILE_LIMITS.lastNameMax) }));
    await assertFails(put({ lastName: "a".repeat(PROFILE_LIMITS.lastNameMax + 1) }));
  });

  it("boundaries: aboutMe max", async () => {
    await assertSucceeds(put({ aboutMe: "a".repeat(PROFILE_LIMITS.aboutMeMax) }));
    await assertFails(put({ aboutMe: "a".repeat(PROFILE_LIMITS.aboutMeMax + 1) }));
  });

  it("boundaries: age min", async () => {
    await assertSucceeds(put({ age: PROFILE_LIMITS.ageMin }));
    await assertFails(put({ age: PROFILE_LIMITS.ageMin - 1 }));
  });

  it("age must be a whole number", async () => {
    await assertFails(put({ age: 18.5 }));
    await assertFails(put({ age: "20" }));
  });

  it("denies blank, empty and untrimmed names", async () => {
    for (const v of ["", " ", "   ", " Ann", "Ann ", "\tAnn", "Ann\n"]) {
      await assertFails(put({ firstName: v }));
      await assertFails(put({ lastName: v }));
    }
  });

  it("allows a single-character and an inner-space name", async () => {
    await assertSucceeds(put({ firstName: "A" }));
    await assertSucceeds(put({ firstName: "Mary Ann" }));
  });

  it("requires firstName and lastName", async () => {
    await assertFails(setDoc(ref(), { lastName: "Student", updatedAt: serverTimestamp() }));
    await assertFails(setDoc(ref(), { firstName: "Test", updatedAt: serverTimestamp() }));
  });

  it("denies unknown option ids", async () => {
    await assertFails(put({ gender: "nope" }));
    await assertFails(put({ year: "nope" }));
    await assertFails(put({ major: "nope" }));
    await assertFails(put({ interests: ["nope"] }));
    await assertFails(put({ traits: ["nope"] }));
    await assertFails(put({ interests: [I1, "nope"] }));
  });

  it("denies option labels in place of ids", async () => {
    await assertFails(put({ gender: "Prefer not to say" }));
  });

  it("denies duplicate list entries and empty lists", async () => {
    await assertFails(put({ interests: [I1, I1] }));
    await assertFails(put({ traits: [T1, T1] }));
    await assertFails(put({ interests: [] }));
    await assertFails(put({ traits: [] }));
  });

  it("denies a non-list interests value", async () => {
    await assertFails(put({ interests: I1 }));
  });

  it("denies extra keys, including a field named like an RC-03 category", async () => {
    await assertFails(put({ extra: 1 }));
    await assertFails(put({ religion: "x" }));
    await assertFails(put({ race: "x" }));
    await assertFails(put({ photoUrl: "x" }));
    await assertFails(put({ startingAddress: { text: "x" } }));
  });

  it("denies null values (absent fields are deleted, never null)", async () => {
    for (const k of ["age", "gender", "year", "major", "interests", "traits", "aboutMe"]) {
      await assertFails(put({ [k]: null }));
    }
  });

  it("allows deleting an optional field", async () => {
    await assertSucceeds(put({ aboutMe: "x" }));
    await assertSucceeds(updateDoc(ref(), { aboutMe: deleteField(), updatedAt: serverTimestamp() }));
  });

  it("denies a wrong or missing updatedAt", async () => {
    await assertFails(put({ updatedAt: new Date("2020-01-01T00:00:00Z") }));
    await assertFails(setDoc(ref(), { firstName: "Test", lastName: "Student" }));
  });

  it("denies writing another student's profile", async () => {
    const other = doc(rulesContext(env, hostStudent()).firestore(), "profiles", UID);
    await assertFails(setDoc(other, base()));
    await assertFails(updateDoc(other, { aboutMe: "x", updatedAt: serverTimestamp() }));
  });

  for (const p of [institutionUser(), studentWithoutConsent(), unauthenticated()]) {
    it(`${p.name} cannot write a profile`, async () => {
      const r = doc(rulesContext(env, p).firestore(), "profiles", p.uid ?? UID);
      await assertFails(setDoc(r, base()));
    });
  }

  it("denies create (the backend creates the profile at sign-up)", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), "profiles", UID));
    });
    await assertFails(setDoc(ref(), base()));
  });

  it("denies delete, even by the owner", async () => {
    await assertFails(deleteDoc(ref()));
  });
});
