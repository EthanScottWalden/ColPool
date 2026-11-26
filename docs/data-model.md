# ColPool data model (core schema)

> **Contract #13 (TMP-04).** Changes only through a separate, reviewed contract-change Issue.
> Decisions and rejected alternatives: [ADR 0002](adr/0002-firestore-layout.md).
> Code: `functions/src/shared/schema/` (names, types, limits, key builders, option seeds). The Android Kotlin models must mirror these names exactly.
> Series, draft and request collections are in §2.10-2.12. Weekday rosters are planned (§2.10), contract Issue #166. Notifications are in §2.9 (#21).

Conventions:
- Document ids for per-student docs are the Auth uid.
- **Backend** = Cloud Functions through the Admin SDK (bypasses rules). **Owner** = the signed-in student the doc belongs to. A client write path exists only where a table says "owner".
- Optional fields that are not provided are **absent**, never `null`. "Not provided" is different from "Prefer not to say".
- Times are Firestore server timestamps. Calendar dates in schedule entries are local America/Los_Angeles `YYYY-MM-DD` strings.
- No field exists for any RC-03 category, and no photo or media field exists (RC-08).
- Institution accounts have no profile or student-doc access in the app (RC-07).

## 1. Collections at a glance

| Collection / doc | Writer | Readers | Notes |
|---|---|---|---|
| `users/{uid}` | backend | owner | Account record. |
| `privateDetails/{uid}` | owner (limited keys) | owner | Starting address (RC-05). |
| `profiles/{uid}` | backend creates at sign-up; owner edits | owner; RC-01 viewers | Public profile. |
| `consents/{uid}` | backend, once | none (rules use `exists()`) | Immutable. |
| `profileAccess/{ownerId}` | backend | none from the app | Rules read it via `get()`. |
| `schedules/{uid}` | backend | owner | RC-32 lookup. |
| `campusPoints/{pointId}` | seed script | consented students | Never deleted. |
| `options/profile` | seed script | consented students | One doc, five lists. |
| `series/{seriesId}` | backend | any consented student | Posted series with stored routes and seats (§2.10). |
| `seriesDrafts/{draftId}` | backend | its host (get only) | Reviewed draft with calculated routes (§2.11). |
| `requests/{seriesId}_{riderUid}` | backend | its rider and the series' host | One request per rider and series (§2.12). |
| `users/{uid}/notifications/{id}` | backend | owner (may flip `read` to true) | Server-rendered notification (§2.9). |

"Consented student" means `exists(/databases/$(database)/documents/consents/$(request.auth.uid))` and not an institution account.

## 2. Documents

### 2.1 `users/{uid}`
| Field | Type | Notes |
|---|---|---|
| `email` | string | Lowercased, copied from Auth. Owner-readable only (RC-01, RC-05). |
| `createdAt` | timestamp | Server time. |

### 2.2 `privateDetails/{uid}`
| Field | Type | Notes |
|---|---|---|
| `startingAddress.text` | string | Display text of the chosen Places suggestion. |
| `startingAddress.placeId` | string | Required (RC-13). |
| `startingAddress.lat`, `.lng` | number | Required (RC-13). |
| `updatedAt` | timestamp | |

The owner may write only these keys. Required for a student to leave onboarding (POLICY §3.1: can be edited, never removed). Used only to rank search (RC-05, RC-19).

### 2.3 `profiles/{uid}`: every SDD §2.2 profile field
| Field | Type | Required | Limit / source | RC-30 / RC-22 |
|---|---|---|---|---|
| `firstName` | string | yes | trimmed, non-blank, at most 30 chars | RC-30 |
| `lastName` | string | yes | trimmed, non-blank, at most 30 chars | RC-30 |
| `age` | integer | no | whole number, 18 or older, no upper bound | RC-30 |
| `gender` | string id | no | in `options/profile.genderIds`; "Prefer not to say" is the id `prefer-not-to-say` | RC-22 |
| `year` | string id | no | in `yearIds` | RC-22 |
| `major` | string id | no | in `majorIds` (CBU program list from the owner) | RC-22 |
| `interests` | string[] of ids | no | subset of `interestIds`, no duplicates, capped only by list size; non-empty if present | RC-22 |
| `traits` | string[] of ids | no | subset of `traitIds`, no duplicates, capped only by list size; non-empty if present | RC-22 |
| `aboutMe` | string | no | at most 300 chars, text only | RC-30 |
| `updatedAt` | timestamp | yes | server time | |

The starting address is **not** here; it is in `privateDetails` (§2.2). Email is **not** here; it is in `users` (§2.1). The profile stores ids, never labels.

RC-30 constants (code: `PROFILE_LIMITS`; a test checks this table against the code):

| Constant | Value | Applies to |
|---|---|---|
| `firstNameMax` | 30 | `firstName` |
| `lastNameMax` | 30 | `lastName` |
| `aboutMeMax` | 300 | `aboutMe` |
| `ageMin` | 18 | `age` |

Other RC-30 limits (riders, price, stop minutes, vehicle) are in §2.10. The password limit belongs to the sign-up function (#14).

### 2.4 `consents/{uid}`
| Field | Type | Notes |
|---|---|---|
| `agreedAt` | timestamp | Server time, written once with the account (RC-06). No statement version for the MVP. Immutable, no client write, never deleted (RC-24). |

### 2.5 `profileAccess/{ownerId}`
Backend-only. One doc per student whose profile may be viewed. Reasons are **sets**, so every write is idempotent and safe to retry. Writers use blind merge writes (`set(..., {merge: true})`) with `FieldValue.delete()` to remove a reason. Never use dotted-path `update()` on these maps: uids can contain `-` and reason keys contain `:`, which need quoting in field paths. An emptied map means no access; entries are emptied, never counted.

```
{
  activeHostedSeries: { <seriesId>: true },
  viewers: { <viewerUid>: { <reasonKey>: true } }
}
```

Reason keys (builders in `keys.ts`): `activeHostedSeries` entry (case 1); `reqReason(seriesId)` = `req:<seriesId>` (cases 2 and 3); `coReason(seriesId, weekday)` = `co:<seriesId>:<weekday>` (case 4).

Rule of access to `profiles/{ownerId}` by a viewer: the owner, **or** (consented, not institution, and (`activeHostedSeries` non-empty, or `viewers[viewerUid]` non-empty)).

#### RC-01 cases mapped to `profileAccess`

| Case | Who sees whose profile | Stored as | Starts when | Ends when |
|---|---|---|---|---|
| 1 | Any student sees an active host | `profileAccess/{host}.activeHostedSeries[seriesId]` | Series is posted | Series is cancelled or ends |
| 2 | Host sees a requester | `profileAccess/{requester}.viewers[host][req:<seriesId>]` | Request is sent | Request has no pending or accepted weekday (declined, withdrawn, left, removed), or series is cancelled or ends |
| 3 | Requester or rider sees the host | `profileAccess/{host}.viewers[requester][req:<seriesId>]` | Request is sent | Same as case 2 |
| 4 | Co-riders see each other | `profileAccess/{A}.viewers[B][co:<seriesId>:<weekday>]` and the mirror `profileAccess/{B}.viewers[A][...]` | Both are accepted on the same weekday of the same series | Either leaves, is removed or is declined on that weekday, or series is cancelled or ends |

Notes:
- Cases 2 and 3 are symmetric: the host's doc lists the requester and the requester's doc lists the host, under the same key.
- **Owner decision (2026-11-14):** host and requester visibility ends when no weekday of the request is pending or accepted, or when the series is cancelled or ends. This matches RC-01 "pending or accepted request".
- Case 3 is stored explicitly even though case 1 usually covers it: case 1 ends when the series ends, and case 3 ends only when the request stops holding. Both lines of RC-01 stay independently true.
- A viewer can hold several reasons at once. Access ends only when the last one is removed ("unless another case still applies").
- The co-rider reasons are per weekday, so removing one weekday leaves a co-rider relationship that still holds on another.

### 2.6 `schedules/{uid}` (RC-32 lookup)
Backend-only; owner read. One doc per student listing every weekday they host, or have pending or accepted as a rider.

```
entries: {
  "<seriesId>_<weekday>": {
    seriesId, weekday,                       // weekday: mon | tue | wed | thu | fri | sat | sun
    role:  "host" | "rider",
    state: "hosting" | "accepted" | "pending",
    startDate, endDate                       // local Pacific YYYY-MM-DD
  }
}
```

- Key builder: `scheduleEntryKey(seriesId, weekday)`.
- `role: host` goes with `state: hosting`; `role: rider` goes with `accepted` or `pending`. Declined, withdrawn, left, removed, cancelled and ended weekdays have **no entry** (the entry is deleted with `FieldValue.delete()`).
- Maintained **in the same transaction** as every lifecycle change: post, request, accept, decline (including automatic declines), remove, leave, withdraw, cancel and the end-of-series job.
- **Serialization:** a conflict check reads the student's `schedules/{uid}` inside the transaction, so two concurrent accepts for the same rider both read and write that doc. Firestore transactions on one doc serialize, so the second accept sees the first and the RC-32 auto-decline stays atomic. This is deliberate and is the reason the lookup is a single doc.
- Open RC-32 edge semantics are listed in ADR 0002 and are decided by the Issue that implements the conflict check.

### 2.7 `campusPoints/{pointId}`
| Field | Type | Notes |
|---|---|---|
| `name` | string | |
| `lat`, `lng` | number | Owner-provided pins; see the table below. |
| `placeId` | string, optional | |
| `order` | integer | Display order. |
| `active` | boolean | Points are never deleted (RC-24 spirit); set `false` instead. |

Seeded by a developer script (`functions/scripts/seed-reference-data.ts`; DEVELOPMENT.md "Seeding reference data"); students read only (RC-11). The seed omits `placeId` and sets `active: true`. The script never deletes: to retire a point, set `active: false`. The ids live in `functions/src/shared/schema/campusPoints.ts`.

Coordinates source: owner-provided Google Maps pins, 2026-11-16 (verbatim, not rounded).

| id | name | order | lat | lng |
|---|---|---|---|---|
| `yeager-center` | Yeager Center | 1 | 33.9291442351809 | -117.42722312301498 |
| `music-building` | Music Building | 2 | 33.92768415162394 | -117.42566509459762 |
| `health-science` | Health Science | 3 | 33.9248353057463 | -117.42887115540034 |
| `lancer-plaza` | Lancer Plaza | 4 | 33.92874664207741 | -117.42216055893344 |
| `events-center` | Events Center | 5 | 33.927733947753204 | -117.42333726656787 |

Reference addresses (owner-provided 2026-11-16; not stored in the seed): main campus, 8432 Magnolia Ave, Riverside, CA 92504. Health Science campus, 3532 Monroe St #6322, Riverside, CA 92504.

### 2.8 `options/profile`
One doc holds all five predefined lists (RC-22). Each entry is `{ id, label, order }`, with a parallel id array per list for rules.

| Lists | Id arrays |
|---|---|
| `genders` | `genderIds` |
| `years` (ordered: freshman, sophomore, junior, senior, graduate; RC-21 uses closeness) | `yearIds` |
| `majors` (CBU program list, 127 entries, alphabetical) | `majorIds` |
| `interests` | `interestIds` |
| `traits` (commute style) | `traitIds` |

Values come from the prototype (24-edit-profile) with stable kebab-case ids; see `options.ts`. Rules read the doc with one `get()` and only when a write changes a list-backed field. `buildProfileOptionsDoc()` derives the id arrays from the entries so they cannot drift.

### 2.9 `users/{uid}/notifications/{notificationId}` (contract #21, [ADR 0005](adr/0005-notifications.md))
One doc per notification, in the recipient's own subcollection. Created only through `writeNotification` (`functions/src/shared/notifications/`), inside the transaction or batch that caused the event. Layout confirmed final by the owner 2026-11-26 (ADR 0005 items 1-3); wording is provisional and affects new docs only.

| Field | Type | Notes |
|---|---|---|
| `type` | string | `request_new`, `day_accepted`, `day_declined`, `day_auto_declined_full`, `day_auto_declined_conflict`, `rider_removed`, `rider_left`, `request_withdrawn`, `series_cancelled`, `series_ended` |
| `seriesId` | string | Related series. |
| `weekdays` | string[] | Optional. Weekday ids, Monday first. One id for per-weekday events, one or more for `request_new`. Absent on series events. |
| `title`, `body`, `seriesLine` | string | Server-rendered, frozen at event time. `seriesLine` is "[Host first name]'s series from [pickup spot]". |
| `createdAt` | timestamp | Server timestamp. |
| `read` | bool | `false` at create. |

Ids are deterministic so a repeated event fails with ALREADY_EXISTS instead of duplicating: `${type}_${seriesId}` for `series_cancelled` and `series_ended`; `request_new_${seriesId}_${riderUid}`; `${type}_${seriesId}_${weekday}` for the four day decisions and `rider_removed`; `${type}_${seriesId}_${riderUid}_${weekday}` for `rider_left` and `request_withdrawn`. Leaving or withdrawing several days writes one notification per weekday. No actor uid is stored. Text never contains addresses, emails or free text.

Rules: the recipient (a consented student) may get and list; update may change only `read`, only to `true`; create and delete are denied; no collection-group rule.

### 2.10 `series/{seriesId}` (contract #20, [ADR 0006](adr/0006-series-draft-seat-layout.md))
`seriesId` equals the draft id, so posting twice cannot create two series. Written by functions only; any consented student may get or list. Layout confirmed final by the owner 2026-11-26 (ADR 0006): `seriesDrafts` auto id, `series.id == draftId`, abandoned drafts stay `reviewed`. Later work only adds optional fields (`cancelledAt`, `endedAt`). Planned, contract Issue #166 (before #40): weekday rosters at `series/{seriesId}/rosters/{weekday}` = `{hostId, riderIds: string[]}` (uids only, at most 6), created lazily on the first accept inside the seat transaction, readable by the consented host or a listed rider while the series is active. No code or Rules yet.

| Field | Type | Notes |
|---|---|---|
| `hostId` | string | Host uid. No host name copy; readers use `profiles` through RC-01. |
| `status` | `"active"` \| `"cancelled"` \| `"ended"` | active to cancelled and active to ended only; both terminal. Never deleted (RC-24). |
| `pickupSpot` | `{text, placeId, lat, lng}` | Chosen Places suggestion (RC-13). |
| `startDate`, `endDate` | `PacificDate` (`YYYY-MM-DD`) | America/Los_Angeles dates. |
| `priceDollars` | integer | 1 to 99 (RC-30). |
| `vehicle` | string | At most 60 chars (RC-30). |
| `maxRiders` | integer | 1 to 6 (RC-30). |
| `weekdays` | `Weekday[]` | Monday first. |
| `days` | map weekday to `{toCampus: Leg, fromCampus: Leg}` | Only selected weekdays. |
| `openSeats` | map weekday to integer | Seat source of truth; starts at `maxRiders`. |
| `openWeekdays` | `Weekday[]` | Weekdays with `openSeats` above 0 (search field); changes with `openSeats`. |
| `lastDepartureAt` | map weekday to timestamp | Last departure from the pickup spot on or before `endDate`, computed at posting. Key absent if no date in range. |
| `inputHash` | string | SHA-256 of the reviewed input (matches the draft). |
| `postedAt` | timestamp | Server time. |

`Leg` = `{departTime: "HH:mm", campusPointId, stops: [{address: {text, placeId, lat, lng}, minutes}], route: RouteResult}`. Stop `minutes` is 0 to 120 (RC-30), at most 10 stops per direction (owner decision 2026-11-23, #143).
`RouteResult` = `{legs: [{meters, durationSec}], totalMeters, totalDurationSec, times: [{arriveMin?, departMin?}]}`; times are minutes after local midnight and may exceed 1439.

Created by the post-series function in one transaction with the draft update, the host's `schedules` entries and `profileAccess.activeHostedSeries` ([ADR 0009](adr/0009-post-series.md)).

After posting only `SERIES_MUTABLE_FIELDS` (`openSeats`, `openWeekdays`, `status`) change, and only from functions. #26 adds `cancelledAt` and `endedAt`. Routes never change (RC-12).

Constants (code: `SERIES_LIMITS`; a test checks this table against the code):

| Constant | Value | Applies to |
|---|---|---|
| `ridersMin` | 1 | `maxRiders` |
| `ridersMax` | 6 | `maxRiders` |
| `priceDollarsMin` | 1 | `priceDollars` |
| `priceDollarsMax` | 99 | `priceDollars` |
| `vehicleMax` | 60 | `vehicle` |
| `stopMinutesMin` | 0 | `stops[].minutes` |
| `stopMinutesMax` | 120 | `stops[].minutes` |
| `maxActiveHosted` | 5 | Active hosted series per student (RC-31) |
| `maxStopsPerDirection` | 10 | Owner decision 2026-11-23 (#143): Routes API Essentials covers at most 10 intermediate waypoints; stops are the only intermediates |

Time helper (`functions/src/shared/time`): "trips left" for a weekday is true when `now` is strictly before `lastDepartureAt[weekday]`. A selected weekday with no date in range has no key and never has trips left.

### 2.11 `seriesDrafts/{draftId}`
Written by the route-preview and post functions. The host may `get` their own draft; no list. Never deleted.

| Field | Type | Notes |
|---|---|---|
| `hostId` | string | |
| `input` | `SeriesInput` | Everything the host entered; no route values. |
| `inputHash` | string | SHA-256 of canonical JSON (sorted keys) of `input`: the "reviewed" marker. Post checks it still matches. |
| `route` | map weekday to `{toCampus, fromCampus}` `RouteResult` | Calculated once per weekday and direction. |
| `reviewedAt`, `createdAt`, `updatedAt` | timestamp | Server time. |
| `status` | `"reviewed"` \| `"posted"` | |
| `postedAt` | timestamp, optional | |

Post series (#30, [ADR 0009](adr/0009-post-series.md)) sets `status: "posted"` and `postedAt`.

Route preview (#28, [ADR 0008](adr/0008-route-preview.md)) writes it in one Firestore transaction with no external calls inside it:
- **Create** (no draft id sent): auto id, `status: "reviewed"`, all three timestamps set to server time.
- **Overwrite** (draft id sent, still `reviewed` and owned by the caller): replaces `input`, `inputHash`, `route`, `reviewedAt` and `updatedAt`; `createdAt` and `hostId` are kept. A posted draft or another host's draft is never touched (the caller gets `failed-precondition` or `not-found`).
- A failed preview (invalid input, no route, campus-time check, Routes down) writes nothing. Nothing is deleted.

**Expected times (`RouteResult.times`)**, computed once at review and stored; the Android app uses the same rule if it ever needs to show a time:
- Each hop is rounded on its own: `legMin = Math.round(durationSec / 60)` (a half minute rounds up). Rounding the total instead would give different times.
- The first point has `departMin` = the leg's `departTime` in minutes after local midnight. Each later point has `arriveMin` = previous `departMin` + previous `legMin`. At a stop, `departMin` = `arriveMin` + that stop's `minutes`. The last point has only `arriveMin`.
- Values above 1439 are kept (a trip can cross midnight); the campus-departure check rejects an arrival past midnight.
- `legs[].durationSec` is the Routes value as returned (may be fractional); totals are plain sums.

### 2.12 `requests/{seriesId}_{riderUid}` (#35, [ADR 0010](adr/0010-requests.md))
Backend written by `sendRequest`; the rider and the series' host may `get` and `list` (queries must filter on `riderId` or `hostId`). No client write. Never deleted. The id is the one-request-per-rider-and-series guarantee (RC-15). Layout, id, `status` and `createdAt` confirmed final by the owner 2026-11-26 (ADR 0010). Later work (#26) only adds optional fields: a per-weekday status enum and timestamps; no derived query fields (filter `days` in memory).

| Field | Type | Notes |
|---|---|---|
| `seriesId` | string | |
| `riderId` | string | |
| `hostId` | string | Copied from the series so rules need no second read. |
| `weekdays` | `Weekday[]` | Monday-first, the weekdays asked for. |
| `days` | map weekday to `{status}` | `status` is `"pending"` at creation. Not indexed. |
| `createdAt` | timestamp | Server time. |

No names or other personal data are stored. Sending also writes `schedules/{rider}.entries` (role `rider`, state `pending`, one per weekday), `profileAccess` for RC-01 cases 2 and 3 (`req:<seriesId>`), and one `request_new` notification for the host, in one transaction. Seats are not changed until the host accepts (#40).

## 3. Indexes
`firestore.indexes.json` carries single-field index exemptions for `profileAccess.viewers`, `profileAccess.activeHostedSeries`, `schedules.entries`, `series.days`, `series.openSeats`, `series.lastDepartureAt`, `requests.days`, `seriesDrafts.input` and `seriesDrafts.route`. These maps have per-uid, per-series or per-weekday keys, so automatic indexing would create index entries that no query uses.

No index is needed for notifications: the unread count uses `where read == false` and history uses `orderBy createdAt`. "Unread newest first" in one query would need a composite index on `read` and `createdAt` (#25).

Composite indexes (collection scope):
- `series`: `status` ascending, `openWeekdays` array-contains. The search query: `status == "active"` and `openWeekdays array-contains-any [...]`.
- `series`: `hostId` ascending, `status` ascending. A host's active series (RC-31 count, hosted list).
- `requests`: `riderId` ascending, `createdAt` descending. A rider's requests, newest first.
- `requests`: `hostId` ascending, `seriesId` ascending, `createdAt` ascending. A host's requests for one series.
