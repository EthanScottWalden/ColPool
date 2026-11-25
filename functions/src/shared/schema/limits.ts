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

// RC-30 and RC-31 limits for series input (contract #20). docs/data-model.md §2.10
// repeats these values; a test keeps the two in step.
export const SERIES_LIMITS = {
  /** Riders per series: whole number 1 to 6 (RC-30). */
  ridersMin: 1,
  ridersMax: 6,
  /** Price per rider per trip: whole dollars 1 to 99 (RC-30). */
  priceDollarsMin: 1,
  priceDollarsMax: 99,
  /** Vehicle description: up to 60 characters (RC-30). */
  vehicleMax: 60,
  /** Minutes at one stop: whole number 0 to 120 (RC-30). */
  stopMinutesMin: 0,
  stopMinutesMax: 120,
  /** Active hosted series per student (RC-31). */
  maxActiveHosted: 5,
  /** Stops per direction. Owner decision 2026-11-23: keeps every route preview within the Routes API Essentials tier (max 10 intermediate waypoints; ADR 0006, #143). */
  maxStopsPerDirection: 10,
} as const;
