// Weekday identifiers used in schedule entries and reason keys.
// Lowercase three-letter English abbreviations. The series contract (#20)
// reuses this type; changing it is a contract change.

export const WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;

export type Weekday = (typeof WEEKDAYS)[number];

export function isWeekday(value: unknown): value is Weekday {
  return typeof value === "string" && (WEEKDAYS as readonly string[]).includes(value);
}
