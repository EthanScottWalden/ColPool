# ADR 0009: Post series

- **Status:** Accepted for #30
- **Issue:** #30 (TMP-17)
- **Scope:** the `postSeries` callable. Code: `functions/src/postSeries.ts`. Depends on [ADR 0002](0002-firestore-layout.md) (`profileAccess`, `schedules`), [ADR 0006](0006-series-draft-seat-layout.md) (series layout and time helper), [ADR 0007](0007-shared-series-validation.md) (validation) and [ADR 0008](0008-route-preview.md) (the reviewed draft).

## Contract
- Callable `postSeries`, region `us-west2`, no secrets, no external calls.
- Input `{ draftId: string, series: <untrusted SeriesInput> }`; `draftId` matches `^[A-Za-z0-9]{20}$`. Output `{ seriesId, alreadyPosted }`; `seriesId` equals `draftId`.
- The app never sends routes, distances or times (RC-12): everything saved comes from the stored draft.

| Case | Error |
|---|---|
| Not signed in, institution account, no consent | `requireStudent`'s own errors |
| Malformed or missing draft id | `invalid-argument` `draft-id-invalid` |
| Draft missing **or** not the caller's | `not-found` (same answer for both) |
| Start date passed, no matching date, RC-31 limit, RC-32 conflict, inactive campus point, campus departure before arrival | `invalid-argument` `series-invalid` with #23 `errors` |
| Body no longer hashes to the reviewed `inputHash` | `failed-precondition`, `reason: "draft-changed"` |
| Draft not `reviewed` with no series, or a weekday missing a route leg | `internal` (logged with uid only) |

`draft-changed` is a new reason string defined in `postSeries.ts`; no shared contract changed.

## Order of checks (one `db.runTransaction`; `requireStudent` and the clock are outside)
1. Reads, before any write: draft, `series/{draftId}`, then `loadValidationContext(db, uid, now, tx)` (active campus points, the host's active series, `schedules/{uid}`).
2. Draft missing or not the caller's: `not-found`.
3. **Idempotency:** `series/{draftId}` exists and belongs to the caller: return `alreadyPosted: true`, write nothing.
4. Draft status must be `reviewed`, else `internal`.
5. `validateSeriesInput(body, ctx)`: start date today or later, a matching date, RC-31, RC-32, active campus points.
6. `hashSeriesInput(parsed.input) === draft.inputHash`, else `draft-changed`.
7. `validateCampusTimes(draft.input, draft.route)`; every selected weekday has both route legs.
8. Writes: create `series/{id}`; update the draft to `posted`; merge the host's `schedules/{uid}` entries; merge `profileAccess/{uid}.activeHostedSeries.{id}`.

## What is written
- `series`: `hostId`, `status: "active"`, the input fields, `days[wd]` = each leg plus its stored `route`, `openSeats[wd] = maxRiders`, `openWeekdays` = every selected weekday, `lastDepartureAt` from `computeLastDepartureAt` using each weekday's to-campus departure time (key absent when no date in range), `inputHash`, `postedAt`.
- `seriesDrafts/{id}`: `status: "posted"`, `postedAt`, `updatedAt`.
- `schedules/{uid}.entries[<id>_<wd>]`: `{seriesId, weekday, role: "host", state: "hosting", startDate, endDate}` per weekday (`scheduleEntryKey`).
- `profileAccess/{uid}.activeHostedSeries[<id>] = true`: every consented student may now read the host's profile (RC-01 case 1). `firestore.rules` is unchanged; a rules test checks allow and deny.

## Serialization
Every post reads and writes `schedules/{uid}`, so two posts by the same host contend on that document and run one after the other; the loser re-reads and sees the winner's series. That is what makes the RC-31 limit and the RC-32 overlap check safe under parallel posts. Two taps on the same draft contend on `series/{draftId}`, and the second returns `alreadyPosted`. Tests: parallel same-draft, parallel different drafts at 4 active, parallel drafts with a shared ride date.

## L1 notes
- A repeat tap returns the existing series before any re-hash or re-validation, so it still works after the start date passes.
- `openWeekdays` lists every selected weekday, including one with no date in range; search filters on trips left (`lastDepartureAt`).
- New reason `draft-changed`, above.

## Not in this Issue
Removing host visibility and the host's schedule entries when a series ends or is cancelled belongs to #46/#47 (lifecycle).
