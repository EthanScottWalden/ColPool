import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { adminApp } from "../../src/admin.ts";
import { createSignUpHandler } from "../../src/signUp.ts";
import { requireStudent } from "../../src/shared/auth/requireStudent.ts";
import { STUDENT_GATE_REASONS } from "../../src/shared/auth/reasons.ts";
import { authEmulatorIdToken, INSTITUTION_CLAIM_KEY, type Persona } from "../helpers/personas.ts";

const adminAuth = getAuth(adminApp());

async function requestFor(idToken: string | null): Promise<CallableRequest<unknown>> {
  let auth: CallableRequest["auth"];
  if (idToken !== null) {
    const decoded = await adminAuth.verifyIdToken(idToken);
    auth = { uid: decoded.uid, rawToken: idToken, token: decoded as unknown as NonNullable<CallableRequest["auth"]>["token"] };
  }
  return { data: undefined, auth, rawRequest: {} as CallableRequest["rawRequest"], acceptsStreaming: false };
}

// Unique fake accounts; uid is assigned by the Auth emulator.
function persona(tag: string, claims: Record<string, unknown> = {}): Persona {
  return { name: tag, uid: tag, email: `test.gate.${tag}@calbaptist.edu`, claims };
}

async function rejectsWith(p: Promise<unknown>, code: string, reason?: string): Promise<void> {
  await assert.rejects(p, (e: { code?: string; details?: { reason?: string } }) => {
    assert.equal(e.code, code);
    if (reason !== undefined) assert.equal(e.details?.reason, reason);
    return true;
  });
}

describe("requireStudent (shared consent check)", () => {
  before(() => {
    assert.ok(process.env["FIREBASE_AUTH_EMULATOR_HOST"], "run via emulators:exec");
  });

  it("rejects an unauthenticated caller", async () => {
    await rejectsWith(requireStudent(await requestFor(null)), "unauthenticated");
  });

  it("rejects an Auth account created directly (no consent record) with consent-missing", async () => {
    const token = await authEmulatorIdToken(persona("noconsent"));
    await rejectsWith(requireStudent(await requestFor(token)), "permission-denied", STUDENT_GATE_REASONS.consentMissing);
  });

  it("rejects an institution-claim user with institution-account", async () => {
    const token = await authEmulatorIdToken(persona("inst-true", { [INSTITUTION_CLAIM_KEY]: true }));
    await rejectsWith(requireStudent(await requestFor(token)), "permission-denied", STUDENT_GATE_REASONS.institutionAccount);
  });

  it('rejects the claim set to the string "true" (any value counts)', async () => {
    const token = await authEmulatorIdToken(persona("inst-str", { [INSTITUTION_CLAIM_KEY]: "true" }));
    await rejectsWith(requireStudent(await requestFor(token)), "permission-denied", STUDENT_GATE_REASONS.institutionAccount);
  });

  it("rejects the claim set to false (presence counts, fails closed)", async () => {
    const token = await authEmulatorIdToken(persona("inst-false", { [INSTITUTION_CLAIM_KEY]: false }));
    await rejectsWith(requireStudent(await requestFor(token)), "permission-denied", STUDENT_GATE_REASONS.institutionAccount);
  });

  it("accepts a student who signed up through signUp and returns the uid", async () => {
    const email = `test.gate.signedup.${Date.now()}@calbaptist.edu`;
    const { uid } = await createSignUpHandler()({
      data: { email, password: "correct-horse-1", firstName: "Test", lastName: "Student", consent: true },
      auth: undefined,
      rawRequest: {} as CallableRequest["rawRequest"],
      acceptsStreaming: false,
    });
    const token = await adminAuth.createCustomToken(uid);
    const host = process.env["FIREBASE_AUTH_EMULATOR_HOST"]!;
    const res = await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=fake-api-key`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ token, returnSecureToken: true }),
    });
    assert.ok(res.ok);
    const { idToken } = (await res.json()) as { idToken: string };
    assert.equal(await requireStudent(await requestFor(idToken)), uid);
  });
});
