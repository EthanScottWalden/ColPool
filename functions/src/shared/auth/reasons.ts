// Stable machine reasons carried in `HttpsError.details.reason` by the sign-up
// callable and the shared consent check. The Android app (#19, #22) mirrors these
// strings to pick the message to show. Changing one is a contract change.

export const SIGN_UP_REASONS = {
  emailDomain: "email-domain",
  passwordTooShort: "password-too-short",
  firstNameInvalid: "first-name-invalid",
  lastNameInvalid: "last-name-invalid",
  consentRequired: "consent-required",
  emailInUse: "email-in-use",
  internal: "internal",
} as const;

export const STUDENT_GATE_REASONS = {
  institutionAccount: "institution-account",
  consentMissing: "consent-missing",
} as const;

export type SignUpReason = (typeof SIGN_UP_REASONS)[keyof typeof SIGN_UP_REASONS];
export type StudentGateReason = (typeof STUDENT_GATE_REASONS)[keyof typeof STUDENT_GATE_REASONS];

/** Required email domain (RC-06). Matched exactly, case-insensitive after lowercasing. */
export const CBU_EMAIL_SUFFIX = "@calbaptist.edu";

/** Minimum password length (RC-30). The password is never trimmed. */
export const PASSWORD_MIN_LENGTH = 8;

/** The claim key that marks an institution account (owner decision 2026-11-16). Presence with any value counts. */
export const INSTITUTION_CLAIM = "institution";
