import { HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { adminApp } from "../../admin.ts";
import { COLLECTIONS } from "../schema/index.ts";
import { INSTITUTION_CLAIM, STUDENT_GATE_REASONS } from "./reasons.ts";

/**
 * Shared gate that every student-facing callable runs first (RC-06, RC-07).
 * Returns the caller's uid. Institution accounts are refused here (the claim
 * counts when present with any value, so this fails closed); the export
 * function checks the claim itself instead.
 */
export async function requireStudent(
  request: CallableRequest<unknown>,
  db: Firestore = getFirestore(adminApp()),
): Promise<string> {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  if (INSTITUTION_CLAIM in auth.token) {
    throw new HttpsError("permission-denied", "This account can't use this feature.", {
      reason: STUDENT_GATE_REASONS.institutionAccount,
    });
  }
  const consent = await db.collection(COLLECTIONS.consents).doc(auth.uid).get();
  if (!consent.exists) {
    throw new HttpsError("permission-denied", "Agree to the terms to use ColPool.", {
      reason: STUDENT_GATE_REASONS.consentMissing,
    });
  }
  return auth.uid;
}
