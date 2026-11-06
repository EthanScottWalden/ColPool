**ColPool**

**Implementation Plan**

CSCI 5520 - AI-Driven Software Development

# 1 Implementation Overview

## 1.1 Implementation Objective

ColPool will be built as a native Kotlin Android app backed by Firebase:

- Firebase Authentication for sign-in
- Cloud Firestore for data
- Cloud Functions (Node.js) for every trusted action

Address entry uses Google Places on the device. Route calculation uses the Google Routes API on the backend.

The implementation delivers the MVP defined in SDD v0.4 §2.2, and nothing beyond it:

- CBU student accounts created with data-sharing consent
- profiles with a private starting address
- recurring ride series with routes calculated on the backend and checked by the host before posting
- search ranked by distance, with a preference match percentage
- requests decided one weekday at a time
- in-app notifications
- the institution CSV export

The plan keeps the SDD's trust boundary throughout. The app reads permitted data directly from Firestore and writes only the student's own profile. Anything that changes accounts, consent, series, seats, requests, profile permissions or notifications goes through a Cloud Function.

## 1.2 Source Design Artifacts

- **Software Design Document:** ColPool SDD v0.4 (November 6, 2026), including RC-31 (hosting limits).
- **Interactive UI/UX prototype:** ColPool Prototype v2, https://claude.ai/artifact/BfQnzLbSXie9qTrGaGk7wP (SDD §6.2). It's a reference for look and behavior, not an authority; the SDD wins where they differ. §6.2 lists the known differences.
- **Architecture reference:** SDD Figure 1 and the technology table in §5.1.
- **Experience-flow reference:** SDD Figure 2 and §6.1. Screen names in this plan follow Figure 2.
- **External references:** the Firebase and Google Maps Platform documentation listed in SDD §8.

## 1.3 Planning Assumptions & Scope Guardrails

- **What's authoritative.** SDD v0.4 is the authoritative design. Prototype-only features are not built: copying a day's schedule, the splash screen waiting for a tap, the simulation panel, demo logins and sample data.
- **No open MVP questions.** Holidays (OQ-04) and notifications while away (OQ-05) have MVP answers. Holiday dates count as scheduled trips, and there are no push notifications. Agents must not build a no-class calendar, skippable dates or push notifications.
- **Platform.** Android only and CBU only (RC-02, RC-25). Map display, GPS, in-app payments, messaging and editing a posted series stay out of scope (SDD §2.3).
- **Billing comes first.** Student sign-up runs in a Cloud Function, so Firebase's pay-as-you-go plan is needed in Phase 1. A Google Maps Platform billing account, restricted API keys and budget alerts must exist before address autocomplete is built in Phase 1 (RC-04, RC-28, RISK-08).
- **Security Rules are tested, not assumed.** Because the app reads Firestore directly, a feature isn't done until its rules are tested in the Firebase Emulator Suite. Hiding something in the UI doesn't count as access control (RC-01, RC-05, RC-07).
- **Server time decides time-based logic.** Ended series, whether a trip counts, and the export all use the server's clock in the university's time zone, America/Los_Angeles (RC-17, RC-18, RC-23, DD-14). The trip-counting rule is implemented once and shared.
- **Real campus points.** The five campus points in RC-11 are seeded with precise coordinates, such as actual curb or lot entrances, during Phase 1. The prototype's locations are only estimates.

> **Planning principle:** Implementation agents may refine technical details within the approved design. They must not add product scope, change a security, privacy or consent rule, or answer an SDD question differently without a documented human decision.

# 2 Phase Summary

ColPool is split into six phases. Each one builds on the data and trust boundaries of the one before:

- Accounts, consent and private profile data come first, because every later feature depends on them.
- Hosting comes next, since search and requests need series to exist.
- Search, requests and the notification system follow, then everything that happens to a request and a series after it's sent.
- The export comes once the timestamps it counts from exist.
- Integrated validation closes out the MVP.

| Phase | Goal / Increment | Primary SDD Sources | Key Deliverables | Depends On | Candidate Issues |
|---|---|---|---|---|---|
| 1 | **Foundation: accounts, consent and profiles.** A student can sign up, onboard and edit their profile; staff can sign in. | US-01, US-02; RC-01–RC-08, RC-10, RC-13, RC-22, RC-25, RC-30; DD-01, DD-09, DD-10 | Firebase project on pay-as-you-go; Android app shell with student tabs and staff screen; sign-up function that creates the account and consent record together; sign-in routing (student, staff, back to onboarding); public profile kept apart from private details; onboarding and Edit Profile with Places autocomplete; seeded campus points and option lists; profile-permission model; baseline Security Rules with emulator tests | None | Project setup; sign-up + consent function; sign-in routing; profile data split; onboarding/profile screens; address autocomplete; seed data; rules baseline |
| 2 | **Hosting a ride series.** A host can create, review, post and view a series with its calculated route. | US-03, US-04, US-05; RC-11, RC-12, RC-14, RC-18, RC-28, RC-30, RC-31; DD-02, DD-03, DD-06, DD-07, DD-12 | Series data model, including seats per weekday and the copied fields search needs; Create Series screen; route-preview function with a server-side draft; Review Calculated Route screen; post function with the posting rechecks and RC-31 limits; Hosting list; Hosted Series Details (schedule and seats); host profile visible to all students while the series is active | Phase 1 | Series data model; Create Series form; route preview (Routes API); review screen; post + rechecks + RC-31; Hosting list and details |
| 3 | **Search, requests and notifications.** A rider can find nearby series and send a request; the host sees it and is notified. | US-07, US-09, US-10, US-11, US-14, US-15, US-16; RC-01, RC-09, RC-16, RC-19–RC-21, RC-26, RC-27; DD-04, DD-11 | Search screen with weekday filter and on-device distance ranking; preference match percentage; Series Details with the host's profile; send-request function (one request per series); host's incoming-requests list with requester profiles; shared notification writer; banner, notification history and unread badge | Phases 1–2 | Search query + ranking; preference match; Series Details; send-request function; incoming requests; notification writer; banner + history |
| 4 | **Request and series lifecycle.** Hosts decide each weekday and riders ride, leave or withdraw. Series can be cancelled or end, and everyone affected is notified. | US-05, US-06, US-08, US-12, US-13, US-14, US-15; RC-01, RC-15–RC-17, RC-24; DD-05, DD-13, DD-14 | Accept/decline function per weekday, with seat changes and request states updated in one atomic step and auto-decline; remove-rider function; Riding tab and Request Details; leave and withdraw; weekday rosters and co-rider profile permissions; cancel series; daily job that ends series; the remaining notification events | Phase 3 | Timestamp contract; accept/decline + auto-decline; remove rider; Riding + Request Details; leave/withdraw; rosters + co-rider permissions; cancel series; daily end job |
| 5 | **Institution export.** Staff can export scheduled trips for any date range as a CSV and share it. | US-17; RC-05, RC-07, RC-18, RC-23, RC-24; DD-08, DD-14 | Shared trip-counting module (departure-time rule); export function checking the institution claim; CSV with scheduled-trip, passenger-mile and vehicle-mile labels; Export Carpool Data screen with Android share menu; edge-case tests with fixed sample data | Phase 4 (request and series timestamps); Phase 1 (staff routing) | Trip-counting module; export function + CSV; Export screen + share |
| 6 | **Integrated validation and MVP hardening.** Every pass/fail check is demonstrated and the main risks are tested. | US-01–US-17; RC-01–RC-31 | Acceptance checklist covering all user stories; Security Rules and access review; last-seat concurrency test; search and notification timing checks; Maps usage check; terminology and polish | Phases 1–5 | Acceptance checklist; rules/access review; concurrency test; performance checks; polish/fixes |

# 3 Detailed Implementation Phases

## 3.1 Phase 1 – Foundation: Accounts, Consent & Profiles

### Goal

Set up the trusted identity, consent, profile and access-control foundation that every later feature depends on. At the end of this phase:

- A CBU student can sign up with consent, complete onboarding, and view and edit their profile.
- University staff can sign in to an empty export screen.
- No student can read another student's starting address or email.

### Source Requirements / Design References

US-01, US-02; RC-01 (own profile and the permission mechanism), RC-02, RC-03, RC-04, RC-05, RC-06, RC-07, RC-08, RC-10, RC-13, RC-22, RC-25, RC-29, RC-30; DD-01, DD-09, DD-10.

Figure 1: Authentication, Cloud Functions, Firestore and Places. Figure 2: Entry, Profile tab and University staff.

### Deliverables

- **Project setup.**
  - A Firebase project on the pay-as-you-go plan, with a budget alert.
  - An Android (Kotlin) app connected to Firebase.
  - Firebase Emulator Suite configured for local testing.
  - A repository with no secrets in it (RC-04).
- **Sign-up function.**
  - It checks the `@calbaptist.edu` domain, password length, name lengths and that consent was given.
  - It creates the Auth account, the user records and the consent record (with a server timestamp) in one step.
  - It removes the Auth account if any later part fails (RC-06).
- **Sign-in and routing.**
  - A student with a starting address goes to Search, which is a placeholder for now.
  - A student without one goes back to Onboarding.
  - A staff account with the institution claim goes to Export Carpool Data, which is also a placeholder.
  - A student account without a consent record can't use the app.
- **Developer script for institution accounts.** It creates the account and sets the claim, outside the app (RC-07).
- **Data model.**
  - The public profile.
  - Private details (starting address with place ID and coordinates, and email) in a separate record that only the owner can read (RC-05).
  - The consent record.
  - The profile-permission structure that later phases fill in (RC-01).
- **Address entry.** Places autocomplete with an API key restricted to the ColPool app and the Places API. Only a chosen suggestion is accepted (RC-13).
- **Screens.**
  - Splash, Sign up / Sign in with the consent statement, Onboarding, Profile, Edit Profile and log out.
  - Predefined options and the RC-30 limits are enforced in the UI.
  - The student tab bar has placeholder tabs.
- **Seed data.**
  - The five campus points with precise coordinates (RC-11).
  - The predefined lists of interests, majors, years, traits and genders (RC-22). Interests, years, traits and genders follow the prototype's lists. The CBU majors list will be supplied by the project owner before this work starts, and agents must not invent one.
- **Baseline Security Rules with emulator tests.**
  - Private details: owner only.
  - Public profiles: the owner or permitted viewers only.
  - Students can write only their own profile, within the RC-30 limits and predefined values.
  - No client writes to consent or permission records.
  - Institution accounts can't read profiles.

### Phase Acceptance Criteria

- **Sign-up rules.** Sign-up fails for a non-CBU email, a password under 8 characters, a missing name, or unchecked consent. A successful sign-up creates the account and its consent record together.
- **Consent is enforced.** An account created directly in Firebase Authentication, without a consent record, can't use the app.
- **Onboarding.** A new student can't finish onboarding without choosing a starting address from the suggestions; typed free text is rejected. A student who quits onboarding and signs in later is sent back to it.
- **Staff routing.** A staff account reaches only Export Carpool Data, with no tabs, and emulator tests show it can't read profiles.
- **Privacy.** Emulator tests show:
  - another student can't read a student's starting address or email
  - profiles can't be read without permission
  - profile writes outside the RC-30 limits or predefined options are rejected by the rules, not just the UI
- **Secrets.** The repository contains no secrets, and the Maps key only works for the ColPool app and the Places API.

### Dependencies & Sequence Notes

This phase has no prerequisites and comes first. Order within it:

1. Set up the project and settle the data model and permission structure first. Every rule, function and screen depends on them.
2. Then the sign-up function, the screens and the rules can proceed in parallel.

Places autocomplete needs a Google Maps Platform billing account, so that setup belongs in this phase. The campus-point coordinates have to be collected from CBU's campus before seeding.

### Relevant UI/UX Artifacts

Prototype screens:

- Splash
- Sign up / Sign in, including the consent statement and the "no password reset" note
- Onboarding
- Profile
- Edit Profile
- the header and log-out button of Export Carpool Data

Figure 2: Entry, Profile tab and University staff.

### Candidate GitHub Issues

- Set up Firebase project and billing
- Create Android app connected to Firebase
- Configure Firebase Emulator Suite for local testing
- Define core Firestore schema and profile-permission model
- Implement sign-up function with CBU domain check and consent record
- Implement sign-in, role routing and app shells
- Add developer script to create institution accounts
- Integrate Places autocomplete with a restricted API key
- Build Onboarding screen
- Build Profile and Edit Profile screens
- Seed campus points and profile option lists
- Write baseline Security Rules with emulator tests

## 3.2 Phase 2 – Hosting a Ride Series

### Goal

A host can create a ride series with its own schedule for each weekday, including stops and campus points. Then they:

- see the route and times calculated on the backend at review
- post it, subject to the posting rechecks and hosting limits
- find it in the Hosting tab

The host's profile becomes visible to all students while the series is active. No one can request the series yet.

### Source Requirements / Design References

US-03, US-04, US-05 (schedule and seats only); RC-01 (hosts of active series), RC-04, RC-11, RC-12, RC-13, RC-14, RC-18, RC-28, RC-29, RC-30, RC-31; DD-02, DD-03, DD-06, DD-07, DD-10, DD-12; RISK-02, RISK-06, RISK-08, RISK-09.

Figure 1: Cloud Functions and the Routes API. Figure 2: Hosting tab.

### Deliverables

- **Series data model.** The series record holds:
  - the pickup spot with coordinates, dates, price, vehicle and maximum riders
  - each weekday's schedule: departure times, ordered stops with minutes, and campus drop-off and pickup points
  - the stored route results for each weekday and direction
  - seat counts for each weekday
  - a status (active, cancelled or ended)
  - the fields search will need, such as open weekdays and pickup coordinates

  Departure times are stored as local clock times in the university's time zone (RC-18).
- **Server-side draft.** The host's draft is kept on the server with its calculated route and a marker showing exactly what was reviewed.
- **Shared series validation**, used at review and again at posting:
  - required fields and the RC-30 ranges
  - the date rules
  - at least one matching date
  - leaving campus no earlier than the expected arrival
  - the RC-31 hosting limits
- **Route-preview function.**
  - It calls the Routes API once for each weekday and direction, without live traffic.
  - It calculates expected times as RC-14 describes.
  - It names the address that failed if a route can't be calculated.
  - The Routes key stays on the server (RC-04).
- **Post function.**
  - It reruns the start-date, matching-date and RC-31 checks.
  - It confirms the draft hasn't changed since review.
  - It saves exactly the reviewed results.
  - It ignores a duplicate tap.
  - It makes the host's profile visible to all students (RC-01).
- **Create Series screen.**
  - Pickup spot and stops are entered with Places autocomplete, with the public-place warning.
  - Dates, price ($1–99), vehicle description, maximum riders (1–6) and weekday toggles.
  - Each weekday and direction gets its own schedule, with stops (0–120 minutes) and campus points from the seeded list.
- **Review Calculated Route screen.**
  - Day tabs, with a timeline for each direction showing miles, minutes and expected times.
  - The "can't be edited" warning.
  - Go back, or post with confirmation.
- **Hosting list and Hosted Series Details.** The schedule and open seats per weekday. The riders and requests sections show empty states until Phases 3–4. There's no cancel button yet (Phase 4).
- **Security Rules with emulator tests.**
  - Series and drafts can be written only by functions.
  - Series can be read by students.
  - Drafts can be read only by their host.

### Phase Acceptance Criteria

- **Validation.** Review rejects a series, with a clear message, if any of these is true:
  - a field is missing or out of range
  - the start date is in the past
  - no date falls on its weekdays
  - it leaves campus before arriving
  - it breaks RC-31

  RC-31 tests show that cancelled and ended series don't count, using seeded data.
- **Route calculation.**
  - Review shows miles, minutes and expected times for each weekday and direction.
  - Unit tests with fixed leg data confirm each expected time equals the departure plus earlier legs plus earlier stop minutes.
  - An address that can't be routed produces an error naming that address.
- **Posting.**
  - Posting saves exactly the reviewed values.
  - Changing the draft after review, or posting after the start date has passed, fails and requires another review.
  - Rules tests show the app can't write series or route values directly.
- **Viewing.** A posted series appears in the Hosting list and Hosted Series Details with the correct open seats per weekday, and it can't be edited.
- **Host visibility.** Another student can read the host's profile once the series is active.
- **Maps usage.** The Routes key appears nowhere in the app or repository, and routes are calculated only at review (RC-28).

### Dependencies & Sequence Notes

This phase needs Phase 1's sign-in, permission model, campus points and Places autocomplete. It also needs the Routes API enabled, with a server-side key kept in the backend's secret storage.

Order within it:

1. Settle the series data model first. Phase 3's search depends on its open-weekday and pickup-coordinate fields.
2. Once the draft-and-review contract is settled, the screens and functions can proceed in parallel.

Cancelling a series is deferred to Phase 4, so RC-31's exclusion of cancelled and ended series is tested with seeded data here.

Shared series validation is a separate work item because review and posting both run it. Written twice, the two copies could drift apart, which is exactly the mistake RC-12's posting rechecks are meant to catch.

### Relevant UI/UX Artifacts

Prototype screens:

- Hosting, including the empty state
- Create Series: the pickup-spot hint, the add-stop sheet with minutes and the stop warning, and the campus-point chips
- Review Calculated Route: the warning note and the post confirmation sheet
- Hosted Series Details: the summary card, day tabs and schedule timelines

Figure 2: Hosting tab.

Don't build "Copy schedule to the other days," which exists only in the prototype. The prototype's miles and minutes are sample estimates, not expected values.

### Candidate GitHub Issues

- Define series, draft and seat data model, with its Security Rules
- Implement shared series validation, including RC-31 hosting limits
- Implement route-preview function with the Routes API
- Implement post-series function with rechecks and host visibility
- Build Create Series screen
- Build Review Calculated Route screen
- Build Hosting list and Hosted Series Details

## 3.3 Phase 3 – Search, Requests & Notifications

### Goal

A rider can:

- find series near their starting address on the weekdays they need
- see a match percentage based on their preferences
- open a series to see its full schedule and the host's profile
- request the weekdays that fit

The host sees the request on their series, can open the requester's profile, and is notified. The notification system (banner, history and unread badges) works from this phase on. Hosts can't accept or decline yet; that comes in Phase 4.

### Source Requirements / Design References

US-07, US-09, US-10, US-11, US-14 (host viewing requesters), US-15 (new request), US-16; RC-01 (requesters), RC-09, RC-16, RC-19, RC-20, RC-21, RC-22, RC-26, RC-27, RC-28, RC-29; DD-04, DD-11, DD-13; RISK-01.

Figure 1: the app reading Firestore directly, Cloud Functions, and Firestore listeners for notifications. Figure 2: Search tab, Hosting tab (requests) and Notifications tab.

### Deliverables

- **Search.**
  - The rider picks weekdays. The search finds active series with an open seat on at least one of them, using the copied open-weekday field from Phase 2.
  - It excludes the rider's own series and any series they've already requested.
  - Results are ranked on the device by straight-line distance from the rider's starting coordinates. No Maps call is made (RC-19, RC-28).
  - Each result shows its distance, weekday chips (open, full or not needed), the times for the first matching day, and the price.
- **Preferences and match percentage.**
  - Interests and traits allow any number of choices; major and year allow one each (RC-20).
  - Interests, major and traits count only on exact matches, and year counts by how close it is (RC-21), using the formula recorded in Section 6.
  - The percentage never changes the order of results, and clearing all preferences removes it.
- **Series Details.**
  - The host's profile, with matched preferences highlighted.
  - Price, dates, maximum riders and vehicle.
  - Day tabs with open seats, and schedule timelines.
  - A weekday picker for the request, with full days and days the series doesn't run disabled.
  - The note that drivers aren't verified (RISK-01).
- **Send-request function.**
  - It checks that each chosen weekday has an open seat, that the series isn't the rider's own, and that the rider has never requested it before. That last check is enforced with one request record per rider and series (RC-16).
  - It creates the pending request.
  - It lets the host view the requester's profile (RC-01).
  - It notifies the host.
- **Incoming requests for the host.**
  - Hosted Series Details lists every request with at least one pending weekday, showing the requester's name and each weekday's status.
  - The Requester Profile screen.
  - A badge on the Hosting tab counting requests with pending days.
  - Accept and decline are wired up in Phase 4.
- **Shared notification writer.**
  - One function used by every backend action to write notifications.
  - Each notification has a recipient, event type, related series, timestamp and read flag.
  - The event types cover every US-15 event now, even though most are first sent in Phase 4.
- **Notification display.**
  - A Firestore listener shows a banner that disappears after a few seconds.
  - The Notifications tab lists history newest first, marks unread entries, and shows a tab badge.
  - Opening the tab marks entries as read.
- **Security Rules with emulator tests.**
  - Requests can be read only by their rider and the series' host, and created only through the function.
  - Notifications can be read only by their recipient and created only by functions. The client may only mark them read.

### Phase Acceptance Criteria

- **Search results.**
  - Search shows only active series with an open seat on at least one chosen weekday, nearest first, with the distance shown.
  - It never shows the rider's own series or series they already requested.
  - No Maps call is made during search.
- **Match percentage.** It appears only when preferences are set, follows RC-21 and the Section 6 formula, and doesn't change the order. Clearing preferences removes it.
- **Request rules.**
  - A request is accepted only when every chosen weekday has an open seat, the series isn't the rider's own, and it's their first request to that series.
  - A second request is rejected by the backend even if the UI is bypassed.
- **Host view and privacy.** The host sees the new request with each weekday's status and can open the requester's profile. A rules test shows an unrelated student can't read that profile.
- **Notifications.**
  - The host gets a banner and a history entry, and the unread badges update.
  - History is newest first and shows the event type, the series and a timestamp.
  - Search speed (RC-26) and notification delivery time (RC-27) are measured formally in Phase 6.

### Dependencies & Sequence Notes

This phase needs Phase 2's series model, especially the open-weekday and pickup-coordinate fields, and Phase 1's private starting coordinates and permission model.

The notification writer's contract should be settled before the send-request function, since that function writes the first notification. Phase 4's functions reuse the same writer. Search and the match percentage can be built in parallel with requests and notifications.

Interim gaps until Phase 4:

- A rider's sent request is confirmed on Search but isn't listed for them anywhere; the Riding tab comes in Phase 4.
- Search relies on each series' status. Series whose end date has passed are marked ended by the daily job, which is also Phase 4.

### Relevant UI/UX Artifacts

Prototype screens:

- Search: day row, preferences panel, result cards, empty state and footnote
- Series Details: host card, summary tiles, schedule, request day picker and safety note
- Hosted Series Details: the Requests section, plus the Hosting tab badge
- Requester Profile
- Notifications: the list and "New" tags, plus the tab badge and banner

The prototype's Accept and Decline buttons belong to Phase 4.

### Candidate GitHub Issues

- Build Search screen with weekday filter and distance ranking
- Implement preference panel and match percentage
- Build Series Details with host profile and request day picker
- Implement send-request function with one request per series, with request Security Rules
- Show incoming requests and requester profiles to the host
- Build shared notification writer and notification records, with notification Security Rules
- Build notification banner, history and unread badges

## 3.4 Phase 4 – Request & Series Lifecycle

### Goal

Everything that happens to a request and a series after the request is sent:

- Hosts accept or decline each requested weekday, and can remove a rider from a weekday.
- Riders see their requests in the Riding tab, see who they ride with, and can leave or withdraw weekdays.
- Hosts can cancel a series, and a daily job ends series whose end date has passed.
- Everyone affected is notified of each event.

This phase also records the timestamps that the Phase 5 export counts from.

### Source Requirements / Design References

US-05 (riders per weekday), US-06, US-08, US-12, US-13, US-14, US-15 (remaining events); RC-01 (co-riders and hosts of requested series), RC-15, RC-16, RC-17, RC-18, RC-24; DD-05, DD-13, DD-14; RISK-03, RISK-10.

Figure 1: Cloud Functions writing seats, requests, permissions and notifications, and the daily job. Figure 2: Hosting tab (Hosted Series Details) and Riding tab.

### Deliverables

- **Accept/decline function**, for one pending weekday at a time.
  - A weekday can be accepted only if it has an open seat.
  - In one transaction it updates the seat count, the request's weekday status, the acceptance timestamp and the series' open-weekday field (RC-15).
  - When the accepted weekday fills up, that weekday is declined automatically in every other pending request that includes it. Each affected rider is notified, and their other weekdays are left alone.
  - The rider is notified of each decision, one notification per weekday (US-15).
  - Profile permissions are updated so riders accepted on the same weekday can see each other (RC-01).
  - A request drops off the host's list once none of its weekdays are pending (US-08).
- **Remove-rider function.** The host removes a rider from one accepted weekday. It reopens that seat, records the removal timestamp, notifies the rider and updates permissions.
- **Leave and withdraw function.**
  - The rider leaves an accepted weekday or withdraws a pending one, one at a time or all at once.
  - Leaving reopens the seat.
  - It records the timestamp, notifies the host ("rider left" or "request withdrawn") and updates permissions.
  - Nothing a rider leaves, withdraws, is removed from or is declined for can be rejoined (RC-16, DD-13).
- **Cancel-series function.**
  - The host confirms, and the cancellation time is recorded.
  - The series leaves search and every list, and pending weekdays are closed.
  - Everyone with a pending or accepted request is notified (US-06).
  - The host stays visible to all students only if they still host another active series.
- **Daily end-of-series job.**
  - It runs on the server's clock in the university's time zone (RC-17, RC-18).
  - It marks series ended once their end date has passed, notifies the host and riders, and updates search fields and permissions.
  - It's safe to run twice.
  - The ended series' records are kept (RC-24).
- **Hosted Series Details, completed.**
  - Riders listed per weekday, each with a Remove button.
  - Accept and Decline for each pending weekday; Accept is disabled when the weekday is full.
  - The Cancel this series button.
- **Riding tab.** Pending and accepted requests on active series, with each weekday's status, the host, the price and the pickup spot.
- **Request Details.**
  - A status summary and the schedule for pending and accepted weekdays.
  - "People on [weekday]": the host, plus co-riders on accepted weekdays the rider shares.
  - "Your days," with Leave or Withdraw buttons.
  - Leave all days or Cancel request.
  - Every action asks for confirmation. The rider stays on Request Details while they have live weekdays, and returns to Riding once none remain (Figure 2).
- **Weekday rosters.** A list of the riders accepted on each weekday, kept by the backend and readable only by the host and by riders accepted on that weekday. Riders can't read each other's request records, so Request Details reads co-riders from the roster (RC-01).
- **Host / Co-rider Profile screen** for riders.
- **Timestamp contract.** The acceptance, leave, removal and cancellation times are stored per weekday as server timestamps. Phase 5's trip counting (DD-14) depends on them.
- **Tests.**
  - Concurrency tests on the seat logic.
  - Security Rules tests for co-rider and host profile visibility.
  - Tests confirming the client can't write requests, seats or series.

### Phase Acceptance Criteria

- **Seats.**
  - Accepting a weekday lowers its open seats by one, and a full weekday can't be accepted.
  - Deciding one weekday doesn't change the request's other weekdays.
  - Removing or leaving reopens exactly one seat on that weekday.
- **Last seat.** In emulator tests, two accepts racing for the last seat on a weekday result in exactly one success. An accept racing a rider leaving also stays consistent (RC-15, RISK-03).
- **Auto-decline.** When a weekday fills, its pending entries in other requests are declined automatically and those riders are notified. Their other weekdays are unchanged.
- **No rejoining.** A rider can't rejoin a weekday they left, withdrew, were removed from or were declined for, and can't send a second request. The backend enforces this.
- **Riding view.**
  - The Riding tab shows every pending and accepted request on active series, with each weekday's status.
  - Co-rider profiles can be opened only for shared accepted weekdays, and rules tests block every other case.
  - Rules tests show a rider can read the roster only for weekdays they're accepted on, and can't read other riders' request records.
- **Cancellation.** Cancelling asks for confirmation. The series then disappears from search and from the host's and riders' lists, and everyone with a pending or accepted request is notified.
- **Ending.**
  - The daily job ends series using the server's clock.
  - Ended series disappear from search and lists, and the host and riders are notified.
  - The records are still in the database.
- **Notifications.** Every US-15 event in this phase produces one notification per affected user, and one per weekday decision.
- **Timestamps.** Every acceptance, leave, removal and cancellation stores a server timestamp.

### Dependencies & Sequence Notes

This phase needs Phase 3's requests, notification writer and permission handling.

The seat transaction carries the most risk, so build accept/decline first. Remove and leave should reuse the same seat-update code rather than repeat it.

Settle the timestamp contract early. Once it's fixed, Phase 5's trip counting can start in parallel with this phase's screens. The daily job runs as a scheduled Cloud Function.

### Relevant UI/UX Artifacts

Prototype screens:

- Hosted Series Details: "Riders by day" with Remove; the Requests section with Accept, Decline and the Full state; "Cancel this series" and its confirmation sheet
- Riding: the list and its empty state
- Request Details: the status card, "People on [weekday]", "Your days," and the leave and withdraw confirmation sheets
- Host / Co-rider Profile
- Notifications: the wording for each remaining event

The prototype's "Simulate other students" buttons describe events worth testing, but they aren't part of the app.

### Candidate GitHub Issues

- Define per-weekday timestamp contract for trip counting
- Implement per-weekday accept/decline with atomic seats and auto-decline, with seat concurrency tests
- Implement remove-rider function
- Implement leave and withdraw function
- Add accept, decline and remove-rider actions to Hosted Series Details
- Build Riding tab and Request Details
- Maintain weekday rosters and profile permissions through the request lifecycle, with their Security Rules
- Implement cancel-series function and confirmation
- Implement daily end-of-series job

## 3.5 Phase 5 – Institution Export

### Goal

University staff choose a date range, generate a CSV of scheduled carpool trips that opens in Excel, and share it through Android's share menu. Trip counts and miles follow RC-23 and DD-14 exactly.

### Source Requirements / Design References

US-17; RC-05 (no starting address in the export), RC-07, RC-18, RC-23, RC-24, RC-28; DD-08, DD-14; RISK-05; OQ-04 (deferred).

Figure 1: the app calling Cloud Functions and receiving the CSV file. Figure 2: University staff.

### Deliverables

- **Shared trip-counting module**, on the backend.
  - Given series and request records, it returns the trips that count for each rider and host, by the departure-time rule (RC-23, DD-14).
  - A host's trip counts only if at least one rider's trip counts on it.
  - There is one implementation, used by the export and by any later check.
  - It's structured so a no-class-days filter could be added later (OQ-04), but none is built now.
- **Export function.**
  - It accepts calls only from accounts with the institution claim, and checks the date range.
  - It builds one row per student, series and Monday–Sunday week that has at least one counted trip, using dates in the university's time zone (RC-18).
  - Each row has: name, email, role (host or rider), host name, pickup spot, trip dates, trip count, estimated miles and mile type (passenger or vehicle).
  - The file is labelled as scheduled trips, not verified ones.
  - It never includes a starting address (RC-05) and makes no Maps calls (RC-28).
- **CSV format.** The file opens cleanly in Excel: UTF-8 with a byte-order mark, and quoted fields. Names with accents or commas survive intact.
- **Export Carpool Data screen.**
  - Date range fields and Generate export.
  - A summary showing rows, students, rider trips, and passenger and vehicle miles, kept separate.
  - Sharing through Android's share menu.
- **Fixture tests.** Fixed sample data covering the edge cases:
  - a weekday accepted before and after that day's departure
  - leaving or being removed before and after a departure
  - a series cancelled partway through a week
  - the series' end date
  - a host date with and without a counted rider
  - week boundaries
  - the week daylight saving time ends

### Phase Acceptance Criteria

- **Access.** Only institution accounts can run the export. Student accounts are rejected by the backend, and staff accounts can't read anything else (RC-07).
- **Counting.** The fixture tests pass for every edge case listed, and host and rider counts agree.
- **File contents.**
  - The CSV opens in Excel with the specified columns and labels.
  - Passenger and vehicle miles are clearly labelled, and names keep special characters.
  - No starting address appears anywhere.
- **Date handling.**
  - Rows are grouped into Monday–Sunday weeks in Pacific time.
  - Trips from cancelled and ended series appear only for departures before the cancellation or end.
- **Sharing.** The file can be shared through Android's share menu, for example to email or Google Drive.

### Dependencies & Sequence Notes

This phase needs Phase 4's timestamp contract and series-ending behavior, and Phase 1's staff routing.

The trip-counting module and its fixture tests can start as soon as the timestamp contract is settled, in parallel with Phase 4's screens. The export function and screen follow once the module passes its tests.

### Relevant UI/UX Artifacts

Prototype screen: Export Carpool Data, including the date fields, Generate export, the summary card with the "Scheduled" label and separate passenger and vehicle mile tiles, the Share button, and the staff header with log out.

Figure 2: University staff.

The prototype's on-screen table is only a preview (SDD §6.2). The deliverable is the CSV file, and the screen doesn't need to show every row.

### Candidate GitHub Issues

- Implement shared trip-counting module using the departure-time rule, with fixture tests
- Implement institution export function and CSV output, with fixture tests
- Build Export Carpool Data screen with Android share

## 3.6 Phase 6 – Integrated Validation & MVP Hardening

### Goal

Show that the complete MVP meets every SDD pass/fail check and constraint. Test the highest risks (privacy, seat concurrency, performance and Maps usage) and fix the defects found. Introduce no new scope.

### Source Requirements / Design References

US-01–US-17; RC-01–RC-31; DD-01–DD-14; RISK-01–RISK-11; all prototype flows; Figures 1 and 2.

### Deliverables

- **Acceptance checklist.** Every user story's pass/fail check is mapped to a demonstration or test, with the result recorded.
- **Security and privacy review.**
  - Run the full Security Rules test suite for RC-01, RC-05 and RC-07.
  - Try direct client writes to series, seats, requests, consent, permissions and notifications, and confirm they're blocked.
  - Confirm that no secrets are in the app or repository and that API keys are restricted.
- **Concurrency tests.** Repeated tests of the last seat and of an accept racing a leave (RISK-03).
- **Performance checks.** On an Android Emulator device (Android Studio), with a seeded dataset sized for the MVP:
  - at least 95% of searches return within 3 seconds (RC-26)
  - at least 95% of notifications arrive within 10 seconds (RC-27)
- **Maps usage check.** During a scripted session, the only Maps calls are address autocomplete and route calculation at review (RC-28), and the budget alerts are in place.
- **Polish.**
  - User-facing terms are consistent with the SDD (RC-29), and times use the 12-hour format in Pacific time (RC-18).
  - Basic accessibility matches the prototype: touch target sizes and screen-reader labels.
- **Known limitations.** The deferred OQ-04 and OQ-05 and the accepted risks are documented for the demo.
- **Fixes** for the highest-priority defects found.

### Phase Acceptance Criteria

- **User stories.** Every US-01–US-17 pass/fail check is demonstrated or tested, and recorded as passing.
- **Access.** No known path lets a student:
  - read another student's starting address or email
  - open a profile RC-01 doesn't allow
  - write seats, requests or series directly

  Staff accounts can do nothing beyond the export.
- **Concurrency.** The concurrency tests pass on every run.
- **Performance.** The RC-26 and RC-27 targets are met on the test dataset.
- **Flows.** The main student and staff flows match the prototype, or differ only where SDD §6.2 says they do.
- **Limitations.** Known limitations are documented, not hidden.

### Dependencies & Sequence Notes

This phase needs Phases 1–5. Fixes may return to earlier phases' areas, but no new product scope is added during hardening without an approved SDD change.

The acceptance checklist can be started earlier, as each phase finishes.

### Relevant UI/UX Artifacts

All prototype flows, especially:

- sign-up and onboarding
- creating, reviewing and posting a series
- searching and requesting
- the host's decisions and the rider's Riding tab
- notifications
- the staff export

### Candidate GitHub Issues

- Create SDD acceptance checklist and record results
- Run security, privacy and concurrency review
- Measure search and notification performance
- Verify Maps usage and budget alerts
- Fix highest-priority defects
- Final terminology, time-format and accessibility polish

# 4 Cross-Phase Dependencies & Implementation Sequence

**The critical path** runs through the shared contracts that later work builds on:

1. Phase 1's data model, profile-permission model and Security Rules test setup
2. Phase 2's series data model, especially seats per weekday and the copied fields search needs
3. Phase 3's send-request function and notification writer
4. Phase 4's seat transaction and timestamp contract
5. Phase 5's trip-counting module

A delay or change in any of these holds up everything after it. That's why each one is a separate issue.

**Shared components** that many later items depend on:

- the Phase 1 data model and permission model
- the Security Rules test setup in the Firebase Emulator Suite
- shared series validation (Phase 2)
- the notification writer (Phase 3)
- the seat-update code (Phase 4)
- the timestamp contract (Phase 4)
- the trip-counting module (Phase 5)

**Parallel work** is possible once a contract is settled:

- **Phase 1:** screens, the sign-up function and rules can proceed side by side.
- **Phase 2:** screens and functions can proceed side by side once the draft-and-review contract is set.
- **Phase 3:** search and the match percentage can be built alongside requests and notifications.
- **Phases 4 and 5:** trip counting can start as soon as the timestamp contract is fixed.
- **Phase 6:** the acceptance checklist can grow as each phase finishes.
- **Early setup:** collecting campus-point coordinates and setting up Maps billing can happen alongside early Phase 1 work.

**Sequencing rules agents must not break:**

- **Rules come with features.** No feature is merged without its Security Rules tests. Client write access to series, seats, requests, consent, permissions or notifications is never opened, not even temporarily as scaffolding.
- **Contract changes are their own issue.** The data model, notification writer, timestamp contract and trip-counting module aren't changed inside a feature issue. Changing one is a separate, reviewed issue, because later work depends on it.
- **One copy of each shared piece of logic.** Review and posting share one validation. Accept, remove, leave and cancel share one seat-update path. The export uses the one trip-counting module. None of these is written a second time.
- **The Routes API is called only from route preview.** Search, viewing a series and the export never call a Maps service (RC-28).
- **Server time decides.** Ended series, whether a trip counts, and the export use the server's clock in the university's time zone, never the device's (RC-17, RC-18).

## 4.1 Dependency / Sequence Table

| Item / Capability | Must Follow | Reason | Can Run in Parallel With |
|---|---|---|---|
| Data model and profile-permission model (P1) | Project setup | Every rule, function and screen uses these record shapes | Collecting campus-point coordinates; Maps billing setup |
| Security Rules test setup (P1) | Data model | Every later phase adds its rules tests to this setup | Sign-up function; Onboarding and Profile screens |
| Series data model (P2) | Phase 1 data and permission models | Search, requests and the lifecycle all read its seat and copied fields | Create Series screen layout |
| Shared validation → route preview → post (P2) | Series data model; Routes API key in backend secret storage | Posting must save exactly what was reviewed, checked by the same validation (RC-12, RC-31) | Review screen; Hosting list and details |
| Search and match percentage (P3) | Series data model; private starting coordinates (P1) | The search query and ranking use these fields | Send-request function; notification writer |
| Notification writer (P3) | Phase 1 rules test setup | The first notification comes from send-request; every Phase 4 function and the daily job reuse it | Search and match percentage |
| Send-request and incoming requests (P3) | Series data model; notification writer | Phase 4 builds on the one-request record and the requester permission | Banner and notification history |
| Accept/decline seat transaction (P4) | Send-request; timestamp contract | The riskiest logic; remove, leave and cancel reuse its seat-update code | Riding tab and Request Details screens |
| Remove, leave/withdraw, cancel, weekday rosters (P4) | Accept/decline seat-update code | They must change seats and rosters the same way to keep RC-15 | Daily end-of-series job |
| Timestamp contract (P4) | Series data model (P2) | Accept, remove, leave and cancel record these timestamps, and trip counting is defined by them (DD-14) | Phase 3 work; Phase 4 screens |
| Trip-counting module and fixture tests (P5) | Timestamp contract | The export and Phase 6 checks depend on it being correct | Phase 4 screens; Export screen layout |
| Export function and CSV (P5) | Trip-counting module; staff routing (P1) | Rows are built from counted trips | Phase 6 acceptance checklist |
| Integrated validation (P6) | Phases 1–5 | Needs every role, data path and notification event in place | Defect fixes; polish |

# 5 GitHub Issue Decomposition Guidance

Each candidate issue in Section 3 becomes one GitHub Issue, with the same title. The inventory in 5.1 gives each a temporary ID (TMP-01 to TMP-51; seven IDs were retired when small issues were merged into their neighbors, leaving 44 issues), its own SDD sources, what it depends on, and the visual context and constraints an agent needs. Once the issues exist, the TMP IDs in the "Depends On" column are replaced with real issue numbers, and the same dependencies are recorded as GitHub "blocked by" relationships.

**An agent-ready ColPool issue contains:**

- **Title** exactly as in 5.1, stating a concrete outcome.
- **Goal:** two or three sentences on what exists when the issue is done, taken from the phase's deliverables.
- **Phase and TMP ID**, with a link to this plan's phase section.
- **SDD sources:** the IDs from 5.1. Copy in only the specific SDD pass/fail sentences this issue must satisfy, not whole sections.
- **Acceptance criteria:** checkable items drawn from the phase acceptance criteria, including the tests required.
- **Visual context:** the prototype screen names from 5.1 and the prototype link. The prototype is a reference, not an authority; the SDD wins where they differ.
- **Dependencies and non-goals:** the blocking issues, any contract issue it relies on, and what not to build. Prototype-only features and SDD §2.3 exclusions are listed when they're tempting for that screen.
- **Guardrails:** a pointer to the sequencing rules in Section 4 and the guardrails in §6.3, plus any that matter most for this issue.
- **Tests expected:**
  - Security Rules changes: emulator tests.
  - Functions: unit tests, run against the emulator.
  - Time-based logic: fixed sample data, including a daylight-saving week.
  - Screens: a check on a device against the named prototype screen.
- **Likely files or components** when known, such as `firestore.rules`, a named Cloud Function, or the screen in the Android app.

**Where the details come from.** The 5.1 table gives each issue's sources, dependencies and visual context. Its constraints, non-goals and tests come from the phase's Deliverables and Acceptance Criteria in Section 3, the sequencing rules in Section 4, and the guardrails in §6.3.

**Contract issues.** A contract is a shared definition that other code depends on: the shape of a record, a shared function, or a rule several features must apply the same way. Six issues create contracts:

- TMP-04 and TMP-14: data models
- TMP-15: shared series validation
- TMP-28: notification writer
- TMP-31: timestamp contract
- TMP-41: trip counting

Their bodies say so, and they get a `contract` label on the board. If an agent working on another issue finds it can't finish without changing a contract, it stops, and a separate contract-change issue is opened. A contract is never changed inside a feature issue, because other issues already depend on it.

**Sizing.** An issue should be finishable in one focused implementation-and-review cycle. TMP-50 is the exception: it's a tracking issue, and each real defect gets its own issue linked to it.

## 5.1 Candidate Issue Inventory

| ID | Issue Title | Phase | SDD Sources | Depends On | Notes / Visual Context |
|---|---|---|---|---|---|
| TMP-01 | Set up Firebase project and billing | 1 | RC-04, RC-28; RISK-08 | None | Figure 1: Firebase / Google Cloud and Google Maps Platform. |
| TMP-02 | Create Android app connected to Firebase | 1 | US-01; RC-04, RC-25 | TMP-01 | Figure 1: Android device (ColPool app). |
| TMP-03 | Configure Firebase Emulator Suite for local testing | 1 | RC-01, RC-05, RC-07 | TMP-01 | Figure 1: Firebase Authentication, Cloud Functions, Cloud Firestore + Security Rules. |
| TMP-04 | Define core Firestore schema and profile-permission model | 1 | US-01, US-02; RC-01, RC-05, RC-06 | TMP-01 | Figure 1: Cloud Firestore + Security Rules. Prototype: Profile and Edit Profile (fields a profile holds). |
| TMP-05 | Implement sign-up function with CBU domain check and consent record | 1 | US-01; RC-02, RC-06, RC-30; DD-01, DD-09 | TMP-04 | Prototype: Sign up / Sign in (Create account form, consent statement). Figure 1: app → Cloud Functions → Authentication. Figure 2: Entry. |
| TMP-06 | Implement sign-in, role routing and app shells | 1 | US-01, US-02; RC-06, RC-07, RC-10 | TMP-02, TMP-04, TMP-05 | Prototype: Splash; Sign up / Sign in; student tab bar; Export Carpool Data header and log out. Figure 2: Entry and University staff routing. |
| TMP-07 | Add developer script to create institution accounts | 1 | US-01; RC-06, RC-07 | TMP-04 | Figure 1: Firebase Authentication (custom claim: institution accounts). No app screen. |
| TMP-08 | Integrate Places autocomplete with a restricted API key | 1 | RC-04, RC-13, RC-28 | TMP-01, TMP-02 | Prototype: address search sheet (search field and suggestions). Figure 1: app ↔ Places. |
| TMP-09 | Build Onboarding screen | 1 | US-02; RC-03, RC-05, RC-13, RC-22, RC-30 | TMP-06, TMP-08, TMP-12, TMP-13 | Prototype: Onboarding. Figure 2: Entry (Onboarding). |
| TMP-10 | Build Profile and Edit Profile screens | 1 | US-02; RC-03, RC-05, RC-08, RC-22, RC-29, RC-30 | TMP-06, TMP-09, TMP-13 | Prototype: Profile (starting address and email cards, Edit profile, Log out); Edit Profile. Figure 2: Profile tab (Profile ↔ Edit Profile). Absorbs former TMP-11. |
| TMP-12 | Seed campus points and profile option lists | 1 | RC-11, RC-22 | TMP-04 | Prototype: Create Series campus-point chips; Onboarding and Edit Profile option chips. Needs human input before implementation: precise campus-point coordinates and the CBU majors list. |
| TMP-13 | Write baseline Security Rules with emulator tests | 1 | RC-01, RC-03, RC-05, RC-07, RC-22, RC-30 | TMP-03, TMP-04 | Figure 1: Cloud Firestore + Security Rules. |
| TMP-14 | Define series, draft and seat data model, with its Security Rules | 2 | US-03, US-05; RC-01, RC-12, RC-15, RC-18 | TMP-04, TMP-13 | Prototype: Create Series and Hosted Series Details (what a series holds). Figure 1: Cloud Firestore; Security Rules. Absorbs former TMP-22. |
| TMP-15 | Implement shared series validation, including RC-31 hosting limits | 2 | US-03; RC-12, RC-30, RC-31 | TMP-14 | Prototype: Create Series error box ("Fix these before continuing"). Figure 2: Create Series → Review Calculated Route. |
| TMP-16 | Implement route-preview function with the Routes API | 2 | US-04; RC-04, RC-12, RC-14, RC-28; DD-07 | TMP-15 | Prototype: Review Calculated Route timelines. Figure 1: Cloud Functions ↔ Routes API. |
| TMP-17 | Implement post-series function with rechecks and host visibility | 2 | US-03, US-04; RC-01, RC-12, RC-31; DD-06 | TMP-13, TMP-16 | Prototype: Review Calculated Route post confirmation sheet; Series Details "Your host" card. Figure 2: Review → post → Hosting. Absorbs former TMP-21. |
| TMP-18 | Build Create Series screen | 2 | US-03; RC-05, RC-11, RC-13, RC-30; DD-10 | TMP-08, TMP-12, TMP-14 | Prototype: Create Series (pickup-spot hint, weekday schedule card, add-stop sheet and stop warning, campus-point chips), without the "Copy schedule" button. Figure 2: Hosting tab. |
| TMP-19 | Build Review Calculated Route screen | 2 | US-04; DD-06; RISK-02 | TMP-16, TMP-18 | Prototype: Review Calculated Route (warning note, day tabs, timelines, post confirmation sheet). Figure 2: Create Series ↔ Review Calculated Route. |
| TMP-20 | Build Hosting list and Hosted Series Details | 2 | US-05; RC-18, RC-29 | TMP-17 | Prototype: Hosting (list and empty state); Hosted Series Details (summary card, day tabs, timelines). Figure 2: Hosting tab. |
| TMP-23 | Build Search screen with weekday filter and distance ranking | 3 | US-09; RC-17, RC-19, RC-26, RC-28 | TMP-09, TMP-14, TMP-17 | Prototype: Search (day row, result cards, "Nearest first", empty state, footnote). Figure 2: Search tab. |
| TMP-24 | Implement preference panel and match percentage | 3 | US-10; RC-20, RC-21; DD-04 | TMP-23 | Prototype: Search preferences panel; match badge on result cards; highlighted matches on the host card. |
| TMP-25 | Build Series Details with host profile and request day picker | 3 | US-09, US-11, US-14; RISK-01 | TMP-17, TMP-23 | Prototype: Series Details (host card, summary tiles, schedule timelines, request day picker, safety note). Figure 2: Search → Series Details → Search. |
| TMP-26 | Implement send-request function with one request per series, with request Security Rules | 3 | US-11; RC-01, RC-16; DD-13 | TMP-14, TMP-17, TMP-28 | Prototype: Series Details request button and "Request sent" banner. Figure 1: app → Cloud Functions → Firestore; Security Rules. Absorbs the request half of former TMP-30. |
| TMP-27 | Show incoming requests and requester profiles to the host | 3 | US-07, US-14 | TMP-20, TMP-26 | Prototype: Hosted Series Details Requests section; Hosting tab badge; Requester Profile. Figure 2: Hosted Series Details → Requester / Rider Profile. |
| TMP-28 | Build shared notification writer and notification records, with notification Security Rules | 3 | US-15, US-16; RC-09, RC-27 | TMP-13 | Prototype: Notifications entries (title, text, series line). Figure 1: Cloud Functions → Firestore (notifications); Security Rules. Absorbs the notification half of former TMP-30. |
| TMP-29 | Build notification banner, history and unread badges | 3 | US-15, US-16; RC-27 | TMP-28 | Prototype: banner; Notifications screen with "New" tags; Notifications tab badge. Figure 1: app listens for notifications. Figure 2: Notifications tab. |
| TMP-31 | Define per-weekday timestamp contract for trip counting | 4 | RC-23; DD-14 | TMP-14 | Figure 1: Cloud Functions → Firestore (requests). No app screen. |
| TMP-32 | Implement per-weekday accept/decline with atomic seats and auto-decline, with seat concurrency tests | 4 | US-08, US-15; RC-15; DD-05; RISK-03 | TMP-26, TMP-28, TMP-31 | Prototype: Hosted Series Details Accept / Decline and "Declined (full)"; "Day accepted" and "Day declined" notifications. Figure 1: Cloud Functions → Firestore. Absorbs the concurrency half of former TMP-40. |
| TMP-33 | Implement remove-rider function | 4 | US-08, US-15; RC-15, RC-16 | TMP-32 | Prototype: "Remove from [day]" button and confirmation sheet; "Removed from [day]" notification. |
| TMP-34 | Implement leave and withdraw function | 4 | US-12, US-15; RC-15, RC-16; DD-13 | TMP-32 | Prototype: Request Details "Your days" Leave / Withdraw buttons and confirmation sheets; "Rider left" and "Request withdrawn" notifications. |
| TMP-35 | Add accept, decline and remove-rider actions to Hosted Series Details | 4 | US-05, US-08 | TMP-27, TMP-32, TMP-33 | Prototype: Hosted Series Details ("Riders by day" with Remove; Requests with Accept, Decline and Full). Figure 2: Hosting tab. |
| TMP-36 | Build Riding tab and Request Details | 4 | US-12, US-13, US-14 | TMP-34, TMP-37 | Prototype: Riding (list and empty state); Request Details (status card, "People on [day]", "Your days", confirmation sheets); Host / Co-rider Profile. Figure 2: Riding tab, including the return to Riding. |
| TMP-37 | Maintain weekday rosters and profile permissions through the request lifecycle, with their Security Rules | 4 | US-14; RC-01 | TMP-32, TMP-33, TMP-34 | Prototype: Request Details "People on [day]". Figure 1: Cloud Functions → Firestore (permissions); Security Rules. Absorbs the rules half of former TMP-40. |
| TMP-38 | Implement cancel-series function and confirmation | 4 | US-06, US-15; RC-17, RC-24 | TMP-32, TMP-37 | Prototype: "Cancel this series" button and confirmation sheet; "Series cancelled" notification. Figure 2: Hosted Series Details → Hosting. |
| TMP-39 | Implement daily end-of-series job | 4 | US-15; RC-17, RC-18, RC-24 | TMP-28, TMP-37 | Prototype: "Series ended" notification. Figure 1: Cloud Functions daily job. |
| TMP-41 | Implement shared trip-counting module using the departure-time rule, with fixture tests | 5 | US-17; RC-23; DD-14 | TMP-31 | Figure 1: Cloud Functions (CSV export). No app screen. Absorbs the trip-counting half of former TMP-44. |
| TMP-42 | Implement institution export function and CSV output, with fixture tests | 5 | US-17; RC-05, RC-07, RC-18, RC-23, RC-24, RC-28; DD-08 | TMP-06, TMP-07, TMP-41 | Prototype: Export Carpool Data table columns (Student through Mile type). Figure 1: app ↔ Cloud Functions (CSV file). Absorbs the export half of former TMP-44. |
| TMP-43 | Build Export Carpool Data screen with Android share | 5 | US-17; DD-08 | TMP-06, TMP-42 | Prototype: Export Carpool Data (date fields, Generate export, summary card, Share button); the on-screen table is only a preview. Figure 2: University staff. |
| TMP-45 | Create SDD acceptance checklist and record results | 6 | US-01–US-17 | Phases 1–5 | All prototype screens; Figure 2 (full flow). |
| TMP-46 | Run security, privacy and concurrency review | 6 | RC-01, RC-04, RC-05, RC-07, RC-15; RISK-03 | TMP-37, TMP-38, TMP-42 | Figure 1: trust boundaries (app ↔ Firestore with Security Rules; Cloud Functions → Firestore). Absorbs former TMP-47. |
| TMP-48 | Measure search and notification performance | 6 | RC-26, RC-27 | TMP-24, TMP-29 | Prototype: Search; banner and Notifications. Figure 1: app ↔ Firestore listeners. Runs on an Android Emulator device; no physical device is available. |
| TMP-49 | Verify Maps usage and budget alerts | 6 | RC-28; RISK-08 | TMP-08, TMP-16, TMP-23 | Figure 1: app ↔ Places; Cloud Functions ↔ Routes API. |
| TMP-50 | Fix highest-priority defects | 6 | As found | TMP-45–TMP-49 | Taken from each linked defect. |
| TMP-51 | Final terminology, time-format and accessibility polish | 6 | RC-18, RC-29 | Phases 1–5 | All prototype screens (wording, times, labels). |

*TMP IDs are temporary. Replace them with GitHub Issue numbers once the issues are created.*

*Retired IDs (merged into neighbors): TMP-11 → TMP-10; TMP-21 → TMP-17; TMP-22 → TMP-14; TMP-30 → TMP-26 and TMP-28; TMP-40 → TMP-32 and TMP-37; TMP-44 → TMP-41 and TMP-42; TMP-47 → TMP-46. Merging keeps each Security Rules and test deliverable inside the feature it protects, as the "rules come with features" sequencing rule in Section 4 requires.*


## 5.2 Created GitHub Issues

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

# 6 Risks, Open Decisions & Agent Guardrails

This section covers implementation-level risks, the technical choices still to be made, and the rules agents must follow. Product risks are already in SDD §7.2 and aren't repeated here.

## 6.1 Implementation Risks

- **Stale profile permissions.** Who can view a profile is kept up to date by Cloud Functions (RC-01). If a function updates a request but fails before updating permissions, someone could keep or lose access they shouldn't. Each function updates permissions and rosters in the same transaction as the change that causes them, and rules tests cover every RC-01 case.
- **Seat races** (RISK-03). Accepting, removing, leaving and cancelling can collide on the same weekday. Seat changes run in Firestore transactions through the one shared seat-update path, and Phase 4 and Phase 6 test the collisions repeatedly.
- **Time zones and daylight saving time.** Departure times are local clock times in America/Los_Angeles. Trip counting and the daily job have to turn them into exact moments correctly, including the weeks daylight saving time starts and ends. The Phase 5 fixture tests include a daylight-saving week.
- **Search query limits.** "An open seat on at least one chosen weekday" needs a copied field listing the open weekdays, a status filter and a matching Firestore index. Ranking happens on the device after the query, which is fine at MVP scale. Beyond that it would need geographic filtering, which is not in scope now (RC-19, RC-26).
- **Maps costs** (RISK-08).
  - API keys are restricted.
  - Budget alerts are set.
  - Places autocomplete uses session tokens, so typing one address is billed as one session.
  - Each trip to the review screen costs one route calculation per weekday and direction.
- **A sign-up that fails partway.** If the sign-up function fails after creating the Auth account and can't remove it, an account is left without a consent record. RC-06 already makes such accounts unusable. The function logs the failure so a developer can delete the account.
- **Late series endings.** The daily job runs once a day, so a series can stay visible for a short time after its end date passes. The job runs shortly after midnight Pacific time, and it's safe to run more than once, so a missed or repeated run does no harm.
- **Campus-point accuracy.** Wrong coordinates would put every route to that point at the wrong spot. The coordinates are checked against the real lot entrances before seeding (Phase 1).

## 6.2 Open Technical Decisions

None of these are product decisions. Each is settled in its first issue and recorded as an architecture decision, so later agents don't decide it again.

- **Android setup:** the UI toolkit (for example, Jetpack Compose) and the minimum Android version. Decided in the Android app issue (Phase 1).
- **Data layout:** the exact Firestore collections, and how profile permissions and weekday rosters are stored. Decided in the data-model issues (Phases 1, 2 and 4).
- **Performance test size:** how many series and students the Phase 6 performance check uses. Decided in that issue, sized for one university at MVP scale.

## 6.3 Agent Guardrails

- **The match formula** (RC-21 gives no formula; this one comes from the prototype):
  - Each chosen preference counts as one item: each interest, each trait, the major and the year.
  - Interests, traits and major score 1 on an exact match and 0 otherwise.
  - Year scores 1 − 0.34 × the number of years apart, never below 0. Freshman, Sophomore, Junior, Senior and Graduate are consecutive steps.
  - If the host left a field empty, preferences on that field score 0.
  - The match percentage is the total score divided by the number of chosen items, rounded to a whole number. With no preferences chosen, no percentage is shown.
- **Scope stays as the SDD defines it.** Don't add any of these (SDD §2.3, OQ-04, OQ-05):
  - push notifications
  - a no-class calendar or skippable dates
  - messaging, payments, ratings or map display
  - GPS or location detection
  - editing a posted series
  - one-way series
  - photos or other media
  - password reset or email verification
- **The SDD wins.** If the SDD and the prototype disagree, follow the SDD. If an issue seems to conflict with the SDD, stop and flag it rather than choosing an answer. Don't copy the prototype's sample data, simulation panel or prototype-only features.
- **Private data stays private.** A starting address or email never appears in another user's view, in notification text, in function logs or in the export, except the email column that US-17 requires. Test data uses made-up students, never real ones.
- **No verification claims.** Nothing in the app may suggest that drivers, vehicles or insurance are checked. No "verified" badges. The safety note stays on Series Details (RISK-01).
- **Nothing is deleted.** Cancelling and ending change a series' status. Functions never delete series, requests, consent records or notifications (RC-24).
- **Accounts and secrets.**
  - Institution accounts are created only by the developer script.
  - The Routes key exists only in the backend's secret storage, and the Places key is restricted to the ColPool app.
  - No key or credential is committed to the repository (RC-04).
- **Wording.** User-facing text uses the SDD's terms ("ride series", "host", "rider", "request", "seat", "pickup spot", "stop") and never database or code names (RC-29). Times use the 12-hour format in Pacific time (RC-18).
