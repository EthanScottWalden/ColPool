import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth, signInWithEmailAndPassword } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { authEmulatorIdToken, authedStudent } from "../helpers/personas.ts";

const app = initializeApp({ projectId: "demo-colpool", apiKey: "fake-api-key" }, "ping-test");
const functions = getFunctions(app, "us-west2");
connectFunctionsEmulator(functions, "127.0.0.1", 5001);
const auth = getAuth(app);
connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });

describe("ping callable", () => {
  it("unauthenticated caller gets unauthenticated", async () => {
    await assert.rejects(httpsCallable(functions, "ping")(), (e: { code?: string }) => e.code === "functions/unauthenticated");
  });

  it("authed student gets { ok: true }", async () => {
    const p = authedStudent();
    await authEmulatorIdToken(p); // creates the user in the Auth emulator
    await signInWithEmailAndPassword(auth, p.email as string, "test-password-1");
    const res = await httpsCallable(functions, "ping")();
    assert.deepEqual(res.data, { ok: true });
  });
});
