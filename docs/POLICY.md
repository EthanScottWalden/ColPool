# ColPool — Product & Domain Policy

> **Status:** v0.1 (2026-11-07). Derived from SDD v0.5 (`SPEC.md`) and the Implementation Plan (`INTENT.md`), plus the owner's answers from the 2026-11-07 policy interview (now written into SDD v0.5).
> **Authority:** `SPEC.md` wins on product behavior; `INTENT.md` wins on agent guardrails. If this file disagrees with them, they win and this file is fixed.
> **Scope:** Enduring rules every implementation must obey. Architecture and technology boundaries live in `docs/ARCHITECTURE.md`; coding style, commands and developer preferences live elsewhere.
> **If a task seems to need a rule that isn't here or in the SDD, stop and flag it. Don't invent one.** Agents may refine technical details, but must not add product scope, change a security, privacy or consent rule, or answer an SDD question differently without a documented human decision (INTENT §1.3).

---

## 1. Roles and what each may do

| Role | Who | May | May not |
|---|---|---|---|
| **Student** | Anyone who signs up with an `@calbaptist.edu` email and agrees to consent (including CBU faculty and staff; RISK-13) | Host ride series, search, request, ride, view permitted profiles, edit own profile | View others' starting address or email; see profiles outside §3.2; export data |
| **Host** | A student, for a series they posted | Accept / decline each requested weekday; remove a rider from a weekday; cancel the series; view requester and rider profiles | Edit a posted series; pick riders' boarding points; set campus points outside the list |
| **Rider** | A student, for a series they requested | Withdraw pending weekdays; leave accepted weekdays (one or all); view host and co-rider profiles | Request the same series twice; rejoin any weekday; ride one direction only |
| **Institution account** | University staff | Export the carpool CSV for a date range ending no later than today | Anything else: view profiles, browse or search series, create or join series, change any data, moderate content, manage users |

- One student account can be a host and a rider at the same time. "Host" and "rider" are relationships to a series, not account types. (SDD §1.2)
- Institution accounts are created only by ColPool's developers, never through sign-up, and carry a claim only developers can set. For the MVP they are assumed to use `@calbaptist.edu` emails. (RC-06, RC-07)
- Hiding something in the UI is not access control. Every permission above must be enforced by the backend or Security Rules. (INTENT §1.3)

## 2. Accounts, eligibility and consent

- **University scope.** Only `@calbaptist.edu` emails can create a student account or sign in. The MVP serves CBU only. If more universities are ever added, students from different universities must never see, search for or interact with each other, and an institution account may export only its own university's students. (RC-02, DD-01)
- **Eligibility.** Any `@calbaptist.edu` address may create a student account. ColPool doesn't try to tell students apart from faculty or staff. (RISK-13)
- **Consent is a condition of use.** A student account cannot be created without agreeing to this statement, shown verbatim (SDD §2.2):
  > **Data-sharing consent.** I agree that my scheduled carpool activity (my name, email, who I ride with, which days, pickup spots and estimated miles) will be shared with my university.
- The account and the record of when consent was given are created together. A student account without a consent record (for example, one created outside ColPool's sign-up) cannot use the app. Institution accounts are exempt. (RC-06)
- **Consent cannot be withdrawn**, and accounts cannot be deleted, in the MVP. (RC-24, DD-09)
- There is **no terms of service or privacy policy**. The consent statement is the only agreement. Don't add one. (RC-06, SDD §2.3)
- **Minimal authentication.** Sign-up checks the email domain only: no email verification. No password reset; a user who loses their credentials can't recover the account through ColPool. (RC-10, DD-01, RISK-04)
- **Required to sign up:** CBU email, password (≥ 8 characters), first and last name, consent. **Required before using the app:** a starting address. A student without one is always sent back to onboarding. (US-01, US-02)

## 3. Profiles and privacy

### 3.1 Profile content

- Required: first name, last name, starting address. These can be edited but never removed. (US-02)
- Optional, and removable at any time: age, gender, interests, major, current year, commute-style traits, about me. (SDD §2.2)
- Interests, major, current year, traits (and gender) are chosen only from predefined lists. Agents must not invent list values; the CBU majors list comes from the project owner. (RC-22, INTENT §3.1)
- **Never collect, store or infer** political views, religious views, sexual orientation, mental health conditions or any other sensitive personal category. Gender is not treated as sensitive. (RC-03)
- **No media** anywhere: no photos, video, files, music, animations or themes. Profiles are text only. (RC-08, SDD §2.3)

### 3.2 Who can see a profile (RC-01)

A user can always see their own profile. They can see another student's profile **only while** one of these holds:

1. That student hosts any active series (posted, not cancelled, not ended), whether or not it currently appears in search.
2. That student has a pending or accepted request on a series the user hosts.
3. That student hosts a series the user has requested or joined.
4. That student is accepted on a weekday the user is also accepted on, in the same series.

- When the case stops holding (leave, removal, decline, withdrawal, cancellation, end), access ends **immediately**, unless another case still applies. (RC-01)
- Institution accounts cannot view profiles in the app. (RC-01, RC-07)

### 3.3 Private data

- A student's **starting address** is used only to rank their own search results. It is never shown to another user, never exported, never used for anything else. Only its owner can read it. (RC-05, DD-10)
- A student's **email** is visible only to that student in the app. The institution export is the only other place it appears. (RC-01)
- Neither the starting address nor the email may appear in another user's view, notification text or logs. (INTENT §6.3)
- **Pickup spots and stop addresses are public** to every student who searches. When a host enters them, the app recommends public places (a parking lot, a gas station) rather than a home or school. (RC-05, DD-10, RISK-06)
- Test data uses made-up students, never real ones. (INTENT §6.3)

## 4. Ride series

### 4.1 What a series is

- A **ride series** is a recurring **round trip**: from one pickup spot to campus and back to the same pickup spot, on chosen weekdays, across a date range. No one-way series. No trips between two off-campus places. (RC-11, DD-02, DD-03)
- Riders board and leave **only** at the pickup spot and that weekday's campus drop-off and pickup points. Stops are for the host only (errands, dropping off family). (SDD §2.2)
- Campus drop-off and pickup points come only from the developer-maintained list. For CBU: Yeager Center, Music Building, Health Science, Lancer Plaza, Events Center. Hosts can't add their own. Each weekday and each direction can use a different point. (RC-11)
- Each weekday and direction has its own departure time and its own ordered stops. (SDD §2.2)
- **Riders pay the host a flat cash price per round trip, outside the app.** No in-app payments. (DD-12)

### 4.2 Posting rules

- Every field except stops is required. (RC-12)
- Start date not in the past; end date not before start; at least one date in range falls on a chosen weekday. (US-03)
- On each weekday, leaving campus must be no earlier than the expected arrival at campus. (US-03)
- **Hosting limits:** at most **five** active series per host, and no two active series of theirs may share a weekday during overlapping dates. Back-to-back series (e.g. one per semester) may reuse weekdays. Cancelled and ended series don't count. (RC-31)
- A host can't post a series on a weekday they are accepted on as a rider in another series with overlapping dates. (RC-32)
- Routes and expected times are calculated by the backend when the host continues to review. The host sees them before posting, and posting saves **exactly** what was reviewed. The app never supplies distances or times. (RC-12, RC-14, US-04)
- Posting rechecks the rules at the moment of posting and fails if the start date has passed, no remaining date matches, the hosting limits or schedule-conflict rules are broken, or the series changed since review. (RC-12, RC-32)
- If a route can't be calculated, the host is told which address caused it and can't post until it's fixed. (RC-14)

### 4.3 After posting

- **A posted series can never be edited**, and its routes and times are never recalculated. A host who needs a change cancels and posts a new series. (RC-12, DD-06)
- The host can cancel the whole series at any time, with confirmation. That ends all remaining trips. (US-06)
- Once cancelled, or once its end date has passed, a series disappears from search and from the host's and riders' lists. Its pending weekdays become declined. Its records are kept. (RC-17, RC-24)
- **The backend's clock decides** what has ended, never the device's. (RC-17)

## 5. Search and matching

- The rider picks one or more weekdays. Results show only series with an open seat **and trips left** on at least one of them, and mark which are available. (US-09, RC-33)
- **Proximity first.** Results are ordered by straight-line distance from the rider's starting address to the pickup spot, nearest first, and each shows that distance. (RC-19, DD-04)
- Search never shows: the rider's own series, series they've already requested, cancelled series, ended series. (US-09)
- **Preferences** are optional: any number of interests and traits, at most one major and one year. Age and gender can never be preferences. (RC-20)
- Preferences produce only a **match percentage**. They never change which series appear or their order. Clearing all preferences removes the percentage. (RC-20, US-10)
- Match formula (INTENT §6.3): each chosen interest, trait, major and year counts as one item. Interests, traits and major score 1 on an exact match, else 0. Year scores 1 − 0.34 × years apart (Freshman → Sophomore → Junior → Senior → Graduate), never below 0. A field the host left empty scores 0. Percentage = total ÷ items, rounded to a whole number. (RC-21)
- No search filters beyond weekdays and preferences. (SDD §2.3)

## 6. Requests, seats and riding

- **Decisions are per weekday.** Seats are tracked per weekday; hosts accept or decline each requested weekday separately; riders leave or withdraw one weekday at a time (or all at once). Deciding one weekday never changes the others, except for the automatic declines below. (DD-05, US-08)
- **One request per series, ever.** A rider lists all wanted weekdays in a single request. They can never send a second request to that series, and can never rejoin a weekday they withdrew, left, were removed from, or were declined for (including automatically). (RC-16, DD-13, RISK-10)
- A request can be sent only if every chosen weekday has an open seat and trips left, no chosen weekday conflicts with the rider's schedule (below), and the series isn't the rider's own. A series that has already started can be requested. (US-11)
- A weekday can be accepted only if it has an open seat, trips left and no schedule conflict. Accepting takes one seat; leaving or removal frees exactly one. (US-08, US-12)
- When a weekday fills up, that weekday is **declined automatically** in every pending request that includes it. The requests' other weekdays are untouched. (RC-15)
- **Trips left (RC-33).** A weekday has trips left while some date on it, on or before the end date, has a departure from the pickup spot that is still in the future (server clock). Without trips left it is unavailable even with open seats: not shown as available in search, can't be requested, can't be accepted. A pending weekday in that state stays pending until it's declined or the series ends.
- **No double-booking (RC-32).** Two series conflict on a weekday when both run on it and their date ranges overlap. A student is never scheduled on two conflicting weekdays:
  - A rider can't be accepted on a weekday that conflicts with one they're already accepted on or host.
  - A rider may have pending weekdays on several conflicting series. When one is accepted, the others are **declined automatically** in the same step, with a notification for each, and can't be rejoined.
  - A request that includes a weekday conflicting with one the rider is accepted on or hosts is rejected.
  - A host can't post a series on a weekday they ride in a conflicting series.
  - Back-to-back date ranges don't conflict.
- Two riders can never take the last seat on the same weekday. Seat counts and request states change together, atomically, on the backend. (RC-15, RISK-03)
- Leaving, withdrawing, removing and cancelling all require confirmation from the user doing it. (US-06, US-12)
- A request leaves the host's request list once none of its weekdays are pending. (US-08)

## 7. When a trip counts, and the institution export

### 7.1 The departure-time rule (DD-14, RC-23)

A rider's trip on an accepted weekday **counts** when, at that trip's scheduled departure from the pickup spot:
- the weekday had already been accepted, **and**
- the rider had not left or been removed from that weekday, **and**
- the series had not been cancelled, **and**
- the date is not after the series' end date.

A **host's** trip counts only if at least one rider's trip counts on it. The rule is defined by departure time, not calendar date: a Monday accepted Monday evening starts with next Monday; a rider who leaves Monday at noon still counts for that morning.

### 7.2 Export content (US-17, RC-23)

- Only institution accounts can export.
- Staff choose a start and end date. The start can't be after the end, and the **end can't be after today** in the university time zone. Future dates are rejected.
- One row per student × series × calendar week (Monday–Sunday, university time zone) that has at least one counted trip.
- Each row: student name and email; hosted or rode; host's name; pickup spot; dates of scheduled round trips that week; number of scheduled round trips; estimated miles.
- Miles = the stored round-trip route miles (including stops) × counted trips. **Passenger miles** on rider rows, **vehicle miles** on host rows, labelled so they are never added together.
- The file states it lists **scheduled** trips, not verified trips. ColPool does not confirm attendance. (DD-08, RISK-05)
- Holidays and breaks inside a series' date range still count as scheduled trips in the MVP. (OQ-04)
- Trips from cancelled and ended series are included if they departed before the cancellation or end.
- The export never includes a starting address. (RC-05)

## 8. Notifications

- In-app only: a banner that disappears after a short time, plus a history list (newest first) with unread markers. No push, email or any other channel. (US-15, US-16, OQ-05)
- Each notification shows its event type, the related series and a timestamp. (US-16)
- Events, and who is notified (US-15):

| Event | Notified |
|---|---|
| New request on a series | Host |
| A requested weekday accepted, declined, or auto-declined (full, or schedule conflict under RC-32) | Rider — **one notification per weekday decision**, never combined |
| Rider removed from a weekday | Rider |
| Rider leaves an accepted weekday | Host |
| Rider withdraws a pending weekday | Host |
| Series cancelled | Every rider with a pending or accepted request |
| Series reaches its end date | Host, and every rider with a pending or accepted request |

- When a series is cancelled or ends, its pending weekdays become declined **without** separate decline notifications. The single cancelled or ended notification covers them. (US-06, US-15)
- No other notification types, and no messaging, calling or other communication between users. (RC-09, DD-11)

## 9. Time

- All times are entered and shown in **12-hour format** in the **university's time zone (America/Los_Angeles)**, following daylight saving time. Export dates, "today" for the export range, and "trips left" use the same zone. (RC-18)
- Server time decides anything time-based: ended series, trips left, whether a trip counts, the export. (RC-17, RC-33, INTENT §1.3)

## 10. Validation and data handling

**Input limits**, enforced in the UI **and** by the backend (RC-30):

| Field | Rule |
|---|---|
| Max riders per series | 1–6 |
| Price per round trip | whole dollars, $1–$99 |
| Minutes at a stop | whole number, 0–120 |
| Password | ≥ 8 characters |
| First name, last name | ≤ 30 characters each |
| About me | ≤ 300 characters |
| Vehicle description | ≤ 60 characters |
| Age (optional) | whole number, 18 or older |

- **Every address** (starting address, pickup spot, stops) must be chosen from autocomplete suggestions. Free text that doesn't match a suggestion is rejected. (RC-13)
- Predefined-list fields accept only list values. (RC-22)
- Every rule the UI checks is checked again by the backend. The UI's checks are for convenience only.
- **Nothing is deleted.** Cancelling and ending change status only. Series, requests, consent records and notifications are kept so past ranges can still be exported. (RC-24, INTENT §6.3)
- Mapping-service calls are limited to address entry and route calculation at review. Search, viewing a series and the export never call a mapping service. (RC-28)

## 11. Terminology

User-facing text uses these domain terms and never exposes database or code names (RC-29):

| Term | Meaning |
|---|---|
| **Ride series** | A host's recurring round trip on chosen weekdays across a date range |
| **Host** | The student who posts and drives a series |
| **Rider** | A student who requests or rides in a series |
| **Request** | A rider's single ask to join a series, listing weekdays; each weekday is pending, accepted or declined |
| **Seat** | One rider place on one weekday of a series |
| **Trips left** | A weekday still has a future departure on or before the end date; needed to request or accept it |
| **Pickup spot** | The series' single off-campus start and end point; public to all searchers |
| **Stop** | A host-only stop along the way; riders never board or leave there |
| **Campus drop-off / pickup point** | A point from the university's list where riders leave or board on campus |
| **Starting address** | Where a student usually leaves from; private; used only for ranking |
| **Scheduled trip** | A trip counted by the departure-time rule; not a verified trip |
| **Passenger miles / vehicle miles** | Export miles on rider rows / host rows; never summed together |

## 12. Safety, ethics and compliance

- **No verification claims.** A CBU email shows university membership only. Nothing in the app may suggest drivers, vehicles, licenses or insurance are checked. No "verified" badges. Series Details carries a note that drivers aren't verified. (RISK-01, INTENT §6.3)
- No ID scanning, license checks, background checks or other driver verification. (SDD §2.3)
- No driver ratings, public or private. (SDD §2.3)
- **No moderation.** No report or block feature and no content filter in the MVP. Institution accounts can't moderate. This is an accepted risk. (RISK-12)
- **No payment or insurance wording** beyond the "drivers aren't verified" note. Cash is arranged between students. This is an accepted risk. (RISK-14)
- ColPool is not a social network: no messaging, feeds or friend lists. Relationships form through riding together. (DD-11)
- The university is a stakeholder with a duty to protect student data. Data reaches it only through the consented export. (SDD §1.2)

## 13. Non-goals — never build (SDD §2.3, INTENT §1.3, §6.3)

- Messaging, calls or any communication beyond the listed notifications
- Push notifications or email summaries
- In-app payments
- Ratings
- Reporting or blocking users, or content filtering
- Multi-university support
- Map display, turn-by-turn navigation, GPS or current-location detection
- Live tracking, live-traffic ETAs, or recalculating times after posting
- Per-rider pickup or drop-off points
- One-way sign-ups or off-campus-to-off-campus trips
- Editing a posted series, skipping dates, excluding holidays, or a no-class calendar
- Trip-completion confirmation or attendance tracking
- Institution dashboards, charts, web portals, auto-emailed reports, moderation or user management
- Search filters beyond weekdays and preferences
- Terms of service or privacy policy documents
- Deleting accounts or records, or withdrawing consent
- Profile pictures, video, music, animations or themes
- Password reset or email verification
- Driver verification of any kind
- Prototype-only features: copy schedule to other days, splash tap-to-continue, simulation panel, demo logins, sample data

## 14. Unresolved policy questions — do not guess

| ID | Question | Status |
|---|---|---|
| PQ-01 | CBU majors list | Waiting on project owner (#17) |
| PQ-02 | Campus-point coordinates | Waiting on project owner (#17) |

Resolved in the 2026-11-07 policy interview and written into SDD v0.5: rider schedule conflicts (RC-32), weekdays with no trips left (RC-33), pending weekdays at cancel or end (US-06, US-15, RC-17), export range ending no later than today (US-17, RC-23), and the accepted risks RISK-12 (no moderation), RISK-13 (non-students with CBU emails) and RISK-14 (cash and insurance).

## 15. Traceability quick index

| Area | Sources |
|---|---|
| Roles & access | US-01, US-14, US-17; RC-01, RC-02, RC-06, RC-07 |
| Consent & accounts | US-01; RC-06, RC-10, RC-24; DD-01, DD-09; RISK-13 |
| Privacy | RC-01, RC-03, RC-05, RC-08; DD-10; INTENT §6.3 |
| Series | US-03–US-06; RC-11, RC-12, RC-14, RC-17, RC-31, RC-32; DD-02, DD-03, DD-06, DD-12 |
| Search & matching | US-09, US-10; RC-19–RC-22, RC-33; DD-04; INTENT §6.3 |
| Requests & seats | US-07, US-08, US-11–US-13; RC-15, RC-16, RC-32, RC-33; DD-05, DD-13 |
| Trip counting & export | US-17; RC-23, RC-24; DD-08, DD-14 |
| Notifications | US-15, US-16; RC-09; OQ-05 |
| Time | RC-17, RC-18, RC-33 |
| Validation | RC-13, RC-22, RC-28, RC-30 |
| Terminology | RC-29 |
| Safety | RISK-01, RISK-04, RISK-06, RISK-11, RISK-12, RISK-14; DD-11 |
