import { HttpsError, onCall } from "firebase-functions/v2/https";

// Harness sample only. Defined here so tests can import it; index.ts exports it
// only under the Functions emulator, so it is never part of a deployed codebase.
export const ping = onCall({ region: "us-west2" }, (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  return { ok: true };
});
