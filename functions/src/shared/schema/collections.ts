// Firestore collection and document names (contract #13, docs/data-model.md).
// The Android Kotlin models must mirror these names exactly.

export const COLLECTIONS = {
  users: "users",
  privateDetails: "privateDetails",
  profiles: "profiles",
  consents: "consents",
  profileAccess: "profileAccess",
  schedules: "schedules",
  campusPoints: "campusPoints",
  options: "options",
  series: "series",
  seriesDrafts: "seriesDrafts",
} as const;

export type CollectionName = (typeof COLLECTIONS)[keyof typeof COLLECTIONS];

/** The single option-list document: `options/profile`. */
export const OPTIONS_PROFILE_DOC_ID = "profile";
