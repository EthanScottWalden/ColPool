import type { OptionEntry, ProfileOptionsDoc } from "./types.ts";

// Seed values for `options/profile`. Taken from the prototype (24-edit-profile),
// with stable kebab-case ids. Profiles store ids, never labels.

function list(items: readonly (readonly [id: string, label: string])[]): OptionEntry[] {
  return items.map(([id, label], i) => ({ id, label, order: i + 1 }));
}

export const GENDER_OPTIONS: OptionEntry[] = list([
  ["woman", "Woman"],
  ["man", "Man"],
  ["non-binary", "Non-binary"],
  ["prefer-not-to-say", "Prefer not to say"],
]);

/** Ordered: RC-21 scores closeness of years, so `order` is the step. */
export const YEAR_OPTIONS: OptionEntry[] = list([
  ["freshman", "Freshman"],
  ["sophomore", "Sophomore"],
  ["junior", "Junior"],
  ["senior", "Senior"],
  ["graduate", "Graduate"],
]);

// TODO(owner: CBU majors list): the project owner supplies the real list
// (POLICY §3.1, #17). Do not use the prototype's sample majors.
export const MAJOR_OPTIONS: OptionEntry[] = [];

export const INTEREST_OPTIONS: OptionEntry[] = list([
  ["music", "Music"],
  ["coffee", "Coffee"],
  ["hiking", "Hiking"],
  ["gaming", "Gaming"],
  ["tech", "Tech"],
  ["podcasts", "Podcasts"],
  ["sports", "Sports"],
  ["fitness", "Fitness"],
  ["movies", "Movies"],
  ["reading", "Reading"],
  ["art", "Art"],
  ["cooking", "Cooking"],
  ["photography", "Photography"],
  ["theater", "Theater"],
  ["anime", "Anime"],
  ["cars", "Cars"],
  ["dance", "Dance"],
  ["volunteering", "Volunteering"],
]);

/** Commute-style traits. */
export const TRAIT_OPTIONS: OptionEntry[] = list([
  ["chatty", "Chatty"],
  ["quiet", "Quiet"],
  ["chill", "Chill"],
  ["friendly", "Friendly"],
  ["music-friendly", "Music-friendly"],
  ["no-music", "No music"],
  ["early-bird", "Early bird"],
  ["podcast-friendly", "Podcast-friendly"],
  ["snack-friendly", "Snack-friendly"],
  ["phone-calls-ok", "Phone calls OK"],
]);

const ids = (entries: readonly OptionEntry[]): string[] => entries.map((e) => e.id);

/** The seed document, with the parallel id arrays derived so they cannot drift. */
export function buildProfileOptionsDoc(): ProfileOptionsDoc {
  return {
    genders: GENDER_OPTIONS,
    genderIds: ids(GENDER_OPTIONS),
    years: YEAR_OPTIONS,
    yearIds: ids(YEAR_OPTIONS),
    majors: MAJOR_OPTIONS,
    majorIds: ids(MAJOR_OPTIONS),
    interests: INTEREST_OPTIONS,
    interestIds: ids(INTEREST_OPTIONS),
    traits: TRAIT_OPTIONS,
    traitIds: ids(TRAIT_OPTIONS),
  };
}
