import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import {
  DEV_ACCOUNTS,
  DEV_PASSWORD,
  DEV_STARTING_ADDRESS,
  main,
  resolveTarget,
  ScriptError,
  seedDevAccounts,
  type SignUpCall,
} from "../../scripts/seed-dev-accounts.ts";
import { writeSignUpDocs } from "../../src/signUp.ts";
import { INSTITUTION_CLAIM_KEY } from "../helpers/personas.ts";

// Fake data only. The real signUp callable needs the Functions emulator (not run by npm test), so a
// stand-in does what the handler does: create the Auth user, then write users/profiles/consents.
const app = initializeApp({ projectId: "demo-colpool" }, "seed-dev-accounts-test");
const auth = getAuth(app);
const db = getFirestore(app);
let signUpCalls = 0;
const signUp: SignUpCall = async (i) => {
  signUpCalls++;
  const uid = (await auth.createUser({ email: i.email, password: i.password })).uid;
  await writeSignUpDocs(db, uid, { email: i.email, firstName: i.firstName, lastName: i.lastName });
  return uid;
};
const emu = { FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099", FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" };

const exists = async (col: string, uid: string) => (await db.collection(col).doc(uid).get()).exists;

describe("seed-dev-accounts (emulator)", () => {
  before(async () => {
    assert.ok(process.env["FIREBASE_AUTH_EMULATOR_HOST"], "run via emulators:exec");
    // Clean slate for the fixed dev emails (other test files use other emails).
    for (const a of DEV_ACCOUNTS) {
      try {
        const u = await auth.getUserByEmail(a.email);
        await auth.deleteUser(u.uid);
      } catch {
        /* not there */
      }
    }
  });

  it("creates one account per routing case, then skips all of them on the second run", async () => {
    const first = await seedDevAccounts({ auth, db, signUp });
    assert.deepEqual(first.map((r) => r.status), ["created", "created", "created", "created"]);
    assert.equal(signUpCalls, 2);

    const by = async (email: string) => auth.getUserByEmail(email);
    const onboarded = await by("dev.student@calbaptist.edu");
    const fresh = await by("dev.newstudent@calbaptist.edu");
    const noConsent = await by("dev.noconsent@calbaptist.edu");
    const staff = await by("dev.staff@calbaptist.edu");

    assert.ok(await exists("consents", onboarded.uid));
    assert.ok(await exists("profiles", onboarded.uid));
    const pd = (await db.collection("privateDetails").doc(onboarded.uid).get()).data();
    assert.deepEqual(pd?.["startingAddress"], DEV_STARTING_ADDRESS);
    assert.ok(pd?.["updatedAt"]);
    assert.deepEqual(Object.keys(pd ?? {}).sort(), ["startingAddress", "updatedAt"]);

    assert.ok(await exists("consents", fresh.uid));
    assert.equal(await exists("privateDetails", fresh.uid), false);

    for (const col of ["consents", "users", "profiles", "privateDetails"]) assert.equal(await exists(col, noConsent.uid), false);
    assert.equal(noConsent.customClaims?.[INSTITUTION_CLAIM_KEY], undefined);

    assert.equal(staff.customClaims?.[INSTITUTION_CLAIM_KEY], true);
    assert.equal(await exists("consents", staff.uid), false);

    const before = (await db.collection("privateDetails").doc(onboarded.uid).get()).updateTime?.toMillis();
    const second = await seedDevAccounts({ auth, db, signUp });
    assert.deepEqual(second.map((r) => r.status), ["exists", "exists", "exists", "exists"]);
    assert.equal(signUpCalls, 2, "no second signUp");
    assert.equal((await db.collection("privateDetails").doc(onboarded.uid).get()).updateTime?.toMillis(), before);
  });

  it("main prints the table with the fake password and exits 0", async () => {
    const out: string[] = [];
    const code = await main([], { env: emu, out: (l) => out.push(l), err: () => {}, auth, db, signUp });
    assert.equal(code, 0);
    const text = out.join("\n");
    for (const a of DEV_ACCOUNTS) assert.match(text, new RegExp(a.email.replace(/\./g, "\\.")));
    assert.match(text, new RegExp(DEV_PASSWORD));
  });
});

describe("seed-dev-accounts guard (pure)", () => {
  it("refuses with no emulator env, or only one of the two hosts", () => {
    assert.throws(() => resolveTarget({}), ScriptError);
    assert.throws(() => resolveTarget({ FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099" }), ScriptError);
    assert.throws(() => resolveTarget({ FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }), ScriptError);
  });
  it("refuses a non-demo project even with the emulator env set", () => {
    assert.throws(() => resolveTarget({ ...emu, GCLOUD_PROJECT: "real-proj" }), ScriptError);
  });
  it("refuses emulator hosts that are not on this machine", () => {
    assert.throws(() => resolveTarget({ ...emu, FIRESTORE_EMULATOR_HOST: "example.com:8080" }), ScriptError);
  });
  it("accepts loopback emulators and defaults to demo-colpool", () => {
    assert.deepEqual(resolveTarget(emu), { projectId: "demo-colpool" });
    assert.deepEqual(resolveTarget({ ...emu, GCLOUD_PROJECT: "demo-other" }), { projectId: "demo-other" });
    assert.deepEqual(resolveTarget({ FIREBASE_AUTH_EMULATOR_HOST: "localhost:9099", FIRESTORE_EMULATOR_HOST: "[::1]:8080" }), { projectId: "demo-colpool" });
  });
  it("main refuses and exits 1 without a target, and rejects arguments", async () => {
    const errs: string[] = [];
    assert.equal(await main([], { env: {}, out: () => {}, err: (l) => errs.push(l) }), 1);
    assert.match(errs[0] ?? "", /Refusing/);
    assert.equal(await main(["--project", "x"], { env: emu, out: () => {}, err: () => {} }), 1);
  });
});
