import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import type { CallableRequest } from "firebase-functions/v2/https";
import { ping } from "../../src/ping.ts";
import { authEmulatorIdToken, authedStudent, institutionUser, INSTITUTION_CLAIM_KEY } from "../helpers/personas.ts";

// Calls the handler directly with `.run`, so no Functions emulator is needed.
// Admin SDK talks to the Auth emulator (FIREBASE_AUTH_EMULATOR_HOST from emulators:exec).
const adminAuth = getAuth(initializeApp({ projectId: "demo-colpool" }, "ping-unit"));

async function requestFor(idToken: string | null): Promise<CallableRequest<unknown>> {
  let auth: CallableRequest["auth"];
  if (idToken !== null) {
    const decoded = await adminAuth.verifyIdToken(idToken);
    auth = { uid: decoded.uid, rawToken: idToken, token: decoded as unknown as NonNullable<CallableRequest["auth"]>["token"] };
  }
  return { data: undefined, auth, rawRequest: {} as CallableRequest["rawRequest"], acceptsStreaming: false };
}

describe("ping callable (handler unit)", () => {
  before(() => {
    assert.ok(process.env["FIREBASE_AUTH_EMULATOR_HOST"], "run via emulators:exec");
  });

  it("unauthenticated request is rejected with unauthenticated", async () => {
    await assert.rejects(
      async () => ping.run(await requestFor(null)),
      (e: { code?: string }) => e.code === "unauthenticated",
    );
  });

  it("authed student gets { ok: true }", async () => {
    const token = await authEmulatorIdToken(authedStudent());
    assert.deepEqual(await ping.run(await requestFor(token)), { ok: true });
  });

  it("institution user (custom claim) is authenticated too", async () => {
    const token = await authEmulatorIdToken(institutionUser());
    const req = await requestFor(token);
    assert.equal(req.auth?.token[INSTITUTION_CLAIM_KEY], true);
    assert.deepEqual(await ping.run(req), { ok: true });
  });
});
