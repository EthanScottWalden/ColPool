# ADR 0010: Requests and send-request

- **Status:** Accepted for #35; items marked PROVISIONAL await owner confirmation
- **Issue:** #35 (TMP-26)
- **Scope:** `requests/{seriesId}_{riderUid}`, the `sendRequest` callable (`functions/src/sendRequest.ts`), request Security Rules and indexes. Depends on [ADR 0002](0002-firestore-layout.md), [ADR 0005](0005-notifications.md), [ADR 0006](0006-series-draft-seat-layout.md), [ADR 0007](0007-shared-series-validation.md), [ADR 0009](0009-post-series.md).

## Layout (PROVISIONAL)
Top-level `requests/{seriesId}_{riderUid}`: `{seriesId, riderId, hostId, weekdays, days: {<wd>: {status: "pending"}}, createdAt}`. The deterministic id makes a second request by the same rider for the same series impossible (`tx.create` plus an existence check). `hostId` is copied from the series so one rule can check the host. No names are stored. `status` and `createdAt` are provisional until #26 settles the per-weekday status values and timestamps. Only `COLLECTIONS.requests` was added to the shared schema (additive); `request.ts` is new.

## Callable `sendRequest`
Region `us-west2`. Input `{seriesId, weekdays}`; output `{requestId}`. Student gate first (`requireStudent`).

| Case | Error (`details.reason`) |
|---|---|
| Series id not 20 alphanumerics | `invalid-argument` `series-id-invalid` |
| Weekdays empty, duplicated, unknown, or not in the series | `invalid-argument` `weekdays-invalid` |
| Series missing | `not-found` |
| Caller hosts it | `failed-precondition` `own-series` |
| Series not active | `failed-precondition` `series-not-active` |
| Request already exists for this rider and series (any weekdays) | `already-exists` `already-requested` |
| Any chosen weekday fails | `failed-precondition` `weekdays-unavailable`, `details.weekdays: [{weekday, reason: "full" \| "no-trips-left" \| "conflict"}]` |

One transaction, reads first: series, request, `schedules/{uid}`, both profiles. A weekday fails when `openSeats` is 0 (RC-33), `hasTripsLeft` is false (a missing `lastDepartureAt` key counts as none), or `conflictingWeekdays` (ADR 0007, shared ride date today or later, Pacific) finds an `accepted` or `hosting` schedule entry on it. Pending entries never block (RC-32). The whole request is rejected if any weekday fails. A series that already started can be requested.

Writes: the request; pending `rider` schedule entries; `profileAccess` for RC-01 case 2 (`viewers[host]["req:<seriesId>"]` on the rider) and case 3 (the mirror on the host); one `request_new` notification. Seats are untouched; accepting takes them (#40). Logs carry the uid and series id only. A missing profile (should not happen) is `internal`.

## Rules and indexes
`allow get, list` for a consented non-institution student whose uid is the request's `riderId` or `hostId`; client writes are default deny. Queries must filter on `riderId` or `hostId`. Indexes: `riderId` + `createdAt` desc; `hostId` + `seriesId` + `createdAt`; `requests.days` not indexed.

## PROVISIONAL, for owner confirmation
Top-level layout and id; field names `status`, `createdAt`; the error reason strings; rejecting the whole request when one weekday fails; pickup spot text as the notification's series name.
