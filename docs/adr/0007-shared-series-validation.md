# ADR 0007: Shared series validation

- **Status:** Accepted for #23, with provisional items below awaiting owner confirmation
- **Issue:** #23 (TMP-15), a contract Issue
- **Scope:** the one validation module for series input, RC-30 ranges, RC-31 hosting limits and RC-32 schedule conflicts at posting. Used by route preview (#28) and posting (#30); the weekday-conflict test is also reused by request (#35) and accept (#40). Code: `functions/src/shared/validation/`.

## Decision

### API
- `validateSeriesInput(raw: unknown, ctx: ValidationContext)` returns `{ ok: true, input: SeriesInput }` or `{ ok: false, errors }`. It parses untrusted callable data, returns **all** errors at once, and on success returns a clean, normalized input (vehicle trimmed, weekdays Monday-first).
- `validateCampusTimes(input, route)` returns errors. It runs only once route times exist (#28, #30). Per weekday, `fromCampus.departTime` (minutes) must be at least the last `toCampus.route.times[].arriveMin`; equal is allowed. A campus arrival past midnight (above 1439) fails. A weekday with no route yet is skipped.
- `validateHosting(span, ctx)` is the RC-31 and RC-32 part, exported so #30 can reuse it.
- `loadValidationContext(db, hostId, now, tx?)` returns `{ now, activeCampusPointIds, hostedSeries, schedule }`. Pass a `Transaction` as `tx` to read inside the posting transaction. It takes the `Firestore` as well because a Transaction has no public way back to its Firestore (this differs slightly from the plan's `Firestore | Transaction` signature). A missing `schedules/{uid}` doc is an empty schedule.
- Only `context.ts` touches Firestore; `errors.ts` imports `HttpsError` only for `seriesInvalidError`.
- `seriesInvalidError(errors)` is `HttpsError("invalid-argument", "series-invalid", { errors })`.

### Errors
`SeriesValidationError = { code, path, params? }`. `path` is a dotted path into `SeriesInput` (`days.tue.toCampus.stops.2.minutes`, `startDate`), `""` for the form as a whole. `params` holds only limits (`limit`, `min`, `max`) and weekday ids, never other users' data or series details. The backend sends codes only, no display text.

| Code | Meaning (path) |
|---|---|
| `required` | Field missing, blank, or an empty weekday list. |
| `notInteger` | Price, riders or stop minutes not a whole number. |
| `outOfRange` | Number outside its RC-30 range (`min`, `max`); an unknown weekday id in `weekdays`; a `days` key not in `weekdays`. |
| `tooLong` | Vehicle over 60 characters (`limit`). |
| `invalidAddress` | Address text or place id blank, or lat/lng not finite or out of range. |
| `invalidDate` | Not a real `YYYY-MM-DD` date. |
| `invalidTime` | Not `HH:mm`, 00:00 to 23:59. |
| `unknownCampusPoint` | Campus point not in the active list. |
| `tooManyStops` | Over `maxStopsPerDirection` (`limit`). |
| `duplicateWeekday` | A weekday chosen twice (`weekday`). |
| `startInPast` | `startDate` before today in Pacific, by the server clock. |
| `endBeforeStart` | `endDate` before `startDate`. |
| `noDateOnWeekdays` | None of the chosen weekdays has a date in range (`""`). One weekday without a date is fine when another has one. |
| `campusDepartureBeforeArrival` | See `validateCampusTimes` (`weekday`). |
| `hostingLimitReached` | Host already has 5 active series (`limit`, `""`). |
| `hostingWeekdayOverlap` | Shares a weekday with overlapping dates with another active series (`weekday`, `days.<wd>`). |
| `riderScheduleConflict` | Host is an accepted rider on that weekday with overlapping dates (`weekday`, `days.<wd>`). |

### RC-31 and RC-32 at posting
- RC-31: `series` where `hostId == uid` and `status == "active"` (#20's index), then drop any whose `endDate` is before today (Pacific), which covers the daily-job lag. Five or more remaining fails. Weekday overlap is checked against the same results.
- RC-32: the host's `schedules/{uid}` entries with `role` rider, `state` accepted and `endDate` on or after today.
- One shared test, `conflictingWeekdays` in `conflicts.ts`: see Owner decisions.

## Owner decisions
- **2026-11-23: RC-31/RC-32 conflict = a shared ride date on that weekday, today or later** (owner chose this over the literal "date ranges overlap" reading). Implemented once in `conflicts.ts` (`conflictingWeekdays(a, b, today)`), used by both `hostingWeekdayOverlap` and `riderScheduleConflict`, and reusable by #35 and #40. Consequences:
  - Two series conflict on weekday W only when some date on W, on or after today (Pacific, server clock), falls inside both date ranges.
  - Ranges overlapping only on days neither series rides do not conflict (S1 Mon/Wed Aug 24 to Fri Dec 11 and S2 Mon/Wed from Thu Dec 10 pass). Back-to-back ranges pass.
  - A weekday with no date in range never conflicts; once all shared dates are past it stops blocking. Trips left (RC-33) is not used.
  - A pending request does not block posting, and posting declines nothing. This settles ADR 0002 §4 items 1 to 3.

## Provisional, owner to confirm
These are lead defaults.
1. **Error wording** lives in the Android Issue; the backend sends codes only.
2. **`maxStopsPerDirection` = 25** is a technical cap carried over from ADR 0006. Owner decision 2026-11-23: it becomes 10; the code stays at 25 until contract-change Issue #143.
3. **Stops array required.** `stops` must be present (may be empty); an absent array is `required`.

## L1 notes
- RC-12 "no remaining date" is read as a date on or after today, so a series whose only trip departed earlier today can still be posted.
- Weekday-conflict reporting is one error per weekday, even when several series conflict.
- A `days` key that is not a selected weekday is `outOfRange`, so the `days` keys always match `weekdays` exactly.
- Hostile-input hardening (review): only plain objects are parsed; address `text` is capped at 500 characters and `placeId` at 300 (technical caps, not product rules); an oversized `stops` or `weekdays` array gets one error without per-element parsing; an unknown `days` key is reported once at `days` and never echoed into an error path.
