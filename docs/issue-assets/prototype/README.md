# ColPool Prototype v2 — screen captures

These screenshots come from the interactive UI/UX prototype (SDD §6.2, https://claude.ai/artifact/BfQnzLbSXie9qTrGaGk7wP). They were captured on 2026-11-06 so that the design is visible directly in each issue. The prototype is public, so the link above can also be opened directly.

**They are a reference, not an authority.** The SDD (`SPEC.md`) wins wherever the two disagree. In particular:

- Names, addresses, majors, miles, minutes and export rows are **sample data**. The majors list shown is a placeholder; the real CBU list will be supplied before it is implemented.
- Campus-point locations in the prototype are approximate.
- The prototype-only "Copy schedule to the other days" button has been hidden from `17-create-series.png`; do not build it.
- The prototype's demo side panel (sample logins, "Skip in", "Simulate other students") is not shown and is not part of the app.
- The export table on `27-export-results.png` is an on-screen preview only. The deliverable is the CSV file.
- Long screens were captured at full scroll height, so they are taller than a real phone screen.

| File | Screen (Figure 2 name) | Notes |
|---|---|---|
| `01-splash.png` | Splash | A signed-in user skips this in the app. |
| `02-sign-in.png` | Sign up / Sign in — sign in | |
| `03-create-account.png` | Sign up / Sign in — create account | Consent statement and "no password reset" note. |
| `04-onboarding.png` | Onboarding | Majors list is a placeholder. Age must be 18 or older (RC-30). |
| `05-address-search-sheet.png` | Address search sheet | Used for the starting address, pickup spot and stops (RC-13). |
| `06-hosting-empty.png` | Hosting — empty state | |
| `07-riding-empty.png` | Riding — empty state | |
| `08-search.png` | Search | Weekday row, result cards, nearest first. |
| `09-search-preferences.png` | Search — preferences panel | Match percentage on results. |
| `10-series-details.png` | Series Details | Host card, schedule, request day picker, safety note. |
| `11-hosting-list.png` | Hosting | List with request badge. |
| `12-hosted-series-details.png` | Hosted Series Details | Riders by day, requests with Accept/Decline, cancel. |
| `13-remove-rider-sheet.png` | Hosted Series Details — remove rider confirmation | |
| `14-requester-profile.png` | Requester / Rider Profile | |
| `15-cancel-series-sheet.png` | Hosted Series Details — cancel series confirmation | |
| `16-add-stop-sheet.png` | Create Series — add-stop sheet | Minutes at stop and stop warning. |
| `17-create-series.png` | Create Series | Copy-schedule button hidden (prototype-only). |
| `18-review-calculated-route.png` | Review Calculated Route | Miles and minutes are sample values. |
| `19-post-confirmation-sheet.png` | Review Calculated Route — post confirmation | |
| `20-riding-list.png` | Riding | |
| `21-request-details.png` | Request Details | Status card, schedule, People on [weekday], Your days. |
| `22-notifications.png` | Notifications | History with "New" tags. |
| `23-profile.png` | Profile | Starting address and email are visible only to their owner. |
| `24-edit-profile.png` | Edit Profile | |
| `25-notification-banner.png` | In-app notification banner | Shown over any student screen. |
| `26-export-carpool-data.png` | Export Carpool Data | Staff screen, no footer. |
| `27-export-results.png` | Export Carpool Data — after Generate export | Summary card and share button. |
| `28-leave-day-sheet.png` | Request Details — leave weekday confirmation | |
| `29-host-coride-profile.png` | Host / Co-rider Profile | Same layout for co-riders. |
