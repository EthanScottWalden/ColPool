# ColPool — Roadmap

High-level direction only. **The [ColPool GitHub Project](https://github.com/users/EthanScottWalden/projects/1) and its Issues are the authoritative source** for task definitions, acceptance criteria, sequencing, dependencies ("blocked by" links), status and day-to-day tracking. This file changes when the direction changes, not when an Issue moves columns.

Related: [SPEC.md](../SPEC.md) (SDD v0.5, the design authority) · [INTENT.md](../INTENT.md) (Implementation Plan: why the phases are ordered this way) · [ARCHITECTURE.md](ARCHITECTURE.md) · [POLICY.md](POLICY.md)

## Product direction

ColPool is a native Android app (Kotlin + Firebase) for California Baptist University commuters. It lets students host and join recurring round-trip carpools to campus, called **ride series**. Search ranks series by distance from the rider's starting address and shows a preference match percentage. Hosts accept or decline each requested weekday. The university gets a CSV export of *scheduled* trips. The MVP is exactly the scope in SDD §2.2. Anything in SDD §2.3 is out of scope.

## Phases (in order)

Each phase builds on the data and trust boundaries of the one before. In the Project, use the **Phase** field (1–6) to see a phase's Issues.

| # | Phase | Outcome when done |
|---|---|---|
| 1 | **Foundation: accounts, consent and profiles** | A CBU student can sign up with consent, onboard and edit their profile. Staff can sign in. Private data (starting address, email) is protected by tested Security Rules. Firebase, Maps billing, the emulator and CI are in place. |
| 2 | **Hosting a ride series** | A host creates a series, reviews routes calculated on the backend, and posts it within the posting rules, hosting limits and schedule-conflict rules. It appears in their Hosting tab. |
| 3 | **Search, requests and notifications** | A rider finds nearby series for their weekdays, sees a match percentage and sends a request. The host sees it and is notified in the app. |
| 4 | **Request and series lifecycle** | Hosts accept, decline or remove riders one weekday at a time, with seat changes made atomically and no student double-booked on a weekday. Riders ride, leave or withdraw. Series can be cancelled or end on their own. Everyone affected is notified. |
| 5 | **Institution export** | Staff export scheduled trips for a date range as a CSV, using the departure-time rule, and share it through Android's share menu. |
| 6 | **Integrated validation and MVP hardening** | Every SDD pass/fail check is shown to work. Privacy, seat concurrency, performance and Maps usage are tested, and defects are fixed. No new scope. |

## Key sequencing

- **Shared contracts set the pace.** Each one blocks the work after it and changes only through its own reviewed Issue:
  - core schema and profile-permission model ([#13](https://github.com/EthanScottWalden/ColPool/issues/13))
  - series and seat model ([#20](https://github.com/EthanScottWalden/ColPool/issues/20))
  - shared series validation ([#23](https://github.com/EthanScottWalden/ColPool/issues/23))
  - notification writer ([#21](https://github.com/EthanScottWalden/ColPool/issues/21))
  - timestamp contract ([#26](https://github.com/EthanScottWalden/ColPool/issues/26))
  - trip-counting module ([#29](https://github.com/EthanScottWalden/ColPool/issues/29))
- **Rules come with features.** No feature ships without its Security Rules emulator tests ([#18](https://github.com/EthanScottWalden/ColPool/issues/18) sets up the baseline).
- **Phase 5 can overlap Phase 4.** Trip counting starts once the timestamp contract (#26) is settled, without waiting for the rest of Phase 4.
- **Human input is needed early in Phase 1:** Firebase and Maps billing ([#10](https://github.com/EthanScottWalden/ColPool/issues/10)), plus the campus-point coordinates and the CBU majors list ([#17](https://github.com/EthanScottWalden/ColPool/issues/17)).

## After the MVP

No post-MVP features are approved. The design docs defer only these items and commit to none of them:

- Adding campus points, such as ones near residence halls (SDD RC-11).
- Firebase App Check (ARCHITECTURE §2).
- Separate dev/staging/production Firebase projects (Implementation Plan §1.3).

Anything else, including the SDD §2.3 non-goals, needs a documented human decision before it goes on this roadmap.
