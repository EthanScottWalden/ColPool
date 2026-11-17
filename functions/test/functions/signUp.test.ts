import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { createSignUpHandler, signUp, type SignUpLogger } from "../../src/signUp.ts";
import { COLLECTIONS, PROFILE_LIMITS } from "../../src/shared/schema/index.ts";
import { PASSWORD_MIN_LENGTH, SIGN_UP_REASONS } from "../../src/shared/auth/reasons.ts";

const auth = getAuth(adminApp());
const db = getFirestore(adminApp());

let counter = 0;
const uniqueEmail = (tag: string) => `test.${tag}.${Date.now()}.${counter++}@calbaptist.edu`;

function valid(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    email: uniqueEmail("ok"),
    password: "correct-horse-1",
    firstName: "Test",
    lastName: "Student",
    consent: true,
    ...overrides,
  };
}

function req(data: unknown): CallableRequest<unknown> {
  return { data, auth: undefined, rawRequest: {} as CallableRequest["rawRequest"], acceptsStreaming: false };
}

interface Captured {
  logger: SignUpLogger;
  text(): string;
}
function captureLogger(): Captured {
  const lines: string[] = [];
  const rec = (level: string) => (message: string, data?: Record<string, unknown>) => {
    lines.push(JSON.stringify({ level, message, data }));
  };
  return { logger: { info: rec("info"), error: rec("error") }, text: () => lines.join("\n") };
}

async function rejectsWith(p: Promise<unknown>, code: string, reason: string): Promise<void> {
  await assert.rejects(p, (e: { code?: string; details?: { reason?: string } }) => {
    assert.equal(e.code, code);
    assert.equal(e.details?.reason, reason);
    return true;
  });
}

async function userExists(email: string): Promise<boolean> {
  try {
    await auth.getUserByEmail(email);
    return true;
  } catch (e) {
    if ((e as { code?: string }).code === "auth/user-not-found") return false;
    throw e;
  }
}

describe("signUp callable", () => {
  const handler = createSignUpHandler();

  before(() => {
    assert.ok(process.env["FIREBASE_AUTH_EMULATOR_HOST"], "run via emulators:exec");
    assert.ok(process.env["FIRESTORE_EMULATOR_HOST"], "run via emulators:exec");
  });

  describe("email", () => {
    for (const [name, email] of [
      ["non-CBU domain", "x@gmail.com"],
      ["subdomain", "x@sub.calbaptist.edu"],
      ["lookalike suffix", "x@notcalbaptist.edu"],
      ["lookalike TLD", "x@calbaptist.edu.evil.com"],
      ["empty local part", "@calbaptist.edu"],
      ["extra @ in local part", "a@b@calbaptist.edu"],
      ["whitespace in local part", "a b@calbaptist.edu"],
      ["blank", "   "],
    ] as const) {
      it(`rejects ${name}`, async () => {
        await rejectsWith(handler(req(valid({ email }))), "invalid-argument", SIGN_UP_REASONS.emailDomain);
      });
    }

    for (const [name, email] of [["missing", undefined], ["number", 5], ["null", null]] as const) {
      it(`rejects ${name} email`, async () => {
        await rejectsWith(handler(req(valid({ email }))), "invalid-argument", SIGN_UP_REASONS.emailDomain);
      });
    }

    it("rejects non-object data", async () => {
      await rejectsWith(handler(req(undefined)), "invalid-argument", SIGN_UP_REASONS.emailDomain);
    });

    it("accepts an uppercase, padded email and stores it lowercased", async () => {
      const lower = uniqueEmail("upper");
      const { uid } = await handler(req(valid({ email: `  ${lower.toUpperCase()}  ` })));
      assert.equal((await auth.getUser(uid)).email, lower);
      assert.equal((await db.collection(COLLECTIONS.users).doc(uid).get()).get("email"), lower);
    });
  });

  describe("password", () => {
    it(`rejects ${PASSWORD_MIN_LENGTH - 1} characters`, async () => {
      await rejectsWith(
        handler(req(valid({ password: "x".repeat(PASSWORD_MIN_LENGTH - 1) }))),
        "invalid-argument",
        SIGN_UP_REASONS.passwordTooShort,
      );
    });

    it(`accepts ${PASSWORD_MIN_LENGTH} characters`, async () => {
      const { uid } = await handler(req(valid({ password: "x".repeat(PASSWORD_MIN_LENGTH) })));
      assert.ok(uid);
    });

    it("does not trim: spaces count toward the length", async () => {
      const { uid } = await handler(req(valid({ password: "        " })));
      assert.ok(uid);
    });

    for (const [name, password] of [["missing", undefined], ["number", 12345678]] as const) {
      it(`rejects ${name} password`, async () => {
        await rejectsWith(handler(req(valid({ password }))), "invalid-argument", SIGN_UP_REASONS.passwordTooShort);
      });
    }
  });

  describe("names", () => {
    const cases = [
      ["firstName", SIGN_UP_REASONS.firstNameInvalid, PROFILE_LIMITS.firstNameMax],
      ["lastName", SIGN_UP_REASONS.lastNameInvalid, PROFILE_LIMITS.lastNameMax],
    ] as const;
    for (const [field, reason, max] of cases) {
      it(`${field}: rejects ${max + 1} characters`, async () => {
        await rejectsWith(handler(req(valid({ [field]: "a".repeat(max + 1) }))), "invalid-argument", reason);
      });
      it(`${field}: accepts ${max} characters`, async () => {
        const { uid } = await handler(req(valid({ [field]: "a".repeat(max) })));
        assert.equal((await db.collection(COLLECTIONS.profiles).doc(uid).get()).get(field), "a".repeat(max));
      });
      it(`${field}: trims before counting`, async () => {
        const { uid } = await handler(req(valid({ [field]: `  ${"a".repeat(max)}  ` })));
        assert.equal((await db.collection(COLLECTIONS.profiles).doc(uid).get()).get(field), "a".repeat(max));
      });
      for (const [name, value] of [["empty", ""], ["whitespace only", " \t "], ["missing", undefined], ["number", 7]] as const) {
        it(`${field}: rejects ${name}`, async () => {
          await rejectsWith(handler(req(valid({ [field]: value }))), "invalid-argument", reason);
        });
      }
    }
  });

  describe("consent", () => {
    for (const [name, consent] of [
      ["false", false],
      ["missing", undefined],
      ["string true", "true"],
      ["number 1", 1],
    ] as const) {
      it(`rejects ${name}`, async () => {
        const email = uniqueEmail("consent");
        await rejectsWith(handler(req(valid({ email, consent }))), "invalid-argument", SIGN_UP_REASONS.consentRequired);
        assert.equal(await userExists(email), false);
      });
    }
  });

  it("rejects an email already in use with email-in-use", async () => {
    const data = valid();
    await handler(req(data));
    await rejectsWith(handler(req({ ...data, firstName: "Other" })), "already-exists", SIGN_UP_REASONS.emailInUse);
  });

  it("success: Auth user and three docs with the right fields and server timestamps", async () => {
    const data = valid({ firstName: "  Ana ", lastName: " Lopez  " });
    const before = Date.now();
    const result = await signUp.run(req(data));
    const after = Date.now();
    const uid = result.uid;

    const user = await auth.getUser(uid);
    assert.equal(user.email, data["email"]);

    const [u, p, c] = await Promise.all([
      db.collection(COLLECTIONS.users).doc(uid).get(),
      db.collection(COLLECTIONS.profiles).doc(uid).get(),
      db.collection(COLLECTIONS.consents).doc(uid).get(),
    ]);
    assert.deepEqual(Object.keys(u.data()!).sort(), ["createdAt", "email"]);
    assert.deepEqual(Object.keys(p.data()!).sort(), ["firstName", "lastName", "updatedAt"]);
    assert.deepEqual(Object.keys(c.data()!), ["agreedAt"]);
    assert.equal(u.get("email"), data["email"]);
    assert.equal(p.get("firstName"), "Ana");
    assert.equal(p.get("lastName"), "Lopez");
    for (const ts of [u.get("createdAt"), p.get("updatedAt"), c.get("agreedAt")]) {
      assert.ok(ts instanceof Timestamp);
      const ms = (ts as Timestamp).toMillis();
      assert.ok(ms >= before - 5000 && ms <= after + 5000, "server timestamp near now");
    }
  });

  it("induced failure after Auth creation: Auth user removed, no docs remain", async () => {
    const email = uniqueEmail("fail");
    let seenUid = "";
    const failing = createSignUpHandler({
      writeDocs: async (_db, uid) => {
        seenUid = uid;
        assert.ok(await userExists(email), "Auth user exists before rollback");
        throw Object.assign(new Error("boom"), { code: "forced" });
      },
    });
    await rejectsWith(failing(req(valid({ email }))), "internal", SIGN_UP_REASONS.internal);
    assert.equal(await userExists(email), false);
    for (const col of [COLLECTIONS.users, COLLECTIONS.profiles, COLLECTIONS.consents]) {
      assert.equal((await db.collection(col).doc(seenUid).get()).exists, false);
    }
  });

  it("batch is atomic: a failing batch leaves no partial docs", async () => {
    const { writeSignUpDocs } = await import("../../src/signUp.ts");
    const uid = `atomic-${Date.now()}`;
    await db.collection(COLLECTIONS.consents).doc(uid).set({ pre: true }); // batch.create on it will fail
    await assert.rejects(writeSignUpDocs(db, uid, { email: "a@calbaptist.edu", firstName: "A", lastName: "B" }));
    assert.equal((await db.collection(COLLECTIONS.users).doc(uid).get()).exists, false);
    assert.equal((await db.collection(COLLECTIONS.profiles).doc(uid).get()).exists, false);
  });

  it("rollback failure is logged with uid and code only", async () => {
    const cap = captureLogger();
    const email = uniqueEmail("rbfail");
    const failing = createSignUpHandler({
      logger: cap.logger,
      writeDocs: async (_db, uid) => {
        await auth.deleteUser(uid); // makes the handler's own deleteUser fail with auth/user-not-found
        throw new Error(`write failed for ${email}`);
      },
    });
    await rejectsWith(failing(req(valid({ email }))), "internal", SIGN_UP_REASONS.internal);
    assert.match(cap.text(), /ROLLBACK FAILED/);
    assert.match(cap.text(), /auth\/user-not-found/);
    assert.ok(!cap.text().includes(email));
  });

  describe("logs never contain the email or password", () => {
    const password = "Sup3r-secret-pw-xyz";

    it("on success", async () => {
      const cap = captureLogger();
      const email = uniqueEmail("logok");
      await createSignUpHandler({ logger: cap.logger })(req(valid({ email, password })));
      assert.ok(cap.text().length > 0);
      assert.ok(!cap.text().includes(email) && !cap.text().includes(password));
    });

    it("on induced failure", async () => {
      const cap = captureLogger();
      const email = uniqueEmail("logfail");
      const h = createSignUpHandler({
        logger: cap.logger,
        writeDocs: async () => {
          throw new Error(`failed for ${email} / ${password}`);
        },
      });
      await assert.rejects(h(req(valid({ email, password }))));
      assert.ok(cap.text().length > 0);
      assert.ok(!cap.text().includes(email) && !cap.text().includes(password));
    });

    it("on email already in use", async () => {
      const cap = captureLogger();
      const data = valid({ password });
      await handler(req(data));
      await assert.rejects(createSignUpHandler({ logger: cap.logger })(req(data)));
      assert.ok(!cap.text().includes(String(data["email"])) && !cap.text().includes(password));
    });
  });
});
