# ADR 0002: Core Firestore layout and profile-permission model

- **Status:** Accepted (owner decisions, 2026-11-14)
- **Issue:** #13 (TMP-04), a contract Issue
- **Scope:** collections and shapes for accounts, consent, profiles, private details, profile permissions, the RC-32 schedule lookup, campus points and option lists. Series, drafts, requests and rosters stay with #20 and #44. Field-level detail is in [`docs/data-model.md`](../data-model.md); the code is `functions/src/shared/schema/`.
- **Not in this Issue:** `firestore.rules` stays default deny. Baseline rules ship with #18. The rule shapes below are the intent only.

## Decisions

### 1. Consent check reads the consent record
Rules treat a student as consented iff `exists(/databases/$(database)/documents/consents/$(request.auth.uid))`. There is no consent custom claim.

- **Rejected: a consent custom claim.** Claims live in the ID token, which can be up to an hour stale, and the sign-up function would have to set it as a second step after creating the consent record, so a partial failure could leave the two disagreeing. RC-06 says the record is the truth. The claim would also be a second thing to keep in step.
- Cost: one `exists()` per rules evaluation that needs consent. A profile save costs at most 2 lookups (consent and options), usually 1.

### 2. Consent record
`consents/{uid}` holds only `agreedAt` (server timestamp). No statement version for the MVP. Immutable, no client write, never deleted (RC-24).

### 3. Profile permissions: one backend-only doc per viewed student
`profileAccess/{ownerId}`: `{ activeHostedSeries: { <seriesId>: true }, viewers: { <viewerUid>: { <reasonKey>: true } } }`.

- **Reasons are sets, not counters.** Writes are idempotent and safe to retry (a retried function cannot double-count). Writers use blind merge writes with field deletes; entries are emptied, never counted.
- Reason keys: case 1 is an `activeHostedSeries` entry; cases 2 and 3 are `req:<seriesId>`, symmetric (host's doc lists the requester, the requester's doc lists the host); case 4 is `co:<seriesId>:<weekday>`, symmetric between co-riders accepted on the same weekday of the same series. Builders: `reqReason`, `coReason`.
- **Owner decision:** host and requester visibility ends when no weekday of the request is pending or accepted, or when the series is cancelled or ends.
- Case 3 is stored explicitly even though case 1 usually covers it.
- Intended rule shape for reading `profiles/{ownerId}`: the owner, **or** (consented, not institution, and (`activeHostedSeries` non-empty, or `viewers[viewerUid]` non-empty)). One `get()` of `profileAccess/{ownerId}` plus the consent `exists()`.
- **Rejected: per-pair permission docs** (one doc per viewer and owner). They would need a query or many docs to answer "who may see me", and case 1 would fan out to every student whenever a series is posted or ends.
- **Rejected: per-series profile copies.** Copies go stale when a student edits their profile and spread private data across docs, so every edit would need fan-out writes.
- **Rejected: query-time permission checks** (rules querying series and requests). Rules cannot query, only `get()` known paths.

### 4. RC-32 lookup: one schedule doc per student
`schedules/{uid}.entries["<seriesId>_<weekday>"] = { seriesId, weekday, role, state, startDate, endDate }` with local Pacific `YYYY-MM-DD` dates. Maintained in the same transaction as every lifecycle change (post, request, accept, decline including automatic declines, remove, leave, withdraw, cancel, end job). Owner read only, no client write.

- **Concurrency:** concurrent accepts for one rider serialize on this doc. Each accept reads the rider's schedule doc in its transaction and writes it, so Firestore transaction retry makes the second see the first. That is what keeps the RC-32 auto-decline atomic.
- **Rejected: query-time RC-32 checks** (querying every series and request on each check). It is slow, needs many indexes, cannot be made atomic with an accept, and misses concurrency.
- Recorded in ARCHITECTURE §2.

#### RC-32 edge semantics: OPEN
These are **not settled here**. The Issue that implements the conflict check (request, accept, post) must decide them, with the product owner where SPEC is silent. The schedule doc stores enough (dates, weekday, state) to support any answer.
1. **Posting while holding a conflicting pending weekday.** RC-32 blocks posting on a weekday the host is *accepted* on as a rider. SPEC does not say what happens if the host has only a *pending* request on that weekday.
2. **Date-range overlap.** Whether overlap counts only on actual shared weekdays within the range, or the whole date ranges overlapping, when the two series have different weekdays. (SPEC §4.1: "both run on it and their date ranges overlap".)
3. **A weekday with no trips left (RC-33)** that is still pending or accepted: whether it still blocks a conflicting weekday.

### 5. Option lists in one doc
`options/profile` holds `genders`, `years`, `majors`, `interests`, `traits`, each entry `{id, label, order}`, plus a parallel id array per list (`genderIds`, ...) for rules.

- Rules read it with one `get()`, only when a write changes a list-backed field (short-circuit on `request.resource.data.diff(resource.data).affectedKeys().hasAny(['gender','year','major','interests','traits'])`; for a create, check the keys present). A profile save costs at most 2 lookups (consent and options), usually 1.
- Profiles store stable ids, never labels. Years are ordered (RC-21 uses closeness).
- Values are the prototype's gender, year, interests and trait lists, with stable kebab-case ids. **Majors** are the CBU program list as provided by the owner 2026-11-14 (126 entries, alphabetical, verbatim labels, kebab-case ids): online labels, degree types and concentrations removed, and minor-, certificate- and credential-only subjects excluded. The prototype's sample majors are not used (POLICY §3.1).
- **Rejected: option ids baked into rules.** A list change would need a rules deploy and test cycle, and rules would drift from the data the app displays.
- **Rejected: five option docs.** Five lookups on a profile save instead of one, and the 10-lookup rules budget per request would be consumed fast once other lookups are added.

### 6. Limits and field behavior
No new product limits. `firstName` and `lastName` are required, max 30 (RC-30). **L1 refinement:** they are trimmed and must be non-blank after trimming. `age` is optional, an integer at least 18, no upper bound. `interests` and `traits` have no duplicates, are a subset of their list and are capped only by list size. `aboutMe` is optional, max 300, text only. Removed optional fields are absent (deleted), never `null`; "not provided" is distinct from "Prefer not to say". No field for any RC-03 category, and no photos (RC-08). Arrays, when present, are non-empty (L1: clearing the last chip deletes the field).

### 7. Other layout points
- `users/{uid}` (`email` lowercased from Auth, `createdAt`): backend written, owner read only.
- `privateDetails/{uid}` (`startingAddress {text, placeId, lat, lng}`, `updatedAt`): owner read and write, limited to those keys (RC-05).
- `profiles/{uid}`: created by the backend at sign-up (so it always exists, even before onboarding), then edited by the owner; readable by the owner and RC-01 viewers.
- `campusPoints/{pointId}` (`name, lat, lng, placeId?, order, active`): seeded, never deleted.
- Institution accounts get no profile access in the app.
- **Other students' profiles are read with a one-shot `get()`, not a listener.** The app cannot rely on a listener's behavior when permission is revoked mid-listen: how and when Firestore re-checks rules for an open listener is unverified, and RC-01 requires access to end immediately. A fresh `get()` always evaluates current rules. (Own profile can use a listener.)
- `firestore.indexes.json` has single-field index exemptions for the `profileAccess.viewers` and `activeHostedSeries` maps and for `schedules.entries`: their keys are per-uid or per-series, and nothing queries them.

## Consequences
- The Android Kotlin models must mirror these names and shapes. A later Issue should check them against the TypeScript constants in `functions/src/shared/schema/`.
- Weekday ids (`mon`..`sun`, `weekday.ts`) are fixed here because schedule keys need one. #20 must reuse that type; changing it is a contract change.
- The institution claim key remains #15's decision. Test helpers' `TODO(#13)` about consent is resolved by decision 1: a consented persona has a `consents/{uid}` doc, no claim.
- Eventual rules shape for #18: default deny, with owner reads of `users`, `privateDetails`, `schedules`; owner write to `privateDetails` (only listed keys) and `profiles` (RC-30 and option checks); consented, non-institution reads of `campusPoints` and `options/profile`; no client write to `consents`, `profileAccess`, `schedules`, `users`.
- Rules lookups per request are bounded (consent `exists`, `profileAccess` `get`, options `get`), well within Firestore's limits.
- The permission tables are only as correct as the transactions that maintain them (accepted architectural risk, ARCHITECTURE §9). #44 owns maintaining them through the lifecycle.
