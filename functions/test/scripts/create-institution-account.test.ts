import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import { initializeApp } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import {
  createInstitutionAccount,
  main,
  parseArgs,
  resolveTarget,
  ScriptError,
  INSTITUTION_CLAIM_KEY as SCRIPT_KEY,
} from "../../scripts/create-institution-account.ts";
import { INSTITUTION_CLAIM_KEY } from "../helpers/personas.ts";

// Fake @calbaptist.edu data only.
const app = initializeApp({ projectId: "demo-colpool" }, "institution-script-test");
const auth = getAuth(app);
const db = getFirestore(app);
const PASSWORD = "fake-pass-123";
let n = 0;
const freshEmail = () => `test.staff.${Date.now()}.${n++}@calbaptist.edu`;

describe("create-institution-account (emulator)", () => {
  before(() => {
    assert.ok(process.env["FIREBASE_AUTH_EMULATOR_HOST"], "run via emulators:exec");
  });

  it("uses the same claim key as the rules tests", () => {
    assert.equal(SCRIPT_KEY, INSTITUTION_CLAIM_KEY);
  });

  it("creates a user with institution: true and no student docs", async () => {
    const email = freshEmail();
    const { uid } = await createInstitutionAccount({ email: `  ${email.toUpperCase()} `, password: PASSWORD, displayName: "Test Staff" }, auth);
    const user = await auth.getUser(uid);
    assert.equal(user.email, email);
    assert.deepEqual(user.customClaims, { [INSTITUTION_CLAIM_KEY]: true });
    for (const col of ["consents", "users", "profiles", "privateDetails"]) {
      assert.equal((await db.collection(col).doc(uid).get()).exists, false, `${col}/${uid} must not exist`);
    }
  });

  it("refuses an existing email and leaves the user's claims unchanged", async () => {
    const email = freshEmail();
    const existing = await auth.createUser({ email, password: PASSWORD });
    await auth.setCustomUserClaims(existing.uid, { other: "x" });
    await assert.rejects(createInstitutionAccount({ email, password: PASSWORD }, auth), ScriptError);
    assert.deepEqual((await auth.getUser(existing.uid)).customClaims, { other: "x" });
  });

  it("refuses a non-CBU email and a short password", async () => {
    await assert.rejects(createInstitutionAccount({ email: "test.staff@example.com", password: PASSWORD }, auth), ScriptError);
    await assert.rejects(createInstitutionAccount({ email: "a@calbaptist.edu.evil.com", password: PASSWORD }, auth), ScriptError);
    const email = freshEmail();
    await assert.rejects(createInstitutionAccount({ email, password: "short" }, auth), ScriptError);
    await assert.rejects(auth.getUserByEmail(email), (e: { code?: string }) => e.code === "auth/user-not-found");
  });

  it("deletes the new user if setting the claim fails", async () => {
    const email = freshEmail();
    const failing = new Proxy(auth, {
      get(t, p) {
        if (p === "setCustomUserClaims") return async () => { throw new Error("boom"); };
        const v = Reflect.get(t, p, t) as unknown;
        return typeof v === "function" ? v.bind(t) : v;
      },
    });
    await assert.rejects(createInstitutionAccount({ email, password: PASSWORD }, failing), ScriptError);
    await assert.rejects(auth.getUserByEmail(email), (e: { code?: string }) => e.code === "auth/user-not-found");
  });

  it("CLI succeeds, prints uid and claim set, never the password", async () => {
    const email = freshEmail();
    const lines: string[] = [];
    const code = await main(["--email", email, "--display-name", "Test Staff"], {
      env: process.env,
      readPassword: async () => PASSWORD,
      out: (l) => lines.push(l),
      err: (l) => lines.push(l),
      auth: auth as Auth,
    });
    assert.equal(code, 0);
    const text = lines.join("\n");
    assert.match(text, /uid: \S+/);
    assert.match(text, /institution claim set/);
    assert.ok(!text.includes(PASSWORD));
  });

  it("CLI failure output never contains the password", async () => {
    const lines: string[] = [];
    const code = await main(["--email", "test.staff@example.com"], {
      env: process.env,
      readPassword: async () => PASSWORD,
      out: (l) => lines.push(l),
      err: (l) => lines.push(l),
      auth,
    });
    assert.equal(code, 1);
    assert.ok(!lines.join("\n").includes(PASSWORD));
  });
});

describe("create-institution-account (argument and target guards)", () => {
  it("parseArgs requires --email and rejects a password argument", () => {
    assert.throws(() => parseArgs([]), ScriptError);
    assert.throws(() => parseArgs(["--email", "a@calbaptist.edu", "--password", "x"]), ScriptError);
    assert.deepEqual(parseArgs(["--email", "a@calbaptist.edu", "--display-name", "A B"]), { email: "a@calbaptist.edu", displayName: "A B" });
  });

  it("emulator env targets demo-colpool", () => {
    assert.deepEqual(resolveTarget({}, { FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099" }), { mode: "emulator", projectId: "demo-colpool" });
    assert.throws(() => resolveTarget({ project: "colpool-prod" }, { FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099" }), ScriptError);
  });

  it("refuses without emulator env and without --project", () => {
    assert.throws(() => resolveTarget({}, {}), ScriptError);
  });

  it("refuses a demo- project in production mode", () => {
    assert.throws(() => resolveTarget({ project: "demo-other", confirmProject: "demo-other" }, {}), ScriptError);
  });

  it("production needs --confirm-project equal to --project, and no key file", () => {
    assert.throws(() => resolveTarget({ project: "colpool-prod" }, {}), /--confirm-project/);
    assert.throws(() => resolveTarget({ project: "colpool-prod", confirmProject: "colpool-other" }, {}), /does not match/);
    // ADC: no GOOGLE_APPLICATION_CREDENTIALS needed; still accepted when set.
    for (const env of [{}, { GOOGLE_APPLICATION_CREDENTIALS: "/x/key.json" }]) {
      assert.deepEqual(resolveTarget({ project: "colpool-prod", confirmProject: "colpool-prod" }, env), { mode: "production", projectId: "colpool-prod" });
    }
  });

  it("parseArgs reads --confirm-project", () => {
    assert.deepEqual(parseArgs(["--email", "a@calbaptist.edu", "--project", "p1", "--confirm-project", "p1"]), {
      email: "a@calbaptist.edu",
      project: "p1",
      confirmProject: "p1",
    });
  });

  it("main refuses before reading the password or touching Auth (mismatch, missing confirm, demo- outside the emulator)", async () => {
    const boom = new Proxy({}, { get: () => { throw new Error("Auth must not be touched"); } }) as unknown as Auth;
    for (const extra of [
      ["--project", "colpool-prod"],
      ["--project", "colpool-prod", "--confirm-project", "colpool-other"],
      ["--project", "demo-colpool", "--confirm-project", "demo-colpool"],
    ]) {
      const lines: string[] = [];
      const code = await main(["--email", "a@calbaptist.edu", ...extra], {
        env: {},
        readPassword: async () => { throw new Error("password must not be read"); },
        out: (l) => lines.push(l),
        err: (l) => lines.push(l),
        auth: boom,
      });
      assert.equal(code, 1);
      assert.match(lines.join("\n"), /Refusing to run/);
    }
  });

  it("CLI exits non-zero with the guard message when the target is unsafe", async () => {
    const lines: string[] = [];
    const code = await main(["--email", "a@calbaptist.edu"], { env: {}, readPassword: async () => PASSWORD, out: (l) => lines.push(l), err: (l) => lines.push(l) });
    assert.equal(code, 1);
    assert.match(lines.join("\n"), /Refusing to run/);
  });
});
