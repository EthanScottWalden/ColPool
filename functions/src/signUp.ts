import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import * as functionsLogger from "firebase-functions/logger";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore, type Firestore } from "firebase-admin/firestore";
import { adminApp } from "./admin.ts";
import { COLLECTIONS, PROFILE_LIMITS } from "./shared/schema/index.ts";
import { CBU_EMAIL_SUFFIX, PASSWORD_MIN_LENGTH, SIGN_UP_REASONS, type SignUpReason } from "./shared/auth/reasons.ts";

export interface SignUpFields {
  email: string;
  firstName: string;
  lastName: string;
}

/** Writes users, profiles and consents together. Injectable so tests can force a failure. */
export type SignUpDocsWriter = (db: Firestore, uid: string, fields: SignUpFields) => Promise<void>;

export interface SignUpLogger {
  info(message: string, data?: Record<string, unknown>): void;
  error(message: string, data?: Record<string, unknown>): void;
}

export interface SignUpDeps {
  writeDocs?: SignUpDocsWriter;
  logger?: SignUpLogger;
}

/** One batch: all three docs, server timestamps (ADR 0002 §7, RC-06). */
export const writeSignUpDocs: SignUpDocsWriter = async (db, uid, f) => {
  const batch = db.batch();
  batch.create(db.collection(COLLECTIONS.users).doc(uid), {
    email: f.email,
    createdAt: FieldValue.serverTimestamp(),
  });
  batch.create(db.collection(COLLECTIONS.profiles).doc(uid), {
    firstName: f.firstName,
    lastName: f.lastName,
    updatedAt: FieldValue.serverTimestamp(),
  });
  batch.create(db.collection(COLLECTIONS.consents).doc(uid), {
    agreedAt: FieldValue.serverTimestamp(),
  });
  await batch.commit();
};

function fail(code: "invalid-argument" | "already-exists" | "internal", reason: SignUpReason, message: string): never {
  throw new HttpsError(code, message, { reason });
}

function validName(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const t = value.trim();
  return t.length > 0 && t.length <= max ? t : null;
}

/** Validates the input and returns normalized values; throws `invalid-argument` with a reason. */
export function validateSignUp(data: unknown): SignUpFields & { password: string } {
  const d = (typeof data === "object" && data !== null ? data : {}) as Record<string, unknown>;

  const rawEmail = typeof d["email"] === "string" ? d["email"].trim().toLowerCase() : "";
  const local = rawEmail.endsWith(CBU_EMAIL_SUFFIX) ? rawEmail.slice(0, -CBU_EMAIL_SUFFIX.length) : "";
  if (local.length === 0 || /[\s@]/.test(local)) {
    fail("invalid-argument", SIGN_UP_REASONS.emailDomain, "Use your @calbaptist.edu email.");
  }

  const password = d["password"];
  if (typeof password !== "string" || password.length < PASSWORD_MIN_LENGTH) {
    fail("invalid-argument", SIGN_UP_REASONS.passwordTooShort, "Password must be at least 8 characters.");
  }

  const firstName = validName(d["firstName"], PROFILE_LIMITS.firstNameMax);
  if (firstName === null) {
    fail("invalid-argument", SIGN_UP_REASONS.firstNameInvalid, "Enter a first name of up to 30 characters.");
  }
  const lastName = validName(d["lastName"], PROFILE_LIMITS.lastNameMax);
  if (lastName === null) {
    fail("invalid-argument", SIGN_UP_REASONS.lastNameInvalid, "Enter a last name of up to 30 characters.");
  }

  if (d["consent"] !== true) {
    fail("invalid-argument", SIGN_UP_REASONS.consentRequired, "Agree to the data-sharing consent statement to create an account.");
  }

  return { email: rawEmail, password, firstName, lastName };
}

// Error codes only: messages from Admin SDK errors can contain the email.
function errorCode(e: unknown): string {
  const c = (e as { code?: unknown } | null)?.code;
  return typeof c === "string" ? c : "unknown";
}

/** Builds the handler. Logs carry the uid and error codes only, never the email or password. */
export function createSignUpHandler(deps: SignUpDeps = {}) {
  const writeDocs = deps.writeDocs ?? writeSignUpDocs;
  const logger: SignUpLogger = deps.logger ?? functionsLogger;

  return async (request: CallableRequest<unknown>): Promise<{ uid: string }> => {
    const input = validateSignUp(request.data);
    const auth = getAuth(adminApp());

    let uid: string;
    try {
      uid = (await auth.createUser({ email: input.email, password: input.password })).uid;
    } catch (e) {
      const code = errorCode(e);
      if (code === "auth/email-already-exists") {
        fail("already-exists", SIGN_UP_REASONS.emailInUse, "An account with this email already exists.");
      }
      if (code === "auth/invalid-email") {
        fail("invalid-argument", SIGN_UP_REASONS.emailDomain, "Use your @calbaptist.edu email.");
      }
      logger.error("sign-up: auth create failed", { code });
      return fail("internal", SIGN_UP_REASONS.internal, "Something went wrong. Try again.");
    }

    try {
      await writeDocs(getFirestore(adminApp()), uid, input);
    } catch (e) {
      logger.error("sign-up: record write failed, removing account", { uid, code: errorCode(e) });
      try {
        await auth.deleteUser(uid);
      } catch (e2) {
        // RC-06: the leftover account has no consent record, so it is unusable. A developer can delete it.
        logger.error("sign-up: ROLLBACK FAILED, account left without consent record", { uid, code: errorCode(e2) });
      }
      return fail("internal", SIGN_UP_REASONS.internal, "Something went wrong. Try again.");
    }

    logger.info("sign-up complete", { uid });
    return { uid };
  };
}

export const signUp = onCall({ region: "us-west2" }, createSignUpHandler());
