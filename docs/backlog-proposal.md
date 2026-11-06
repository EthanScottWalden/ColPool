# ColPool — Proposed GitHub Issue Backlog (DRAFT for review)

Status: **approved with revisions on 2026-11-06** (see §3 for the decisions). This file is the source for the GitHub Issue bodies.
Sources: `SPEC.md` (SDD v0.4), `INTENT.md` (Implementation Plan), ColPool Prototype v2 (https://claude.ai/artifact/BfQnzLbSXie9qTrGaGk7wP), GitHub Project "ColPool Project" (#1).

---

## 0. How to read this

### 0.1 Baseline and changes from the Implementation Plan

The plan's §5.1 lists 51 candidate issues (TMP-01 to TMP-51). This proposal keeps the **TMP IDs for traceability** but **recommends merging 7 of them into neighbors**, giving **44 issues**. Reasons:

| Merge | Why |
|---|---|
| TMP-11 → **TMP-10** (Profile + Edit Profile) | Profile view alone is a micro-issue; the two screens share one data binding and the plan's Figure 2 treats them as one tab. |
| TMP-21 → **TMP-17** (post function makes host visible) | Visibility is set by the post function in the same transaction (plan §6.1 "stale permissions"). A separate issue would leave a merged post function that violates RC-01. |
| TMP-22 → **TMP-14** (series/draft rules with the model) | Plan §4 rule: "No feature is merged without its Security Rules tests." A separate later rules issue contradicts that. |
| TMP-30 → split into **TMP-26** (request rules) and **TMP-28** (notification rules) | Same reason. Also TMP-27 (host reads requests) and TMP-29 (app reads notifications) cannot work under default-deny until these rules exist. |
| TMP-40 → split into **TMP-32** (concurrency tests) and **TMP-37** (roster/co-rider rules tests) | Same reason; the plan's Phase 4 acceptance criteria already put these on the features. |
| TMP-44 → split into **TMP-41** (trip-counting fixtures) and **TMP-42** (export/CSV fixtures) | The plan's §4.1 table already says "Trip-counting module **and fixture tests**". A module merged without its edge-case tests is the riskiest thing in Phase 5. |
| TMP-47 → **TMP-46** (security, privacy and concurrency review) | "Run seat concurrency tests" re-runs tests that exist after TMP-32; too small alone. |

If you prefer the plan's 51 issues exactly, say so — each merged issue would be restored with the same content split back out.

### 0.2 Project field conventions (proposed)

| Field | Existing? | Proposed use |
|---|---|---|
| **Status** | Yes | All new issues start in **Backlog**. Moved to **Ready** by you after Human Review = Approved. |
| **Phase** (number) | Yes | 1–6, from the Implementation Plan. |
| **Sequence** (number) | Yes | **Execution wave** (1 = first). Issues with the same number can run in parallel once their blockers are done. |
| **Priority** | Yes (P0/P1/P2) | **P0** = critical-path contract or trust boundary; **P1** = core MVP feature; **P2** = validation/polish that can slip last. |
| **Size** | Yes (XS–XL) | Estimate for one agent implement-and-review cycle. |
| **Area** (multi-select) | Field exists, **no options defined** | Proposed options: `Android`, `Backend`, `Security Rules`, `Data Model`, `Maps`, `Infra`, `Testing`, `Docs`. |
| **Human Review** | **Does not exist** | Proposed: new single-select field with `Pending`, `Approved`, `Changes requested`. All new issues = `Pending`. |
| Labels | Repo has defaults only | Create `contract` (plan §5) and `human-task` (needs a person: billing, coordinates, majors list). |
| Blocked-by | GitHub issue dependencies | Each "Depends on" below becomes a GitHub *blocked by* relationship. |

### 0.3 Content every issue body will include (not repeated below)

- Link to its Implementation Plan phase section and SDD sections (permalinks to `SPEC.md` / `INTENT.md` on `dev`).
- Prototype link + named screens, with the note: *the prototype is a reference, not an authority; the SDD wins (SDD §6.2).*
- SDD Figure 1 (architecture) / Figure 2 (flow) links — these are Mermaid diagrams in `SPEC.md` and render on GitHub.
- The exact SDD pass/fail sentences the issue must satisfy (copied, not paraphrased).
- **Guardrails** pointer to plan §4 sequencing rules and §6.3, plus the global non-goals: no push notifications, no no-class calendar/skippable dates, no messaging/payments/ratings/map display/GPS, no editing a posted series, no one-way series, no media, no password reset/email verification, no prototype-only features (copy-schedule, splash tap, simulation panel, demo logins, sample data).
- **Tests expected** per plan §5: rules → emulator tests; functions → unit tests against the emulator; time logic → fixed fixtures incl. a DST week; screens → on-device check against the named prototype screen.
- For `contract` issues: "Other issues depend on this. Changing it later requires a separate contract-change issue."

---

## 1. Summary table

| TMP | Title | Ph | Seq | Pri | Size | Area | Depends on |
|---|---|---|---|---|---|---|---|
| 01 | Set up Firebase project and billing | 1 | 1 | P0 | S | Infra | — |
| 02 | Create Android app connected to Firebase | 1 | 2 | P0 | M | Android, Infra | 01 |
| 03 | Configure Firebase Emulator Suite for local testing | 1 | 2 | P0 | S | Infra, Testing | 01 |
| 04 | Define core Firestore schema and profile-permission model | 1 | 2 | P0 | M | Data Model | 01 |
| 05 | Implement sign-up function with CBU domain check and consent record | 1 | 3 | P0 | M | Backend | 03, 04 |
| 07 | Add developer script to create institution accounts | 1 | 3 | P1 | S | Backend, Infra | 04 |
| 08 | Integrate Places autocomplete with a restricted API key | 1 | 3 | P1 | M | Android, Maps | 01, 02 |
| 12 | Seed campus points and profile option lists | 1 | 3 | P1 | S | Data Model | 04 |
| 13 | Write baseline Security Rules with emulator tests | 1 | 3 | P0 | M | Security Rules, Testing | 03, 04 |
| 06 | Implement sign-in, role routing and app shells | 1 | 4 | P0 | M | Android, Backend | 02, 04, 05 |
| 14 | Define series, draft and seat data model (+ series/draft rules) | 2 | 4 | P0 | L | Data Model, Security Rules | 04, 13 |
| 28 | Build shared notification writer and notification records (+ rules) | 3 | 4 | P0 | M | Backend, Security Rules | 13 |
| 09 | Build Onboarding screen | 1 | 5 | P1 | M | Android | 06, 08, 12, 13 |
| 15 | Implement shared series validation, including RC-31 hosting limits | 2 | 5 | P0 | M | Backend | 14 |
| 18 | Build Create Series screen | 2 | 5 | P1 | L | Android, Maps | 08, 12, 14 |
| 29 | Build notification banner, history and unread badges | 3 | 5 | P1 | M | Android | 28 |
| 31 | Define per-weekday timestamp contract for trip counting | 4 | 5 | P0 | S | Data Model | 14 |
| 10 | Build Profile and Edit Profile screens | 1 | 6 | P1 | M | Android | 06, 09, 13 |
| 16 | Implement route-preview function with the Routes API | 2 | 6 | P0 | L | Backend, Maps | 15 |
| 41 | Implement shared trip-counting module (+ fixture tests) | 5 | 6 | P0 | L | Backend, Testing | 31 |
| 17 | Implement post-series function with rechecks (+ host visibility) | 2 | 7 | P0 | M | Backend, Security Rules | 13, 16 |
| 19 | Build Review Calculated Route screen | 2 | 7 | P1 | M | Android | 16, 18 |
| 42 | Implement institution export function and CSV output (+ fixtures) | 5 | 7 | P0 | L | Backend, Testing | 06, 07, 41 |
| 20 | Build Hosting list and Hosted Series Details | 2 | 8 | P1 | M | Android | 17 |
| 23 | Build Search screen with weekday filter and distance ranking | 3 | 8 | P1 | L | Android, Data Model | 09, 14, 17 |
| 26 | Implement send-request function with one request per series (+ rules) | 3 | 8 | P0 | M | Backend, Security Rules | 14, 17, 28 |
| 43 | Build Export Carpool Data screen with Android share | 5 | 8 | P1 | M | Android | 06, 42 |
| 24 | Implement preference panel and match percentage | 3 | 9 | P1 | M | Android | 23 |
| 25 | Build Series Details with host profile and request day picker | 3 | 9 | P1 | M | Android | 17, 23 |
| 27 | Show incoming requests and requester profiles to the host | 3 | 9 | P1 | M | Android | 20, 26 |
| 32 | Implement per-weekday accept/decline with atomic seats and auto-decline | 4 | 9 | P0 | L | Backend, Testing | 26, 28, 31 |
| 33 | Implement remove-rider function | 4 | 10 | P1 | S | Backend | 32 |
| 34 | Implement leave and withdraw function | 4 | 10 | P1 | M | Backend | 32 |
| 35 | Add accept, decline and remove-rider actions to Hosted Series Details | 4 | 11 | P1 | M | Android | 27, 32, 33 |
| 37 | Maintain weekday rosters and profile permissions through the lifecycle (+ rules) | 4 | 11 | P0 | L | Backend, Security Rules | 32, 33, 34 |
| 36 | Build Riding tab and Request Details | 4 | 12 | P1 | L | Android | 34, 37 |
| 38 | Implement cancel-series function and confirmation | 4 | 12 | P1 | M | Backend, Android | 32, 37 |
| 39 | Implement daily end-of-series job | 4 | 12 | P1 | M | Backend | 28, 37 |
| 45 | Create SDD acceptance checklist and record results | 6 | 13 | P1 | M | Testing, Docs | Phases 1–5 (leaf issues) |
| 46 | Run security, privacy and concurrency review | 6 | 13 | P0 | M | Security Rules, Testing | 37, 38, 42 |
| 48 | Measure search and notification performance (Android Emulator) | 6 | 13 | P2 | M | Testing | 24, 29 (+ all Phase 1–5 done) |
| 49 | Verify Maps usage and budget alerts | 6 | 13 | P2 | S | Maps, Infra | 08, 16, 23 |
| 51 | Final terminology, time-format and accessibility polish | 6 | 13 | P2 | M | Android | Phases 1–5 (leaf issues) |
| 50 | Fix highest-priority defects (tracking) | 6 | 14 | P1 | XS | Testing | 45, 46, 48, 49 |

"Leaf issues" for 45 and 51 = 10, 19, 24, 25, 29, 35, 36, 38, 39, 43 (every other Phase 1–5 issue is upstream of one of these).

Critical path: 01 → 04 → 14 → 15 → 16 → 17 → 26 → 32 → 33/34 → 37 → 38/39 → 46 → 50.

---

## 2. Issue details

Prototype = https://claude.ai/artifact/BfQnzLbSXie9qTrGaGk7wP. Plan = `INTENT.md`. SDD = `SPEC.md`.

---

### Phase 1 — Foundation: Accounts, Consent & Profiles

#### TMP-01 · Set up Firebase project and billing
- **Fields:** Phase 1 · Seq 1 · P0 · S · Area: Infra · Labels: `human-task` · Status Backlog · Human Review Pending
- **Outcome:** A Firebase project on the pay-as-you-go (Blaze) plan with a budget alert; a Google Maps Platform billing account with Places API and Routes API enabled and a budget alert; a server-side Routes key stored in Cloud Functions secret storage (Secret Manager). No key or credential in the repo.
- **SDD:** RC-04, RC-28; RISK-08. Plan §1.3 "Billing comes first", §3.1 Deliverables "Project setup".
- **Implementation context:** Billing setup requires a human with a payment method. An agent can write `docs/setup/firebase.md` documenting the exact console steps, project IDs, enabled APIs and alert thresholds. The Places key's Android-app restriction needs the app's package name and SHA-1, so it is finalized in TMP-08.
- **Acceptance criteria:** Project exists on Blaze; budget alerts set for Firebase and Maps; Places + Routes APIs enabled; Routes key exists only in Secret Manager; `docs/setup/firebase.md` committed; `.gitignore` excludes `google-services.json`*, service-account keys and `.env` files.
- **UI/UX:** Figure 1 (Firebase / Google Cloud, Google Maps Platform).
- **Depends on:** none.
- **Testing:** Manual verification checklist in the setup doc; `git grep` for key patterns returns nothing.
- **Constraints:** RC-04 — no secrets committed.
- **Open question (resolve in Human Review):** one Firebase project, or separate projects per environment (for example dev/staging/production to match the board's "In dev" and "In staging" columns)? Default if not decided otherwise: one Firebase project for the MVP, with local development and tests on the Emulator Suite.

#### TMP-02 · Create Android app connected to Firebase
- **Fields:** Phase 1 · Seq 2 · P0 · M · Android, Infra
- **Outcome:** A Kotlin Android project (`/android`) that builds, connects to Firebase Auth/Firestore/Functions, and can point at the Emulator Suite in debug builds.
- **SDD:** US-01; RC-04, RC-25. Plan §6.2 "Android setup" (UI toolkit and minimum Android version decided here).
- **Implementation context:** Decide and record in `docs/adr/0001-android-setup.md`: UI toolkit (e.g. Jetpack Compose), min SDK, architecture pattern, DI approach. Debug build flag to use emulators. Places SDK dependency added but not wired (TMP-08).
- **Acceptance criteria:** `./gradlew assembleDebug` succeeds; app launches to an empty placeholder; a debug toggle connects to local emulators; ADR committed; no Firebase config or keys committed (template/instructions only).
- **UI/UX:** Figure 1 (Android device).
- **Depends on:** TMP-01.
- **Testing:** Build + one instrumented smoke test.
- **Constraints:** Android only (RC-25). No iOS/web scaffolding.

#### TMP-03 · Configure Firebase Emulator Suite for local testing
- **Fields:** Phase 1 · Seq 2 · P0 · S · Infra, Testing
- **Outcome:** A `/functions` (Node.js, TypeScript or JS — record choice) workspace plus `firebase.json`, `firestore.rules`, `firestore.indexes.json`, and a test harness that runs Functions unit tests and Security Rules tests against the Auth + Firestore + Functions emulators with one command.
- **SDD:** RC-01, RC-05, RC-07. Plan §1.3 "Security Rules are tested, not assumed."
- **Implementation context:** Start `firestore.rules` as **default deny all**. Provide test helpers for: authenticated student, student without consent, institution-claim user, unauthenticated.
- **Acceptance criteria:** `npm test` (or documented equivalent) starts emulators and runs a sample rules test and a sample function test, both passing; default-deny verified by a test; README section on running tests.
- **UI/UX:** Figure 1 (Auth, Functions, Firestore + Security Rules).
- **Depends on:** TMP-01.
- **Testing:** The harness itself.
- **Constraints:** Default-deny is never loosened as scaffolding (plan §4).

#### TMP-04 · Define core Firestore schema and profile-permission model  `contract`
- **Fields:** Phase 1 · Seq 2 · P0 · M · Data Model · Labels: `contract`
- **Outcome:** A documented schema (`docs/data-model.md` + shared type definitions) for: user record, public profile, private details (starting address with place ID + coordinates, email), consent record (server timestamp), profile-permission structure (who may view whose profile, filled by later phases), and the campus-points and option-list collections.
- **SDD:** US-01, US-02; RC-01, RC-03, RC-05, RC-06, RC-22, RC-30. Plan §3.1 "Data model", §6.2 "Data layout".
- **Implementation context:** Private details stored apart from the public profile so rules can make them owner-only (RC-05). Permission structure must support all four RC-01 cases (active host → all students; requester → host; host → requester/rider; co-riders on a shared accepted weekday). No field for any RC-03 sensitive category. Record design in `docs/adr/0002-firestore-layout.md`.
- **Acceptance criteria:** Schema doc covers every field in SDD §2.2 "Profiles" and RC-30 limits; each RC-01 case is mapped to how the permission record represents it; reviewed shapes exported as shared constants/types usable by functions and rules tests.
- **UI/UX:** Prototype: Profile, Edit Profile (fields a profile holds). Figure 1 (Cloud Firestore).
- **Depends on:** TMP-01.
- **Testing:** Doc review; type definitions compile.
- **Constraints:** Contract — later changes need their own issue.

#### TMP-05 · Implement sign-up function with CBU domain check and consent record
- **Fields:** Phase 1 · Seq 3 · P0 · M · Backend
- **Outcome:** A callable Cloud Function that validates `@calbaptist.edu`, password ≥ 8, first/last name 1–30 chars, consent = true; then creates the Auth account, user records and consent record (server timestamp) together, and deletes the Auth account if any later step fails (logging the failure).
- **SDD:** US-01; RC-02, RC-06, RC-10, RC-30; DD-01, DD-09. Plan §3.1 "Sign-up function", §6.1 "A sign-up that fails partway".
- **Implementation context:** No email verification, no password reset. Logs must not contain the email or password (plan §6.3 "Private data stays private").
- **Acceptance criteria:** Rejects non-CBU email, short password, missing/over-long name, unchecked consent — each with a distinct error code the app can display; success creates account + consent record; induced failure after Auth creation removes the Auth account.
- **UI/UX:** Prototype: Sign up / Sign in (Create account form, consent statement). Figure 1 (app → Functions → Auth); Figure 2 Entry.
- **Depends on:** TMP-03, TMP-04.
- **Testing:** Unit tests against emulator for every rejection path and the rollback path.
- **Constraints:** RC-06 — an account never exists without its consent record (except the logged rollback-failure case, which RC-06 makes unusable).

#### TMP-06 · Implement sign-in, role routing and app shells
- **Fields:** Phase 1 · Seq 4 · P0 · M · Android, Backend
- **Outcome:** Splash, Sign up / Sign in screen (consent checkbox + "no password reset" note), routing: student with starting address → Search (placeholder); student without → Onboarding; institution claim → Export Carpool Data (placeholder, no footer, log out); student without consent record → blocked. Student footer with five placeholder tabs (Search, Hosting, Riding, Notifications, Profile).
- **SDD:** US-01, US-02; RC-06, RC-07, RC-10, RC-29. Figure 2 Entry + University staff.
- **Implementation context:** Signed-in users skip the splash (no tap-to-continue — prototype-only). Sign-up calls TMP-05; sign-in uses Firebase Auth directly. The consent checkbox uses the exact statement in SDD §2.2. Institution accounts are assumed to use `@calbaptist.edu` emails (SDD RC-07).
- **Acceptance criteria:** Each routing case demonstrated on device; an account created directly in Firebase Auth without consent cannot get past sign-in; staff sees no tabs; log out returns to Sign in.
- **UI/UX:** Prototype: Splash; Sign up / Sign in; student tab bar; Export Carpool Data header + log out.
- **Depends on:** TMP-02, TMP-04, TMP-05.
- **Testing:** Instrumented/UI tests for routing; device check vs prototype.
- **Constraints:** Hiding UI is not access control — consent enforcement also lives in rules (TMP-13).

#### TMP-07 · Add developer script to create institution accounts
- **Fields:** Phase 1 · Seq 3 · P1 · S · Backend, Infra
- **Outcome:** A Node admin script (`/scripts/create-institution-account`) that creates an Auth account and sets the institution custom claim; documented usage. Never callable from the app.
- **SDD:** US-01; RC-06, RC-07. Figure 1 (custom claim).
- **Acceptance criteria:** Script works against emulator and (documented) production; claim present on created user; no credentials in repo (service account path from env).
- **Depends on:** TMP-04.
- **Testing:** Emulator test that the created user has the claim and no consent record is required.
- **Constraints:** Institution accounts are created only by this script (plan §6.3).

#### TMP-08 · Integrate Places autocomplete with a restricted API key
- **Fields:** Phase 1 · Seq 3 · P1 · M · Android, Maps
- **Outcome:** A reusable address-entry component (search field + suggestion list) using Places SDK for Android with session tokens; returns place ID, display text and coordinates; rejects free text that is not a chosen suggestion. Places key restricted to the ColPool Android app (package + SHA-1) and the Places API.
- **SDD:** RC-04, RC-13, RC-28; RISK-08.
- **Implementation context:** Key supplied via local `secrets.properties`/Gradle secrets plugin — not committed. Optional "public place" warning slot for TMP-18.
- **Acceptance criteria:** Selecting a suggestion yields coordinates; typed text without a selection cannot be submitted; key restriction verified in console and documented; one autocomplete session per address entry.
- **UI/UX:** Prototype: address search sheet (search field + suggestions). Figure 1 (app ↔ Places).
- **Depends on:** TMP-01, TMP-02.
- **Testing:** Unit test for "only selected suggestion accepted"; device check.
- **Constraints:** No map display, GPS or current-location (SDD §2.3).

#### TMP-09 · Build Onboarding screen
- **Fields:** Phase 1 · Seq 5 · P1 · M · Android
- **Outcome:** After sign-up (or sign-in without a starting address), Onboarding requires a starting address via TMP-08 and offers optional age, gender, interests, major, current year, commute-style traits and about me (≤ 300). Finishing saves the profile and goes to Search.
- **SDD:** US-02; RC-03, RC-05, RC-13, RC-22, RC-30.
- **Implementation context:** Starting address goes to the private-details record; other fields to the public profile; option chips come from seeded lists (TMP-12). Age, if entered, must be a whole number of 18 or older (RC-30, SDD v0.4).
- **Acceptance criteria:** Cannot finish without a chosen starting address; optional fields skippable; quitting and signing in again returns here; values outside RC-30 or not in seeded lists are blocked in UI and rejected by rules.
- **UI/UX:** Prototype: Onboarding. Figure 2 Entry (Onboarding).
- **Depends on:** TMP-06, TMP-08, TMP-12, TMP-13.
- **Testing:** UI tests; device check vs prototype.
- **Constraints:** No photo/media (RC-08); no sensitive categories (RC-03).

#### TMP-10 · Build Profile and Edit Profile screens  *(merges TMP-11)*
- **Fields:** Phase 1 · Seq 6 · P1 · M · Android
- **Outcome:** Profile tab shows the student's own profile, including their starting address and email (visible only to them), with Edit profile and Log out. Edit Profile lets them change name and starting address (cannot clear them) and add/edit/remove optional fields.
- **SDD:** US-02; RC-03, RC-05, RC-08, RC-22, RC-29, RC-30. Figure 2 Profile tab.
- **Acceptance criteria:** Own profile always viewable; name/starting address editable not removable; optional fields removable; edits persist; rules reject invalid writes (tested in TMP-13's suite, extended if needed).
- **UI/UX:** Prototype: Profile (starting address and email cards, Edit profile, Log out); Edit Profile.
- **Depends on:** TMP-06, TMP-09, TMP-13.
- **Testing:** UI tests; device check.
- **Constraints:** No media; email visible only to its owner (RC-01).

#### TMP-12 · Seed campus points and profile option lists
- **Fields:** Phase 1 · Seq 3 · P1 · S · Data Model · Labels: `human-task` (coordinates)
- **Outcome:** A seed script that writes the five CBU campus points (Yeager Center, Music Building, Health Science, Lancer Plaza, Events Center) with precise coordinates, and the predefined lists for interests, majors, years (Freshman, Sophomore, Junior, Senior, Graduate), traits and genders.
- **SDD:** RC-11, RC-22. Plan §1.3 "Real campus points", §6.1 "Campus-point accuracy".
- **Implementation context:** Interests, years, traits and genders use the prototype's lists (see `04-onboarding.png`). **Blocked on human input before implementation:** (1) the CBU majors list, which the project owner will supply (the prototype's 10 majors are placeholders; do not invent a list); (2) precise coordinates for the five campus points (actual curb/lot entrances, plan §3.1), which the project owner is still working out. Points near residence halls are post-MVP (RC-11) and out of scope here.
- **Acceptance criteria:** Seed is idempotent; runs against emulator and production; values match the approved lists exactly; coordinates documented with source.
- **UI/UX:** Prototype: Create Series campus-point chips; Onboarding/Edit Profile option chips.
- **Depends on:** TMP-04.
- **Testing:** Script test against emulator.
- **Constraints:** Hosts cannot add campus points (RC-11).

#### TMP-13 · Write baseline Security Rules with emulator tests
- **Fields:** Phase 1 · Seq 3 · P0 · M · Security Rules, Testing
- **Outcome:** Rules + tests for: private details owner-only; public profiles readable by owner or permitted viewers only; students write only their own profile within RC-30 and predefined values; no client writes to consent or permission records; institution accounts cannot read profiles; accounts without consent (and without institution claim) can read nothing.
- **SDD:** RC-01, RC-03, RC-05, RC-06, RC-07, RC-22, RC-30.
- **Acceptance criteria:** Every bullet above has a passing allow test and deny test.
- **UI/UX:** Figure 1 (Firestore + Security Rules).
- **Depends on:** TMP-03, TMP-04.
- **Testing:** Emulator rules tests (the deliverable).
- **Constraints:** Default-deny stays in place for everything not explicitly allowed.

---

### Phase 2 — Hosting a Ride Series

#### TMP-14 · Define series, draft and seat data model  `contract`  *(merges TMP-22)*
- **Fields:** Phase 2 · Seq 4 · P0 · L · Data Model, Security Rules · Labels: `contract`
- **Outcome:** Documented series record (pickup spot + coordinates, dates, price, vehicle, max riders, per-weekday schedule for both directions with ordered stops/minutes and campus points, stored route results, per-weekday seat counts, status active/cancelled/ended, search fields such as open weekdays and pickup coordinates); server-side draft record with calculated route and a "reviewed" marker; local-time representation in America/Los_Angeles plus a shared time helper. Rules + tests: series and drafts writable only by functions; series readable by students (not institution accounts); drafts readable only by their host.
- **SDD:** US-03, US-05; RC-12, RC-15, RC-17, RC-18. Plan §3.2 "Series data model", "Server-side draft".
- **Acceptance criteria:** Schema doc + shared types; Firestore index plan for the search query (status + open weekdays); rules tests for every read/write case above including "client cannot write route values".
- **UI/UX:** Prototype: Create Series and Hosted Series Details (what a series holds). Figure 1.
- **Depends on:** TMP-04, TMP-13.
- **Testing:** Rules emulator tests; time-helper unit tests incl. DST transitions.
- **Constraints:** Contract issue. Nothing is ever deleted (RC-24) — status changes only.

#### TMP-15 · Implement shared series validation, including RC-31 hosting limits  `contract`
- **Fields:** Phase 2 · Seq 5 · P0 · M · Backend · Labels: `contract`
- **Outcome:** One backend validation module used by route preview and posting: required fields and RC-30 ranges; start date not in the past (Pacific, server clock); end ≥ start; at least one date in range on a chosen weekday; campus departure ≥ expected campus arrival (given route results); RC-31 (≤ 5 active series; no shared weekday with overlapping dates among active series). Returns field-level error codes.
- **SDD:** US-03; RC-12, RC-30, RC-31.
- **Acceptance criteria:** Unit tests for each rule's pass and fail; RC-31 tests with seeded cancelled and ended series showing they don't count; back-to-back series on same weekdays allowed.
- **UI/UX:** Prototype: Create Series error box ("Fix these before continuing").
- **Depends on:** TMP-14.
- **Testing:** Unit tests with fixed clock.
- **Constraints:** Exactly one copy of this logic (plan §4).

#### TMP-16 · Implement route-preview function with the Routes API
- **Fields:** Phase 2 · Seq 6 · P0 · L · Backend, Maps
- **Outcome:** Callable function: validates the draft (TMP-15), calls Routes API Compute Routes once per weekday per direction (to campus: pickup → stops → drop-off; return: campus pickup → stops → pickup spot) without live traffic, computes per-leg miles/minutes and expected times (departure + earlier legs + earlier stop minutes), stores results on the server-side draft with a reviewed marker, and returns them. Names the failing address if a route can't be computed.
- **SDD:** US-04; RC-04, RC-12, RC-14, RC-28; DD-07.
- **Acceptance criteria:** Unit tests with fixed leg data confirm the expected-time formula; failing route returns the specific address; Routes key read only from secret storage; campus-departure check runs after times are known.
- **UI/UX:** Prototype: Review Calculated Route timelines. Figure 1 (Functions ↔ Routes API).
- **Depends on:** TMP-15.
- **Testing:** Unit tests with a mocked Routes client; one manual call against the real API documented.
- **Constraints:** The only place the Routes API is called (plan §4).

#### TMP-17 · Implement post-series function with rechecks  *(merges TMP-21)*
- **Fields:** Phase 2 · Seq 7 · P0 · M · Backend, Security Rules
- **Outcome:** Callable function that re-runs start-date, matching-date and RC-31 checks, confirms the draft hasn't changed since review, saves the series with exactly the reviewed results, ignores duplicate taps (idempotent), and in the same transaction grants all students permission to view the host's profile while they host an active series.
- **SDD:** US-03, US-04; RC-01 (active hosts), RC-12, RC-31; DD-06.
- **Acceptance criteria:** Draft changed after review → fails; start date passed since review → fails; double submit creates one series; another student can read the host profile after posting (rules test); unrelated profile still unreadable.
- **UI/UX:** Prototype: Review post confirmation sheet; Series Details "Your host" card. Figure 2 Review → post → Hosting.
- **Depends on:** TMP-13, TMP-16.
- **Testing:** Function unit tests + rules tests.
- **Constraints:** App never supplies distances/times (RC-12).

#### TMP-18 · Build Create Series screen
- **Fields:** Phase 2 · Seq 5 · P1 · L · Android, Maps
- **Outcome:** Form for pickup spot (TMP-08 with public-place hint), start/end dates, price ($1–99 whole), vehicle (≤ 60), max riders (1–6), weekday toggles; per weekday and direction: departure time (12-hour, Pacific), ordered stops (address + 0–120 min, with stop warning), campus drop-off/pickup chips. Continue calls route preview (TMP-16) and shows returned errors.
- **SDD:** US-03; RC-05, RC-11, RC-13, RC-18, RC-30; DD-10.
- **Acceptance criteria:** Every RC-30 limit enforced in UI; per-day and per-direction differences supported; server errors shown against the right field.
- **UI/UX:** Prototype: Create Series (pickup-spot hint, weekday schedule card, add-stop sheet + stop warning, campus-point chips). **Do not build "Copy schedule to the other days".**
- **Depends on:** TMP-08, TMP-12, TMP-14.
- **Testing:** UI tests for limits; device check.
- **Constraints:** No one-way series, no free-text campus points.

#### TMP-19 · Build Review Calculated Route screen
- **Fields:** Phase 2 · Seq 7 · P1 · M · Android
- **Outcome:** Day tabs; for each direction a timeline with per-leg miles and minutes, expected times at stops, campus arrival and return to pickup spot; "can't be edited after posting" warning; Go back (to Create Series, draft kept) or Post (confirmation sheet → TMP-17 → Hosting).
- **SDD:** US-04; DD-06; RISK-02; RC-18.
- **Acceptance criteria:** Displays exactly the returned values; posting failure (e.g. stale date) returns host to fix and re-review; times 12-hour Pacific.
- **UI/UX:** Prototype: Review Calculated Route (warning note, day tabs, timelines, post confirmation sheet). The prototype's miles/minutes are sample values.
- **Depends on:** TMP-16, TMP-18.
- **Testing:** UI test; device check.

#### TMP-20 · Build Hosting list and Hosted Series Details
- **Fields:** Phase 2 · Seq 8 · P1 · M · Android
- **Outcome:** Hosting tab lists active series (empty state when none); Hosted Series Details shows summary card, day tabs, schedule timelines and open seats per weekday. Riders and Requests sections show empty states (filled in TMP-27/TMP-35). No edit control; no cancel yet.
- **SDD:** US-05; RC-18, RC-29.
- **Acceptance criteria:** Posted series appears with correct seats per weekday; cancelled/ended series don't appear; nothing editable.
- **UI/UX:** Prototype: Hosting (list + empty state); Hosted Series Details (summary card, day tabs, timelines). Figure 2 Hosting tab.
- **Depends on:** TMP-17.
- **Testing:** UI test; device check.

---

### Phase 3 — Search, Requests & Notifications

#### TMP-23 · Build Search screen with weekday filter and distance ranking
- **Fields:** Phase 3 · Seq 8 · P1 · L · Android, Data Model
- **Outcome:** Rider selects weekdays; Firestore query returns active series with an open seat on ≥ 1 chosen weekday; client excludes own series and already-requested series; ranks on device by straight-line distance from the rider's stored starting coordinates (nearest first) and shows distance, weekday chips (open / full / not needed), first matching day's times, and price. Required Firestore index added.
- **SDD:** US-09; RC-17, RC-19, RC-26, RC-28.
- **Acceptance criteria:** Only qualifying series shown, correct order; no Maps call during search; empty state shown when none.
- **UI/UX:** Prototype: Search (day row, result cards, "Nearest first", empty state, footnote). Figure 2 Search tab.
- **Depends on:** TMP-09, TMP-14, TMP-17.
- **Testing:** Unit tests for ranking/exclusion; device check. Formal RC-26 timing in TMP-48.
- **Constraints:** No filters beyond weekdays and preferences (SDD §2.3).

#### TMP-24 · Implement preference panel and match percentage
- **Fields:** Phase 3 · Seq 9 · P1 · M · Android
- **Outcome:** Preferences: any number of interests and traits, at most one major and one year. Match % per result using the plan §6.3 formula; never changes order; clearing all preferences hides it; matched items highlighted on the host card.
- **SDD:** US-10; RC-20, RC-21, RC-22; DD-04. Plan §6.3 "The match formula".
- **Acceptance criteria:** Unit tests for the formula incl. year distance (1 − 0.34 × steps, floor 0), empty host fields score 0, rounding; order unchanged with prefs on/off.
- **UI/UX:** Prototype: Search preferences panel; match badge; highlighted matches on host card.
- **Depends on:** TMP-23.
- **Constraints:** No age/gender preferences (RC-20).

#### TMP-25 · Build Series Details with host profile and request day picker
- **Fields:** Phase 3 · Seq 9 · P1 · M · Android
- **Outcome:** From a search result: host's profile card (matched prefs highlighted), price, dates, max riders, vehicle, day tabs with open seats and timelines, weekday picker (full days and non-running days disabled), driver-not-verified safety note; Send request calls TMP-26 and returns to Search with confirmation.
- **SDD:** US-09, US-11, US-14; RISK-01.
- **Acceptance criteria:** All fields shown; disabled days not selectable; safety note present; no "verified" wording anywhere.
- **UI/UX:** Prototype: Series Details (host card, summary tiles, schedule timelines, request day picker, safety note). Figure 2 Search → Series Details → Search.
- **Depends on:** TMP-17, TMP-23. (Send button wired once TMP-26 is merged; may be stubbed until then.)
- **Testing:** UI test; device check.

#### TMP-26 · Implement send-request function with one request per series  *(+ request rules from TMP-30)*
- **Fields:** Phase 3 · Seq 8 · P0 · M · Backend, Security Rules
- **Outcome:** Callable function: checks each chosen weekday has an open seat, series is active and not the rider's own, and no request exists for this rider + series (enforced by one deterministic request record per rider and series); creates the pending request; grants the host permission to view the requester's profile; notifies the host via TMP-28. Rules + tests: requests readable only by their rider and the series' host; no client create/update.
- **SDD:** US-11; RC-01, RC-15, RC-16; DD-13.
- **Acceptance criteria:** Second request rejected by backend even if UI bypassed; full weekday rejected; own series rejected; host can read requester profile, unrelated student cannot (rules test); notification written.
- **UI/UX:** Prototype: Series Details request button; "Request sent" banner. Figure 1.
- **Depends on:** TMP-14, TMP-17, TMP-28.
- **Testing:** Function unit tests + rules tests.

#### TMP-27 · Show incoming requests and requester profiles to the host
- **Fields:** Phase 3 · Seq 9 · P1 · M · Android
- **Outcome:** Hosted Series Details Requests section lists requests with ≥ 1 pending weekday (requester name, each weekday's status); Requester Profile screen; Hosting tab badge counting requests with pending days. Accept/Decline buttons are not wired (TMP-35).
- **SDD:** US-07, US-14.
- **Acceptance criteria:** New request appears live; badge count correct; profile opens only for requesters.
- **UI/UX:** Prototype: Hosted Series Details Requests section; Hosting tab badge; Requester Profile. Figure 2 Hosted Series Details → Requester / Rider Profile.
- **Depends on:** TMP-20, TMP-26.

#### TMP-28 · Build shared notification writer and notification records  `contract`  *(+ notification rules from TMP-30)*
- **Fields:** Phase 3 · Seq 4 · P0 · M · Backend, Security Rules · Labels: `contract`
- **Outcome:** One backend function/module used by every action to write notifications (recipient, event type, related series, timestamp, read flag, display text). Event types cover every US-15 event: new request; weekday accepted; declined; auto-declined (full); removed from weekday; rider left; request withdrawn; series cancelled; series ended. Rules + tests: readable only by recipient; client may only flip `read`.
- **SDD:** US-15, US-16; RC-09, RC-27.
- **Implementation context:** Usable inside a Firestore transaction so callers can write notifications atomically with their change. Text must not include starting addresses or emails. Prototype wording is available as a reference (see Appendix B).
- **Acceptance criteria:** Unit tests per event type; rules tests for read/mark-read/deny-other-writes.
- **UI/UX:** Prototype: Notifications entries (title, text, series line).
- **Depends on:** TMP-13.
- **Constraints:** Contract. No push, email or other channels (RC-09, OQ-05).

#### TMP-29 · Build notification banner, history and unread badges
- **Fields:** Phase 3 · Seq 5 · P1 · M · Android
- **Outcome:** Firestore listener shows a top banner that auto-dismisses after a few seconds; Notifications tab lists history newest first (event type, series, timestamp) with "New" marks and a tab badge; opening the tab marks entries read.
- **SDD:** US-15, US-16; RC-27.
- **Acceptance criteria:** Banner appears for a newly written notification while app is open; history order and unread state correct; tab leads nowhere else.
- **UI/UX:** Prototype: banner; Notifications screen with "New" tags; tab badge. Figure 2 Notifications tab.
- **Depends on:** TMP-28.
- **Testing:** UI tests using emulator-written notifications.

---

### Phase 4 — Request & Series Lifecycle

#### TMP-31 · Define per-weekday timestamp contract for trip counting  `contract`
- **Fields:** Phase 4 · Seq 5 · P0 · S · Data Model · Labels: `contract`
- **Outcome:** Documented fields on the request (per weekday) and series: requested, accepted, declined (manual/auto), left, removed, withdrawn, and series cancelled/ended — all server timestamps — plus the weekday status enum, including what happens to pending weekdays when a series is cancelled or ends.
- **SDD:** RC-23; DD-14. Plan §3.4 "Timestamp contract".
- **Acceptance criteria:** Doc + shared types; every lifecycle function issue (32–39) and TMP-41 references these exact field names.
- **Depends on:** TMP-14.
- **Constraints:** Contract.

#### TMP-32 · Implement per-weekday accept/decline with atomic seats and auto-decline  *(+ concurrency tests from TMP-40)*
- **Fields:** Phase 4 · Seq 9 · P0 · L · Backend, Testing
- **Outcome:** Callable function deciding one pending weekday. In one transaction: accept only if a seat is open; update seat count, weekday status, acceptance timestamp, series open-weekday field; if the weekday fills, auto-decline that weekday in every other pending request (others untouched); write one notification per decision (and per auto-decline). Exposes the **shared seat-update path** used by TMP-33/34/38.
- **SDD:** US-08, US-15; RC-15; DD-05; RISK-03.
- **Acceptance criteria:** Accept lowers seats by one; full weekday can't be accepted; other weekdays unchanged; auto-decline + notifications correct; **emulator race tests**: two accepts for the last seat → exactly one success; accept racing a leave stays consistent; request leaves host list when none pending.
- **UI/UX:** Prototype: Accept/Decline, "Declined (full)"; "Day accepted"/"Day declined" notifications.
- **Depends on:** TMP-26, TMP-28, TMP-31.
- **Testing:** Unit + concurrency tests (repeated runs).
- **Constraints:** One seat-update path only (plan §4).

#### TMP-33 · Implement remove-rider function
- **Fields:** Phase 4 · Seq 10 · P1 · S · Backend
- **Outcome:** Host removes a rider from one accepted weekday via the shared seat path: reopens the seat, records removal timestamp, notifies rider, updates permissions. Rider can't rejoin.
- **SDD:** US-08, US-15; RC-15, RC-16.
- **Acceptance criteria:** Exactly one seat reopened; other weekdays unchanged; notification; client can't write directly.
- **UI/UX:** Prototype: "Remove from [day]" + confirmation; "Removed from [day]" notification.
- **Depends on:** TMP-32.

#### TMP-34 · Implement leave and withdraw function
- **Fields:** Phase 4 · Seq 10 · P1 · M · Backend
- **Outcome:** Rider leaves an accepted weekday or withdraws a pending one — one or all at once — via the shared seat path; records timestamps; notifies host ("Rider left" / "Request withdrawn"); updates permissions; no rejoining.
- **SDD:** US-12, US-15; RC-15, RC-16; DD-13.
- **Acceptance criteria:** Leave reopens exactly one seat per weekday; withdraw removes from host's pending list; "all" ends participation; rejoin attempts rejected by backend.
- **UI/UX:** Prototype: Request Details "Your days" Leave/Withdraw + confirmation sheets; "Rider left" / "Request withdrawn" notifications.
- **Depends on:** TMP-32.

#### TMP-35 · Add accept, decline and remove-rider actions to Hosted Series Details
- **Fields:** Phase 4 · Seq 11 · P1 · M · Android
- **Outcome:** "Riders by day" with Remove (confirmation); Requests with Accept/Decline per pending weekday, Accept disabled when full ("Full" state).
- **SDD:** US-05, US-08.
- **Acceptance criteria:** Actions call TMP-32/33; UI reflects backend state live; full-day state shown.
- **UI/UX:** Prototype: Hosted Series Details ("Riders by day" with Remove; Requests with Accept, Decline and Full). Figure 2 Hosting tab.
- **Depends on:** TMP-27, TMP-32, TMP-33.

#### TMP-36 · Build Riding tab and Request Details
- **Fields:** Phase 4 · Seq 12 · P1 · L · Android
- **Outcome:** Riding lists pending/accepted requests on active series (weekday statuses, host, price, pickup spot; empty state). Request Details: status card, schedule for pending/accepted weekdays, "People on [weekday]" (host + co-riders from rosters), "Your days" with Leave/Withdraw, Leave all days / Cancel request; every action confirmed; stays on Request Details while live weekdays remain, else returns to Riding. Host / Co-rider Profile screen.
- **SDD:** US-12, US-13, US-14. Figure 2 Riding tab.
- **Acceptance criteria:** Matches US-13 pass/fail; co-rider profiles open only for shared accepted weekdays; navigation rule per Figure 2.
- **UI/UX:** Prototype: Riding (list, empty state); Request Details (status card, "People on [day]", "Your days", confirmation sheets); Host / Co-rider Profile.
- **Depends on:** TMP-34, TMP-37.

#### TMP-37 · Maintain weekday rosters and profile permissions through the request lifecycle  *(+ roster/co-rider rules from TMP-40)*
- **Fields:** Phase 4 · Seq 11 · P0 · L · Backend, Security Rules
- **Outcome:** Backend-maintained per-weekday rosters (riders accepted on that weekday), readable only by the host and riders accepted on that weekday; permission updates (co-riders, host of requested series, requesters) applied in the same transaction as each lifecycle change (accept, decline, remove, leave, withdraw, cancel, end).
- **SDD:** US-14; RC-01. Plan §6.1 "Stale profile permissions".
- **Acceptance criteria:** Rules tests for every RC-01 case allow and deny; rider can read roster only for weekdays they're accepted on and can't read others' request records; access ends immediately when its RC-01 case stops holding (leave, removal, decline, withdrawal, cancellation, end) unless another case still applies (SDD v0.4 RC-01).
- **UI/UX:** Prototype: Request Details "People on [day]".
- **Depends on:** TMP-32, TMP-33, TMP-34.

#### TMP-38 · Implement cancel-series function and confirmation
- **Fields:** Phase 4 · Seq 12 · P1 · M · Backend, Android
- **Outcome:** Host confirms; function records cancellation time, sets status cancelled, closes pending weekdays, removes series from search fields, notifies everyone with a pending or accepted request, updates permissions (host stays visible to all only if still hosting another active series). "Cancel this series" button + confirmation sheet → Hosting.
- **SDD:** US-06, US-15; RC-17, RC-24.
- **Acceptance criteria:** Series disappears from search, host and rider lists; notifications sent; records retained; departed trips still countable (TMP-41).
- **UI/UX:** Prototype: "Cancel this series" + confirmation; "Series cancelled" notification. Figure 2 Hosted Series Details → Hosting.
- **Depends on:** TMP-32, TMP-37.

#### TMP-39 · Implement daily end-of-series job
- **Fields:** Phase 4 · Seq 12 · P1 · M · Backend
- **Outcome:** Scheduled function shortly after midnight America/Los_Angeles marks series ended once the end date has passed (server clock), notifies host and riders, updates search fields and permissions; idempotent; records kept.
- **SDD:** US-15; RC-17, RC-18, RC-24.
- **Acceptance criteria:** Fixed-clock tests incl. DST nights; running twice sends no duplicate notifications; ended series gone from search and lists.
- **UI/UX:** Prototype: "Series ended" notification.
- **Depends on:** TMP-28, TMP-37.

---

### Phase 5 — Institution Export

#### TMP-41 · Implement shared trip-counting module using the departure-time rule  `contract`  *(+ fixture tests from TMP-44)*
- **Fields:** Phase 5 · Seq 6 · P0 · L · Backend, Testing · Labels: `contract`
- **Outcome:** Pure backend module: given series and request records, returns counted trips per rider and host per date, using the departure-time rule (RC-23, DD-14); host trip counts only if ≥ 1 rider trip counts; never after end date; structured so a no-class-days filter could be added later (not built).
- **SDD:** US-17; RC-18, RC-23; DD-14.
- **Acceptance criteria:** Fixture tests pass for: accepted before/after that day's departure; leave/removal before/after departure; cancelled mid-week; end date; host date with/without counted rider; week boundaries; DST-end week (and DST-start).
- **Depends on:** TMP-31.
- **Constraints:** Contract; single implementation.

#### TMP-42 · Implement institution export function and CSV output  *(+ export fixture tests from TMP-44)*
- **Fields:** Phase 5 · Seq 7 · P0 · L · Backend, Testing
- **Outcome:** Callable function, institution claim only, validates date range; builds one row per student × series × Monday–Sunday week (Pacific) with ≥ 1 counted trip: name, email, role, host name, pickup spot, trip dates, trip count, estimated miles, mile type (passenger/vehicle); file labelled "scheduled trips, not verified"; UTF-8 with BOM, quoted fields; returns the file.
- **SDD:** US-17; RC-05, RC-07, RC-18, RC-23, RC-24, RC-28; DD-08.
- **Acceptance criteria:** Student call rejected; no starting address anywhere; no Maps calls; accented/comma names survive; fixture-based golden CSV test.
- **UI/UX:** Prototype: Export Carpool Data table columns (Student → Mile type). Figure 1 (app ↔ Functions, CSV).
- **Depends on:** TMP-06, TMP-07, TMP-41.

#### TMP-43 · Build Export Carpool Data screen with Android share
- **Fields:** Phase 5 · Seq 8 · P1 · M · Android
- **Outcome:** Staff screen: date range fields, Generate export, summary (rows, students, rider trips, passenger miles and vehicle miles shown separately, "Scheduled" label), Share via Android share sheet, log out.
- **SDD:** US-17; DD-08.
- **Acceptance criteria:** CSV shared to email/Drive opens in Excel; summary matches file; staff sees nothing else.
- **UI/UX:** Prototype: Export Carpool Data (date fields, Generate export, summary card, Share). The on-screen table is only a preview — not required. Figure 2 University staff.
- **Depends on:** TMP-06, TMP-42.

---

### Phase 6 — Integrated Validation & MVP Hardening

#### TMP-45 · Create SDD acceptance checklist and record results
- **Fields:** Phase 6 · Seq 13 · P1 · M · Testing, Docs
- **Outcome:** `docs/acceptance-checklist.md` mapping every US-01–US-17 pass/fail check to a test or demo with recorded result; plus `docs/known-limitations.md` (OQ-04, OQ-05, accepted risks).
- **SDD:** US-01–US-17; RC-01–RC-31; RISK-01–RISK-11.
- **Acceptance criteria:** Every pass/fail sentence mapped and marked; failures filed as defect issues linked to TMP-50.
- **Depends on:** Phases 1–5 (leaf issues).
- **Note:** Can be started earlier and grown per phase.

#### TMP-46 · Run security, privacy and concurrency review  *(merges TMP-47)*
- **Fields:** Phase 6 · Seq 13 · P0 · M · Security Rules, Testing
- **Outcome:** Full rules suite run; attempted direct client writes to series, seats, requests, consent, permissions and notifications all blocked; secrets/key-restriction audit; repeated last-seat and accept-vs-leave race runs; logs checked for private data.
- **SDD:** RC-01, RC-04, RC-05, RC-07, RC-15; RISK-03.
- **Acceptance criteria:** No known path to read another's starting address/email, open a disallowed profile, or write seats/requests/series; staff can only export; concurrency tests pass on every run (N runs, N recorded).
- **Depends on:** TMP-37, TMP-38, TMP-42.

#### TMP-48 · Measure search and notification performance
- **Fields:** Phase 6 · Seq 13 · P2 · M · Testing
- **Outcome:** Seeded MVP-scale dataset (size decided and recorded here, plan §6.2); on an Android Emulator device (Android Studio) ≥ 95% of searches < 3 s and ≥ 95% of notifications < 10 s; results recorded, with the emulator configuration and host machine noted. No physical device is available.
- **SDD:** RC-26, RC-27.
- **Depends on:** TMP-24, TMP-29 (run after Phases 1–5 complete).

#### TMP-49 · Verify Maps usage and budget alerts
- **Fields:** Phase 6 · Seq 13 · P2 · S · Maps, Infra
- **Outcome:** Scripted session confirms the only Maps calls are autocomplete and review-time route calculation; budget alerts confirmed.
- **SDD:** RC-28; RISK-08.
- **Depends on:** TMP-08, TMP-16, TMP-23.

#### TMP-51 · Final terminology, time-format and accessibility polish
- **Fields:** Phase 6 · Seq 13 · P2 · M · Android
- **Outcome:** All user-facing text uses SDD terms (RC-29); all times 12-hour Pacific (RC-18); touch targets and screen-reader labels match prototype; no "verified" claims.
- **SDD:** RC-18, RC-29; RISK-01.
- **Depends on:** Phases 1–5 (leaf issues).

#### TMP-50 · Fix highest-priority defects
- **Fields:** Phase 6 · Seq 14 · P1 · XS · Testing
- **Outcome:** Tracking issue; each defect from 45/46/48/49 gets its own issue added as a sub-issue. Closed when all P0/P1 defects are fixed.
- **Depends on:** TMP-45, TMP-46, TMP-48, TMP-49.

---

## 3. Decisions (resolved 2026-11-06)

| # | Question | Decision |
|---|---|---|
| D-1 | Merge to 44 issues or keep 51? | **Merge.** Plan §3 and §5.1 updated. |
| D-2 | Board setup | Create **Human Review** (Pending / Approved / Changes requested), **Area** options as proposed, labels `contract` and `human-task`. No `tracking` label. |
| D-3 | Option lists | Prototype lists for interests, years, traits and genders. **Majors list to be supplied by the project owner** before TMP-12 is implemented. |
| D-4 | Age limits | **18 or older** (SDD v0.4 RC-30; RISK-11 updated). |
| D-5 | Campus-point coordinates | **Open.** The project owner is still working these out; TMP-12 is labelled `human-task` and blocked on this input. |
| D-6 | Environments (one Firebase project vs dev/staging/prod) | **Open.** Recorded on TMP-01 for Human Review; default is one project plus the Emulator Suite. |
| D-7 | CI on pull requests | **Not added yet.** Pending the owner's answer after an explanation. |
| D-8 | Profile access after leaving | **Revoke immediately** when the RC-01 case stops holding (SDD v0.4 RC-01). |
| D-9 | Staff email domain | Institution accounts are assumed to use `@calbaptist.edu` (SDD v0.4 RC-07). |
| D-10 | Prototype images | Captured from the prototype into `docs/issue-assets/prototype/` and embedded in issues. |
| D-11 | Consent wording | Prototype wording adopted verbatim (SDD v0.4 §2.2). |
| — | Performance testing device | No physical Android phone is available; TMP-48 uses an Android Emulator device (plan §3.6 updated). |

### Minor items agents can settle (flagged, no decision needed unless you object)
- Banner duration: prototype uses 4.5 s ("a short time" in SDD).
- Pending weekdays on cancel: prototype marks them declined; riders get one "Series cancelled" notification, not per-day decline notices. TMP-31 will record this.
- Export date range: no maximum in SDD; TMP-42 will validate start ≤ end only.
- "Series ended" notification: prototype wording exists ("…series reached its end date on Oct 9.").

---

## Appendix A — TMP mapping

Merged away: TMP-11 → 10; TMP-21 → 17; TMP-22 → 14; TMP-30 → 26 + 28; TMP-40 → 32 + 37; TMP-44 → 41 + 42; TMP-47 → 46.

## Appendix B — Prototype notification wording (reference, from `project/Main.dc.html`)

| Event | Title | Body |
|---|---|---|
| New request | New ride request | "[Rider] wants to join your series on [days]." |
| Accepted / declined | Day accepted / Day declined | "[Host] accepted/declined your request for [day]." |
| Auto-declined | Day declined | "[Day] in [Host]'s series filled up, so that day of your request was declined automatically." |
| Removed | Removed from [Day] | "[Host] removed you from their series on [Day]." |
| Rider left | Rider left | "[Rider] left your series on [days]. Seats opened up." |
| Withdrawn | Request withdrawn | "[Rider] withdrew their request for [days]." |
| Cancelled | Series cancelled | "[Host] cancelled a series you were in." |
| Ended | Series ended | "[Host]'s series reached its end date on [date]." |

## Appendix C — Created GitHub Issues

The issues were created on November 6, 2026 in the [ColPool Project](https://github.com/users/EthanScottWalden/projects/1). Dependencies are recorded on GitHub as "blocked by" relationships.

| TMP ID | Issue | Title |
|---|---|---|
| TMP-01 | #10 | Set up Firebase project and billing |
| TMP-02 | #11 | Create Android app connected to Firebase |
| TMP-03 | #12 | Configure Firebase Emulator Suite for local testing |
| TMP-04 | #13 | Define core Firestore schema and profile-permission model |
| TMP-05 | #14 | Implement sign-up function with CBU domain check and consent record |
| TMP-06 | #19 | Implement sign-in, role routing and app shells |
| TMP-07 | #15 | Add developer script to create institution accounts |
| TMP-08 | #16 | Integrate Places autocomplete with a restricted API key |
| TMP-09 | #22 | Build Onboarding screen |
| TMP-10 | #27 | Build Profile and Edit Profile screens |
| TMP-12 | #17 | Seed campus points and profile option lists |
| TMP-13 | #18 | Write baseline Security Rules with emulator tests |
| TMP-14 | #20 | Define series, draft and seat data model, with its Security Rules |
| TMP-15 | #23 | Implement shared series validation, including RC-31 hosting limits |
| TMP-16 | #28 | Implement route-preview function with the Routes API |
| TMP-17 | #30 | Implement post-series function with rechecks and host visibility |
| TMP-18 | #24 | Build Create Series screen |
| TMP-19 | #31 | Build Review Calculated Route screen |
| TMP-20 | #33 | Build Hosting list and Hosted Series Details |
| TMP-23 | #34 | Build Search screen with weekday filter and distance ranking |
| TMP-24 | #37 | Implement preference panel and match percentage |
| TMP-25 | #38 | Build Series Details with host profile and request day picker |
| TMP-26 | #35 | Implement send-request function with one request per series, with request Security Rules |
| TMP-27 | #39 | Show incoming requests and requester profiles to the host |
| TMP-28 | #21 | Build shared notification writer and notification records, with notification Security Rules |
| TMP-29 | #25 | Build notification banner, history and unread badges |
| TMP-31 | #26 | Define per-weekday timestamp contract for trip counting |
| TMP-32 | #40 | Implement per-weekday accept/decline with atomic seats and auto-decline, with seat concurrency tests |
| TMP-33 | #41 | Implement remove-rider function |
| TMP-34 | #42 | Implement leave and withdraw function |
| TMP-35 | #43 | Add accept, decline and remove-rider actions to Hosted Series Details |
| TMP-36 | #45 | Build Riding tab and Request Details |
| TMP-37 | #44 | Maintain weekday rosters and profile permissions through the request lifecycle, with their Security Rules |
| TMP-38 | #46 | Implement cancel-series function and confirmation |
| TMP-39 | #47 | Implement daily end-of-series job |
| TMP-41 | #29 | Implement shared trip-counting module using the departure-time rule, with fixture tests |
| TMP-42 | #32 | Implement institution export function and CSV output, with fixture tests |
| TMP-43 | #36 | Build Export Carpool Data screen with Android share |
| TMP-45 | #48 | Create SDD acceptance checklist and record results |
| TMP-46 | #49 | Run security, privacy and concurrency review |
| TMP-48 | #50 | Measure search and notification performance |
| TMP-49 | #51 | Verify Maps usage and budget alerts |
| TMP-50 | #53 | Fix highest-priority defects |
| TMP-51 | #52 | Final terminology, time-format and accessibility polish |
