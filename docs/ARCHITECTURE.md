# ColPool — Architecture

> **Status:** Draft v0.1 (2026-11-07). Derived from SDD v0.4 (`SPEC.md`) and the Implementation Plan (`INTENT.md`).
> **Authority:** `SPEC.md` wins on product behavior and rules; `INTENT.md` wins on sequencing and agent guardrails. This file summarizes the architecture those documents approve. If this file disagrees with them, they win and this file is fixed. If an issue seems to conflict with any of them, **stop and flag it** — don't pick an answer.
> **Scope:** Architecture only. Feature tasks live in GitHub Issues; domain policies will live in `docs/POLICY.md`.

---

## 1. Purpose at a glance

ColPool is an **Android-only** app that lets commuting students at **one university (CBU)** set up recurring round-trip carpools ("ride series") between a pickup spot and campus, and lets university staff **export a CSV of scheduled trips**. (SDD §2.1, §2.2)

```
Android app (Kotlin) ──► Firebase Auth        sign in only
        │      ├────────► Cloud Firestore     read permitted data; write own profile; mark notifications read
        │      └────────► Cloud Functions     every trusted action (callable, with auth token)
        └──────────────► Google Places        address autocomplete (on device)

Cloud Functions ──► Firestore (admin) · Auth (admin) · Google Routes API (review only)
```

The authoritative diagram is **SDD Figure 1 (§5.2)**. The one-line model: **the client reads, the backend writes.** The app reads Firestore directly under Security Rules and writes only the student's own profile (and the read flag on their notifications). Everything else goes through a Cloud Function. (INTENT §1.1)

---

## 2. Approved technology (do not replace)

| Layer | Choice | Notes | Source |
|---|---|---|---|
| Client | Native **Kotlin Android** app | Only client, for students *and* staff. No web, iOS or desktop. | SDD §5.1, RC-25 |
| Identity | **Firebase Authentication**, email + password | Institution accounts carry a **custom claim** only developers set. | SDD §5.1 |
| Data | **Cloud Firestore** + **Security Rules** | Central store. Rules are the client-side access control. | SDD §5.1 |
| Backend | **Firebase Cloud Functions (Node.js, TypeScript)** | All trusted logic, the Routes key, the daily job. App→backend calls are **callable functions (`onCall`)**, including the export (CSV returned in the response). | SDD §5.1; owner decision 2026-11-07 |
| Region | **us-west2 (Los Angeles)** for Firestore and Functions | Closest to CBU. Firestore's location is permanent. | Owner decision 2026-11-07 |
| Real-time | **Firestore listeners** | In-app banners and live screens. **No push notifications** (FCM is out of scope). | SDD §5.1, §2.3, OQ-05 |
| Addresses | **Google Places SDK for Android** | Autocomplete on device; yields place ID + coordinates. | SDD §5.1, RC-13 |
| Routes | **Google Routes API (Compute Routes)** | Backend only, no live traffic. Not the legacy Directions API. | SDD §5.1, RC-14 |
| Billing | Firebase **pay-as-you-go** + Google Maps Platform billing, with budget alerts | Required before Phase 1 sign-up and autocomplete. | SDD §5.1, INTENT §1.3 |
| Environments | **One Firebase project**; **Emulator Suite** for local dev, tests and CI | No dev/staging/prod split for the MVP. | INTENT §1.3 |
| CI | **GitHub Actions** on PRs: Android build + Functions + Rules tests on the emulator | No deploys, no secrets in CI. | INTENT §5.1 (TMP-52, #54) |

Not in the approved stack (don't add without a documented human decision): Cloud Storage, FCM, Firebase App Check (deferred past the MVP), any web hosting/portal, any other mapping/geo service, any third-party backend or database.

**Repository layout** (monorepo; owner decision 2026-11-07, revisit if it causes problems):

```
/
├─ android/               Kotlin app (Gradle project)
├─ functions/             Cloud Functions (TypeScript); shared modules under src/shared/
├─ scripts/               developer-only scripts (institution accounts, seeding)
├─ firebase.json          emulator + deploy config
├─ firestore.rules
├─ firestore.indexes.json
└─ docs/
```

**Decided later, inside their first issue, then recorded here** (INTENT §6.2):

| Decision | Decided in | Value |
|---|---|---|
| Android UI toolkit, minimum Android version and app structure (pattern, modules) | #11 (TMP-02) | _TBD_ |
| Exact Firestore collection layout | #13 (TMP-04), #20 (TMP-14) | _TBD_ |
| How rules recognize a consented student (custom claim vs reading the consent record) | #13 (TMP-04) | _TBD_ |
| How profile permissions and weekday rosters are stored | #13 (TMP-04), #44 (TMP-37) | _TBD_ |
| Phase 6 performance dataset size | #50 (TMP-48) | _TBD_ |

---

## 3. Components and responsibilities

### 3.1 Android app

- All screens in SDD Figure 2. Screen names in code and issues follow Figure 2.
- Role routing after sign-in: institution claim → Export Carpool Data only (no tabs); student without starting address → Onboarding; otherwise → Search. A student account without a consent record can't use the app. (US-01, US-02, RC-06)
- Reads Firestore directly; listens for notifications.
- **Ranks search results on device** by straight-line distance from the rider's stored starting coordinates. No Maps call. (RC-19)
- Computes the preference match percentage (formula in INTENT §6.3); it never changes result order. (RC-20, RC-21)
- Address entry via Places autocomplete; only a chosen suggestion is accepted. (RC-13)
- Shares the export CSV through Android's share menu. (US-17)
- **Never** computes or supplies distances, times, seats, request states or trip counts that the backend stores. UI checks mirror backend checks for UX only.

### 3.2 Cloud Functions (backend)

| Function / job | Responsibility | Source |
|---|---|---|
| Sign-up | Checks `@calbaptist.edu`, password, name lengths, consent; creates Auth account + user records + consent record (server timestamp) in one step; deletes the Auth account if a later step fails, and logs failures. | RC-06, INTENT §3.1 |
| Route preview | Runs shared validation; calls Routes once per weekday × direction; computes expected times; stores result on a **server-side draft** with a "what was reviewed" marker; names the failing address. | RC-12, RC-14, RC-28 |
| Post series | Re-runs shared validation (start date, matching date, RC-31); confirms draft unchanged since review; saves exactly the reviewed results; idempotent on duplicate tap; makes host profile visible. | RC-12, RC-31, RC-01 |
| Send request | One request per rider × series; open-seat check; grants host view of requester; notifies host. | RC-16, RC-01 |
| Accept / decline | One weekday at a time; atomic seat + request state + timestamp + search fields; auto-declines that weekday in other pending requests when full; updates permissions/rosters; one notification per weekday decision. | RC-15, US-08, US-15 |
| Remove rider / leave / withdraw | Reuse the shared seat-update path; record timestamps; update permissions/rosters; notify. No rejoining. | RC-15, RC-16 |
| Cancel series | Status change (never delete); close pending weekdays; record cancel time; update permissions; notify everyone with a pending/accepted request. | US-06, RC-24 |
| Daily end-of-series job | Scheduled, shortly after midnight America/Los_Angeles; marks ended series by server clock; notifies; updates search fields/permissions; **idempotent**. | RC-17, INTENT §6.1 |
| Export | Institution claim only; builds CSV from stored records via the trip-counting module; returns the file to the app. | US-17, RC-07, RC-23 |
| Institution account script | **Developer-run, outside the app.** Creates the account and sets the claim. | RC-07, #15 |

### 3.3 Shared backend modules ("contracts")

Each exists **exactly once**. Changing one is its own reviewed contract-change issue, never part of a feature issue. (INTENT §4, §5)

| Module | Used by | Contract issue |
|---|---|---|
| Core schema + profile-permission model | everything | #13 (TMP-04) |
| Series / draft / seat data model (incl. copied search fields: open weekdays, pickup coordinates, status) | hosting, search, lifecycle | #20 (TMP-14) |
| Shared series validation | route preview **and** post | #23 (TMP-15) |
| Notification writer | every function that notifies, daily job | #21 (TMP-28) |
| Per-weekday timestamp contract (accepted / left / removed / cancelled, server timestamps) | lifecycle functions, trip counting | #26 (TMP-31) |
| Seat-update path (Firestore transaction) | accept, remove, leave, cancel | inside #40 (TMP-32) |
| Trip-counting module (departure-time rule; takes records, returns counted trips) | export, Phase 6 checks | #29 (TMP-41) |
| Consent gate (rejects student accounts without a consent record) | every student-facing function | inside #14 (TMP-05) |

### 3.4 Firestore (logical entities)

Exact collection names are TBD (§2). Logical entities and who may touch them:

| Entity | Client read | Client write |
|---|---|---|
| Public profile | owner; permitted viewers (RC-01) | **owner only**, within RC-30 limits and predefined values |
| Private details (starting address + coords + place ID, email) | **owner only** | owner (via profile edit) |
| Consent record | — | **never** |
| Profile permissions | as needed by rules | **never** |
| Campus points, option lists (seeded) | students | **never** |
| Series draft | its host | **never** |
| Series (incl. stored routes, per-weekday seats, status) | students | **never** |
| Request (one per rider × series) | its rider and the series host | **never** |
| Weekday roster | host; riders accepted on that weekday | **never** |
| Notification | its recipient | read flag only |

Institution accounts read nothing in the app; they only call the export function. An account with neither a consent record nor the institution claim can read and write nothing. (INTENT §3.1)

---

## 4. Key flows

1. **Sign-up:** app → sign-up function → Auth account + records + consent in one step → app signs in. Plain Auth sign-ups outside the app are possible, so rules and functions treat consent-less student accounts as unusable. (§5.2 ¶1)
2. **Address entry:** app ↔ Places (session tokens) → place ID + coordinates stored with the address. (RC-13, INTENT §6.1)
3. **Host a series:** Create Series → route-preview function (validation → Routes API × weekday × direction → draft) → Review screen reads the draft → post function (revalidate, check draft unchanged, save exactly what was reviewed). Never recalculated after posting. (RC-12, RC-14)
4. **Search:** app queries active series with an open seat on a chosen weekday (copied open-weekday field + status filter + index) → excludes own and already-requested → sorts on device by straight-line distance. No Maps call. (RC-19, RC-26)
5. **Request lifecycle:** every state change is a function call running a Firestore transaction that updates seats, request state, timestamps, search fields, permissions/rosters **together**, then writes notifications via the writer. (RC-15, INTENT §6.1)
6. **Notifications:** functions write notification docs → app listener shows a banner (~4.5 s) and history with unread badge. Target: 95% within 10 s with the app open. (RC-27)
7. **Export:** staff picks a date range → export function checks claim → trip-counting module → CSV (UTF-8 with BOM, quoted fields) → app → Android share sheet. No Maps call. (US-17, INTENT §3.5)

---

## 5. Boundaries and allowed dependencies

**Allowed:**
- App → Auth (sign-in/sign-out only; never account creation for students).
- App → Firestore (reads under rules; own profile writes; notification read flag).
- App → Cloud Functions (callable functions, carrying the user's auth token).
- App → Places.
- Functions → Firestore, Auth (admin), Routes API.
- Function handlers → shared backend modules → Firestore. Shared modules don't depend on handlers.

**Forbidden:**
- App → Routes API, or any Maps call outside address entry.
- Functions → Routes from anywhere but route preview. Search, viewing a series and export **never** call a Maps service. (RC-28)
- App writes to series, drafts, seats, requests, rosters, consent, permissions, or notifications (other than the read flag) — **not even temporarily as scaffolding**. (INTENT §4)
- A second copy of validation, seat updates or trip counting. (INTENT §4)
- Any path that lets one university's data reach another's (relevant only if multi-university is ever added; RC-02).

---

## 6. Trust, security and privacy boundaries

| Boundary | Rule | Source |
|---|---|---|
| Device ↔ Firestore | Security Rules are the access control. **UI hiding is not access control.** Every rule is tested in the emulator before the feature merges. | INTENT §1.3, §4 |
| Device ↔ Functions | Functions trust only the auth token and server state. Re-check every rule the UI checks. | RC-12, RC-15 |
| Consent | No student account exists without a consent record; consent-less accounts can't use the app. Consent can't be withdrawn. | RC-06, RC-24 |
| Institution role | Custom claim, set only by the developer script. Export only — no profiles, series or writes. | RC-07 |
| Private data | Starting address and email live in a separate owner-only record. Never in another user's view, notification text, function logs, or the export (except the email column US-17 requires). Starting address is used **only** for ranking. | RC-01, RC-05, INTENT §6.3 |
| Profile visibility | Only the four RC-01 cases; access ends immediately when no case holds. Permissions are maintained by functions **in the same transaction** as the change that causes them. | RC-01, INTENT §6.1 |
| Secrets | Nothing in the repo. Routes key only in the backend's secret storage. No secrets in CI. | RC-04 |
| Places key | Ships in the app, so it is **public by design**. Protected by restricting it to the Places API only, the Android app restriction (package name + signing-cert fingerprint), quotas and budget alerts — not by secrecy. Injected at build time with the **Secrets Gradle plugin**: real key in uncommitted `local.properties`, committed placeholder so CI builds without it. | RC-04, RC-28, RISK-08; owner decision 2026-11-07 |
| Data minimization | No sensitive categories (RC-03). No media (RC-08). No messaging (RC-09). Test data uses made-up students. | RC-03, RC-08, RC-09, INTENT §6.3 |
| No verification claims | Nothing may imply drivers, vehicles or insurance are checked. | RISK-01, INTENT §6.3 |

---

## 7. Invariants (must / never)

**Time**
- **Server clock decides** ended series, trip counting and export. Never the device clock. (RC-17)
- All times are **America/Los_Angeles** with DST. Departure times are stored as **local clock times**; conversion to instants happens in the backend and is tested with a DST week. Display is 12-hour. (RC-18, INTENT §6.1)
- A trip counts by its **scheduled departure from the pickup spot**, not its calendar date. (DD-14, RC-23)

**Data**
- **Nothing is deleted.** Cancel and end change status; series, requests, consent and notifications are kept. (RC-24)
- A posted series is **immutable**. Stored routes are **never recalculated**. (RC-12, DD-06)
- Exactly **one request per rider × series**, ever; no rejoining a weekday. Enforced by record structure, not just a check. (RC-16)
- Seats are per weekday; the backend is the single source of truth; changes are **atomic** — two riders can never take the last seat. (RC-15)
- Every address has coordinates from Places. (RC-13)
- Interests, majors, years, traits and genders come from seeded predefined lists. Agents must **not invent** the CBU majors list or campus-point coordinates. (RC-22, INTENT §3.1)

**Process**
- Rules come with features; contracts change only through their own issue. (INTENT §4)
- Scheduled jobs and post are **idempotent**.
- User-facing text uses SDD domain terms, never DB/code names. (RC-29)

**Scope — never build** (SDD §2.3, INTENT §6.3): push notifications, no-class calendar or skippable dates, messaging, payments, ratings, map display, GPS/location detection, editing a posted series, one-way series, media, password reset, email verification, multi-university, web portal, or prototype-only features (copy schedule, splash tap, simulation panel, demo logins, sample data).

---

## 8. Runtime and deployment

- **One Firebase project** hosts Auth, Firestore, Functions and the scheduled job; the Maps billing account and keys sit alongside it. (INTENT §1.3)
- **Firebase Emulator Suite** runs locally and in CI; all rules and function tests run against it. CI never deploys.
- Scale target: one university, MVP load. Search (95% < 3 s) relies on query-then-rank-on-device; beyond MVP scale it would need geographic filtering, which is out of scope. (RC-26, INTENT §6.1)
- Maps usage must stay within free allowances: restricted keys, budget alerts, Places session tokens, routes only at review. (RC-28, RISK-08)

---

## 9. Open items — do not guess

| Item | Status | Owner / where |
|---|---|---|
| CBU majors list | Waiting on project owner | #17 (TMP-12), `human-task` |
| Precise campus-point coordinates | Waiting on project owner | #17 (TMP-12), `human-task` |
| Android UI toolkit, min SDK, app structure | Decide in issue, record in §2 | #11 |
| Firestore layout, permission/roster storage, consent check in rules | Decide in issue, record in §2 | #13, #20, #44 || Performance dataset size | Decide in issue | #50 |

**Accepted architectural risks** (don't "fix" by adding scope): stale permissions if a function fails mid-update (mitigated by same-transaction updates); late series ending of up to a day; partial sign-up leaving a consent-less Auth account (unusable by RC-06, logged); search won't scale past MVP without geo-filtering; unverified emails (RISK-04); estimated, not live, route times (RISK-09). See SDD §7.2 and INTENT §6.1.

---

## 10. Where to look

| Need | Go to |
|---|---|
| Product rules (RC-##), user stories | `SPEC.md` §3–§4 |
| Architecture diagram and narrative | `SPEC.md` §5 (Figure 1) |
| Screen flow | `SPEC.md` §6.1 (Figure 2) |
| Design decisions, risks | `SPEC.md` §7 |
| Phase order, critical path, sequencing rules | `INTENT.md` §2, §4, §4.1 |
| Match formula, agent guardrails | `INTENT.md` §6.3 |
| Issue ↔ TMP mapping | `INTENT.md` §5.2 |
| Notification wording (reference) | `docs/backlog-proposal.md` Appendix B |
| Visual reference (not authoritative) | Prototype: https://claude.ai/artifact/BfQnzLbSXie9qTrGaGk7wP, `docs/issue-assets/prototype/` |
