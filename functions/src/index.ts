import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";

setGlobalOptions({ region: "us-west2" });

// Harness sample only: exported solely under the Functions emulator so it is
// never part of a deployed codebase. Real callables arrive with their Issues.
export const ping = process.env["FUNCTIONS_EMULATOR"] === "true"
  ? onCall((request) => {
      if (!request.auth) {
        throw new HttpsError("unauthenticated", "Sign in first.");
      }
      return { ok: true };
    })
  : undefined;
