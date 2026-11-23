# ADR 0006: Series, draft and seat layout

- **Status:** Accepted for #20, with provisional items below awaiting owner confirmation
- **Issue:** #20 (TMP-14), a contract Issue
- **Scope:** `series` and `seriesDrafts` documents, their Security Rules and indexes, the shared Pacific time helper. Request layout is #35/#26. (ADR 0005 is reserved for #21.)

## Decision
- `seriesDrafts/{draftId}`: server-written draft holding the host's `input`, its `inputHash` (SHA-256 of canonical JSON, the "reviewed" marker), the calculated `route` per weekday and direction, and `status` `reviewed` or `posted`. The host may `get` it; no list; no client write. Never deleted.
- `series/{seriesId}`, with `seriesId` equal to the draft id so posting is idempotent. One document holds the schedule, stored routes, `openSeats` per weekday (the seat source of truth, starting at `maxRiders`) and the search fields `status` and `openWeekdays`. Any consented student may get or list; no client write, host included (RC-12).
- Seats are a map on the series document, so a seat change, `openWeekdays` and status change in one transaction with the request (ARCHITECTURE §4).
- `lastDepartureAt` per weekday is computed once at posting, so the "trips left" check (RC-33) never recomputes dates or calls a service.
- After posting only `openSeats`, `openWeekdays` and `status` change (`SERIES_MUTABLE_FIELDS`). Status moves active to cancelled or active to ended; both terminal. `cancelledAt`/`endedAt` are left to #26.
- Time: `functions/src/shared/time`, Intl only, no new dependency. Search query uses indexes in `firestore.indexes.json`.

## Alternative rejected: a `seats` subcollection (one doc per weekday)
It would spread a series' seat state across documents, so one transaction touches several documents and the search needs a collection-group query or a copied field anyway. A map on the series document (at most 7 integers) keeps seat, search field and status updates in one document write and one read. Contention is per series, which the transaction serializes safely at MVP scale.

## Provisional, owner to confirm
1. The collection layout above (`seriesDrafts` auto id, `series` with `seriesId == draftId`).
2. `maxStopsPerDirection` is 25 (Routes API waypoint limit), a technical cap not in the SDD. **Note 2026-11-23:** the owner decided the cap should be **10** (Routes Essentials covers up to 10 intermediates; the higher rate starts after 10). The code and data-model still say 25 until contract-change Issue #143 (Human Review Pending) lands; then this item, ADR 0007 provisional item 2 and data-model §2.10 change to 10.
3. A nonexistent spring-forward local time (for example 02:30 on 2026-03-08) shifts forward to the equivalent after the gap (10:30Z); a repeated fall-back time uses the earlier, PDT instant (like java.time).
4. A selected weekday with no date in range is allowed; it has no `lastDepartureAt` key and never has trips left.

## Open item for later
RC-32 open item 3 (do weekdays with no trips left still block a student's schedule?) may need `lastDepartureAt` in #13's `ScheduleEntry`. That would be a separate contract-change Issue; #13's contract is unchanged here.
