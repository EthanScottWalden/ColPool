// RC-30 limits for profile input, plus the L1 refinements from ADR 0002.
// docs/data-model.md repeats these values; a test keeps the two in step.

export const PROFILE_LIMITS = {
  /** First name: required, trimmed, non-blank, up to 30 characters (RC-30). */
  firstNameMax: 30,
  /** Last name: required, trimmed, non-blank, up to 30 characters (RC-30). */
  lastNameMax: 30,
  /** About me: optional, up to 300 characters, text only (RC-30). */
  aboutMeMax: 300,
  /** Age: optional whole number, 18 or older, no upper bound (RC-30). */
  ageMin: 18,
} as const;
