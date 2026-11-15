# ColPool data model (core schema)

> **Contract #13 (TMP-04).** Changes only through a separate, reviewed contract-change Issue.
> Decisions and rejected alternatives: [ADR 0002](adr/0002-firestore-layout.md).
> Code: `functions/src/shared/schema/` (names, types, limits, key builders, option seeds). The Android Kotlin models must mirror these names exactly.
> Series, draft, request and roster collections are not here: #20 and #44 own them. Notifications are #21.

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

Other RC-30 limits (riders, price, stop minutes, vehicle) belong to the series contract (#20). The password limit belongs to the sign-up function (#14).

### 2.4 `consents/{uid}`
| Field | Type | Notes |
|---|---|---|
| `agreedAt` | timestamp | Server time, written once with the account (RC-06). No statement version for the MVP. Immutable, no client write, never deleted (RC-24). |

### 2.5 `profileAccess/{ownerId}`
Backend-only. One doc per student whose profile may be viewed. Reasons are **sets**, so every write is idempotent and safe to retry. Writers use blind merge writes (`set(..., {merge: true})`) with `FieldValue.delete()` to remove a reason. An emptied map means no access; entries are emptied, never counted.

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
| `lat`, `lng` | number | Precise coordinates come from the owner (#17). |
| `placeId` | string, optional | |
| `order` | integer | Display order. |
| `active` | boolean | Points are never deleted (RC-24 spirit); set `false` instead. |

Seeded by a developer script; students read only (RC-11).

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

## 3. Indexes
`firestore.indexes.json` carries single-field index exemptions for `profileAccess.viewers`, `profileAccess.activeHostedSeries` and `schedules.entries`. These maps have per-uid and per-series keys, so automatic indexing would create an index entry per key that no query uses. Search indexes belong to #20.
