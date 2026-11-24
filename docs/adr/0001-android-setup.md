# ADR 0001: Android app setup

- **Status:** Accepted (owner decisions, 2026-11-11)
- **Issue:** #11 (TMP-02)
- **Scope:** Android only (RC-25). No iOS or web.

## Decisions

### UI and app structure
- **Jetpack Compose + Material 3**, single `Activity` (`MainActivity`).
- **MVVM, one-way data flow.** A ViewModel exposes `StateFlow<UiState>`. Screens are stateless `(state, onEvent)` composables.
- **Repositories** expose reads and callable-function invocations only. The only Firestore client writes ever allowed are the own profile and the notification read flag (ARCHITECTURE §3.4). None exist yet beyond a skeleton in `data/repo`.
- **Manual DI.** One `AppContainer` (created in `ColPoolApp`) holds the Firebase singletons. ViewModels are built with `viewModelFactory { initializer { } }`. No DI library.
- **Single `:app` module, packaged by feature:** `ui/<screen>`, `ui/theme`, `data/repo`, `data/model`, `di/AppContainer`, `config/EmulatorConfig`.
- **navigation-compose 2.10.2** is reserved. It is not wired yet.

### Identity and SDK levels
- `applicationId` and `namespace`: `io.github.ethanscottwalden.colpool`. No debug `applicationIdSuffix`, so one package name serves the Firebase app registration and the Places key restriction.
- `minSdk` 26, `compileSdk` 37, `targetSdk` 36.

### Toolchain (pinned)
| Item | Version |
|---|---|
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.4.21 |
| Gradle wrapper | 9.9.1, with `distributionSha256Sum` pinned |
| Version catalog | `gradle/libs.versions.toml` (the Gradle root is the repo root; the app module stays in `android/app`) |

AGP 9 has **built-in Kotlin**, so `org.jetbrains.kotlin.android` is not applied (AGP 9 rejects it). Only the Kotlin Compose compiler plugin is applied. The fallback to AGP 9.4.x / Gradle 9.6.0 was not needed.

The build targets Java 17 bytecode and compiles on JDK 21 (no toolchain provisioning, so no toolchain download repository is configured).

### Emulator toggle (debug only)
- `BuildConfig.USE_EMULATORS` comes from the Gradle property `colpool.useEmulators` (default `true`) and is set from it only in the `debug` build type. The `release` type hard-codes `false`, so release can never use emulators.
- Host: `colpool.emulatorHost` (default `10.0.2.2`, the Android emulator's alias for the host machine). A physical device needs the PC's LAN address.
- In `Application.onCreate`, before any other Firebase use: Auth `useEmulator(host, 9099)`, Firestore `(host, 8080)`, `Firebase.functions("us-west2")` `(host, 5001)`.
- Always region `us-west2`.
- A debug-only network security config allows cleartext to `10.0.2.2` and `localhost`.
- The ports are the Emulator Suite defaults. **#12's `firebase.json` must match them.**
- Override a build: `./gradlew assembleDebug -Pcolpool.useEmulators=false`.

### Layout and emulator auto-start (owner decision 2026-11-17, tooling PRs #116, #117, #118)
- **Gradle root moved to the repo root** (`settings.gradle.kts`, `gradlew`, `gradle/`, `local.properties`). The `:app` module stays at `android/app`. Run `./gradlew` from the root.
- **`ensureFirebaseEmulators`** (`android/app/build.gradle.kts`) runs before `assembleDebug` and `installDebug`, on Windows only. It is skipped on Linux, in CI, with `colpool.useEmulators=false` or `-Pcolpool.autoStartEmulators=false`. It starts the emulators in WSL with `npm run emulators:dev` (seeded accounts) and waits for the seeded login. Procedure: `DEVELOPMENT.md` §2 "Run the app from Android Studio (Windows)".
- **Debug-only error detail** (`ui/ErrorDetail.kt`) shows the exception class and redacted message under generic errors.
- **Placeholder API key** for emulator builds without `google-services.json` is key-shaped (`"AIza"` plus 35 zeros, built at runtime), because an SDK check on the `signUp` path rejected the earlier placeholder.

### Firebase config
- The google-services plugin is applied **only if `android/app/google-services.json` exists**. That file is never committed (`.gitignore`). Owner steps are in `android/app/google-services.README.md`.
- When the file is absent, debug + emulators initialise `FirebaseApp` manually with placeholder `FirebaseOptions`. The project ID is the Gradle property `colpool.emulatorProjectId`, default `demo-colpool`. **Provisional:** #12 owns `.firebaserc` and the emulator project ID and must match or change it.
- A release build without the file fails at startup with a clear message.

### Places key
- Places SDK 6.0.2 is wired by #16 (see "Places autocomplete (#16)" below).
- Key via the Secrets Gradle plugin: the real key goes in uncommitted `local.properties` at the repo root as `PLACES_API_KEY`. The committed `local.defaults.properties` (repo root) holds a placeholder so CI and fresh clones build.
- The app's package for the key restriction is `io.github.ethanscottwalden.colpool`. The owner adds the Android-app restriction once the debug SHA-1 exists (`docs/setup/firebase.md`).

### Places autocomplete (#16)
- **Where:** port `data/places/PlacesAutocomplete.kt` (`newSession`, `suggestions`, `resolve`, `SelectedAddress`), SDK implementation `data/places/PlacesSdkAutocomplete.kt`, state holder `ui/address/AddressSearchViewModel.kt`, sheet `ui/address/AddressSearchSheet.kt` (`AddressSearchSheet` and stateless `AddressSearchContent`, with an optional `notice` slot for the public-places note). Tests use a fake port. Onboarding (#22) and Edit Profile (#27) use the sheet; Create Series (#24) will.
- **Wiring:** `AppContainer.places` initialises Places once, on first use, with `Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)`. The key comes from the Secrets plugin (placeholder `DEFAULT_API_KEY` in CI). With the placeholder the app builds and runs, and the sheet shows its error state.
- **New Places API:** used, to match the key restricted to **Places API (New)** (`docs/setup/firebase.md`). The legacy initialiser would need the legacy Places API enabled and the key restricted to it.
- **Session token:** one `AutocompleteSessionToken` per address entry. The ViewModel creates it when the entry starts and uses it for every suggestion call and the final lookup. A successful lookup ends the Places session, so the ViewModel then starts a new one: a later edit and search never reuses the ended token. A new entry (new ViewModel, or `startNewEntry()`) gets a new token. Requests are cancelled with the coroutine, and out-of-date search responses are ignored.
- **Field mask:** the lookup requests only `ID`, `FORMATTED_ADDRESS`, `LOCATION`.
- **Rules:** suggestions limited to the US; no device-location bias, GPS or map. Input under 3 characters is not sent; typing is debounced 300 ms. Tap to choose (owner decision 2026-11-16): tapping a suggestion resolves it and closes the sheet with the `SelectedAddress`; the only button is "Close". The tapped row shows a progress indicator and further taps are ignored. If the lookup fails the sheet stays open with the suggestions and an error above them. The sheet calls `onConfirm(SelectedAddress)` and the caller closes it. Call `startNewEntry()` or use a fresh ViewModel for each entry: reusing a ViewModel that still holds a selection re-fires `onConfirm` with the old address. While a lookup runs, the other rows are disabled and TalkBack announces "Looking up address…". Only a resolved suggestion is ever returned; editing clears a selection or error. Typed text, suggestions and coordinates are never logged (RC-05).
- **Accepted risk (owner decision 2026-11-19):** on one Android Studio "Medium Phone" API 36 emulator, tapping the address field on Onboarding or Edit Profile showed a ripple but the sheet never appeared. There was no crash or app error in Logcat, and animations were on. The same build opens the sheet on a Pixel Pro API 36 emulator and on a second Medium Phone-class emulator, and JVM tests confirm that the sheet opens and is displayed (`OnboardingAddressWiringTest`). The cause is not found. Treated as an emulator image problem; development continues. Reopen it if a real device shows the same thing.

### Testing
- **JVM tests:** Robolectric 4.17 with Roborazzi 1.78.0. A smoke test starts the real app class. Screenshot tests render screens into `android/app/src/test/screenshots/` (the original `placeholder.png` was replaced by the #19 and #16 goldens; [ADR 0004](0004-sign-in-routing.md)).
- **Instrumented smoke test** (`androidTest`): written and compiled by agents (`assembleDebugAndroidTest`). The owner runs it on the Windows emulator. CI runs it on PRs into `staging` (not a required check; owner decision 2026-11-16, #54).
- Robolectric needs the Android 16 (SDK 36) jar. Its own downloader can't pass the sandbox proxy, so `app/build.gradle.kts` resolves `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7` through Gradle and runs tests offline. The Robolectric version fixes this coordinate: update it when Robolectric changes.
- JDK 21 needs `--add-exports/--add-opens java.base/jdk.internal.access` for Robolectric (set in the build file).

## Approved library list (owner, 2026-11-11)

Anything else is L2 (`DEVELOPMENT.md` §2).

**Now**
- Plugins: AGP 9.5.1, Kotlin Compose compiler plugin 2.4.21, google-services 4.6.0, Secrets Gradle plugin 2.0.1, Roborazzi 1.78.0.
- Libraries: core-ktx 1.19.1, activity-compose 1.13.0, Compose BOM 2026.09.00 (ui, material3, ui-tooling-preview, debug ui-tooling), lifecycle-runtime-compose and lifecycle-viewmodel-compose 2.11.0, Firebase BOM 35.2.0 with `firebase-auth`, `firebase-firestore`, `firebase-functions` only (Java APIs, no KTX modules), places 6.0.2.
- Tests: junit 4.13.2, robolectric 4.17, roborazzi / roborazzi-compose / roborazzi-junit-rule 1.78.0, Compose `ui-test-junit4` and debug `ui-test-manifest`, androidx.test ext-junit 1.3.0, runner 1.7.0, espresso-core 3.7.0, kotlinx-coroutines-test 1.11.0.

**Reserved** (listed, not added)
- kotlinx-coroutines-play-services 1.11.0.
- navigation-compose 2.10.2, with the kotlinx-serialization Gradle plugin (version = Kotlin, 2.4.21) and kotlinx-serialization-json **1.13.0** (latest on Maven Central on 2026-11-11; not yet built against Kotlin 2.4.21, so the Issue that wires navigation verifies it).
- core-splashscreen 1.2.0.

**Never:** Analytics, Crashlytics, App Check, FCM, Storage, Maps display. These are never added as direct dependencies. Places 6.0.2 pulls in `play-services-maps` transitively, but it is never called. The POLICY §13 "no map display" non-goal is about product behavior, so no map view is ever shown. `firebase-appcheck-interop` is an interface dependency of the Firebase SDKs, not App Check itself.

## Consequences

- A later release-hardening task must enable minify and signing, and strip the transitive c2dm `RECEIVE` permission (`tools:node="remove"`). FCM is out of scope.
- Later screen Issues add `ui/<screen>` packages, a ViewModel and a `(state, onEvent)` composable each, plus a Roborazzi screenshot.
- #12 must use the emulator ports above and settle the emulator project ID.
- The owner registers the app in Firebase and adds `google-services.json` before any real-Firebase or release build.

## Create Series (#24) notes
- Stateless `CreateSeriesContent` plus a stateful `CreateSeriesScreen` that owns the address sheets. The Add a stop sheet reuses `AddressSearchSheet`, passing the minutes field and the "stops are for you only" note through its `notice` slot.
- Backend errors (ADR 0007, 0008) are kept as `{code, path, params}` and mapped to text by `SeriesErrorText`; errors with a field show there, the rest go in the "Fix these before continuing" box.
- Mon to Fri only and 10 stops per direction are Android constants (`SeriesWeekday`, `SeriesLimits.MAX_STOPS_PER_DIRECTION`); keep the stop cap in sync with `SERIES_LIMITS.maxStopsPerDirection` once #143 lands.
- Until #33 (Hosting list) and #31, the Hosting tab placeholder has a "Host a ride series" button, and a successful route preview opens `ReviewPlaceholder(draftId)`.

## Review (#31) notes
- `ReviewViewModel` reads the stored draft (`seriesDrafts/{id}` get) so the host sees exactly what was saved (RC-12), then calls `postSeries` with the draft's stored `input` unchanged (so it hashes to the reviewed `inputHash`). The app never sends routes or times. Times are the stored `arriveMin`/`departMin`; per-hop minutes use `round(durationSec / 60)` and card totals are the sum of displayed hop minutes plus stop minutes (`ReviewCards`, `ReviewFormat`). Values of 1440 or more show as clock time plus "(next day)" (provisional).
- Exits: `ReviewExit.Posted` (also for `alreadyPosted`) goes to the Hosting tab; `ReviewExit.Edit` returns to Create Series with the form and draft kept. `draft-changed` becomes the client-side form error `draftChanged`; `series-invalid` errors are shown on Review and carried to the form on Go back and edit.
- The confirmation sheet is an in-layout scrim plus panel (not `ModalBottomSheet`) so screenshot tests can capture it.

## Hosting (#33) notes
- `SeriesBackend.observeHostedSeries()` is a live Firestore listener on `series` where `hostId == uid` and `status == "active"` (the #20 index). `HostedSeriesParser` skips a document that is incomplete or inconsistent and clamps `openSeats` to 0..`maxRiders`; `lastDepartureAt` is read as `Timestamp` and kept, not shown.
- `HostingViewModel` filters and orders for display (`HostingList`): series whose `endDate` is before today in Pacific are hidden (display only; the backend decides RC-17), then soonest start date, first weekday. It also owns which series is open; there is still no navigation library (ADR 0004), so `ColPoolRoot` shows the details screen above the shell and returns to the Hosting tab.
- Details reuse the #31 rendering (`ReviewCards`, `DirectionCard`, `DayChip`, `Fact`) on the stored `days[wd].*.route`. Riders and Requests are empty states until #43 and #39. Wording is provisional.

## Search (#34) notes
- One `series` query (`status == "active"` and `openWeekdays array-contains-any` the picked days, the #20 index), one `requests` list (`riderId == uid`), one `privateDetails/{uid}` get for the rider's coordinates, and one `profiles/{hostId}` get per host shown (readable for active hosts, ADR 0009; first name only, cached per session). No Maps call (RC-28). `SearchBackend` / `FirebaseSearchBackend` hold the reads; `HostedSeriesParser` now also keeps `hostId` and the pickup coordinates.
- `SearchRules` (pure): a picked weekday is Open (seat and trips left), Full, No trips left or Not needed; a day with no trips left (`lastDepartureAt[wd]` not after now, or absent) is "no trips left" even if its seats are 0. A series qualifies with at least one Open picked day. Own series and requested series are dropped. Distance is haversine miles (R = 3958.7613) rounded once to tenths, used for both display and ordering; ties go to start date, then id. Without starting coordinates there is no distance and no ranking (start date, then id) and the screen says so.
- The Search tab re-reads when shown again (`refresh`, keeps old results while loading). Card tap opens `SeriesDetailsPlaceholder(seriesId)` above the shell until #38; the id is never shown.
- `DayChip` takes an optional `role`: `Role.Tab` (with selected) where it switches the day shown (Hosting details, Review, Create Series schedule card), `Role.Checkbox` (toggle state) where it picks days (Search, Create Series weekdays). Look unchanged.
- Preferences, match % and the request flow are #37 and #38. Wording is provisional.

## Series Details (#38) notes
- Opens from a Search card (`SearchViewModel.openedSeriesId`); `DetailsViewModel` shows the series Search already read, then reads in the background the host's `profiles/{hostId}` (RC-01 case 1, ADR 0009), `options/profile` for labels, `campusPoints` for the timelines and `requests/{seriesId}_{uid}` (a rider's own request, get only). No Maps call, no starting address or email is read or shown (RC-28, POLICY §3).
- Host card: first and last name always; year, major, gender, age, interests, commute style and about me only when filled (`DetailsRules.hostFields`; no "Not provided"). Match highlighting is #37. The safety note says only what ColPool checks (RISK-01); the word "verified" appears nowhere.
- Day rules: a running day is open (seat and trips left), full, or no trips left, checked in Search's order (`DetailsRules.dayStates` reuses `SearchRules.dayState`). Only open days can be picked. RC-32 conflicts are not guessed: the server reports them (`sendRequest`, ADR 0010).
- Request flow: Request to join (needs one day) opens the confirm sheet (days, price, "The host decides each day separately.", the one-request warning); Send calls `sendRequest` once (ignored while sending). `SendRequestResult` maps ADR 0010: `series-id-invalid`/`weekdays-invalid` to Invalid; `not-found`/`series-not-active` to NotAvailable; `own-series`; `already-requested`; `weekdays-unavailable` with `details.weekdays` to per-day reasons; everything else to Failed. A per-day failure marks and deselects those days (they stay disabled), keeps the others picked and lets the rider send again; nothing was saved on the server. Success goes back to Search with a "Request sent" banner (live region) and Search re-reads, so the series is gone (RC-16).
- Already requested, own series and no-longer-available show a message instead of the picker. `DayChip` gained `subMaxLines` (default 1, other screens unchanged) so "no trips left" can wrap.
- Provisional wording: the confirm sheet text, "Pick the days you want to ride...", the per-day error summary, the short chip word "Conflict", "Request sent", and "Host" when no name can be read. Not built: host requests list (#39), accept/decline (#43), Riding tab link (#45), notifications UI (#25), match % (#37).

## Host requests (#39) notes
- `HostRequestsBackend` / `FirebaseHostRequestsBackend` are reads only: one live listener on `requests` where `hostId == uid` (the Hosting tab badge) and, while a hosted series is open, one where `hostId == uid` and `seriesId == X` ordered by `createdAt` ascending (the #35 index). `IncomingRequestParser` skips a document with no series or rider id, skips odd days, keeps an unknown status as sent (shown trimmed, 24 characters at most) and sorts a missing `createdAt` last.
- `HostRequestRules` (pure): the Requests section lists requests with at least one pending day, oldest first, and shows every day of the request with its status (`Tue · pending`, `Thu · accepted`, `Fri · declined`). The badge counts requests with a pending day on the series in the Hosting list (active series only).
- Names come from `profiles/{riderId}` (readable by the host while a request exists, RC-01 case 3); an unreadable one shows "A rider". Requester Profile reuses the host card (`ProfileCard`): name always, optional fields only when filled, never the email or starting address; an unreadable profile shows "This profile is no longer available.". No ids are shown.
- No Accept or Decline buttons (#43), no notifications UI (#25). The badge is a Material `Badge` on the Hosting tab with a plural content description.
- Provisional wording: "Requests · N with pending days", "No requests yet.", "A rider", "Requester", "This profile is no longer available.", the status words, the badge description.

## Notifications (#25) notes
- `NotificationsBackend` / `FirebaseNotificationsBackend`: one live listener on `users/{uid}/notifications` ordered by `createdAt` descending, limited to 100 (no composite index, ADR 0005). The only client write is `markRead`: a batch of updates that each set `read` to `true` and nothing else. `NotificationParser` skips a record with neither title nor body, tolerates missing or extra fields and unknown types, and counts a missing `read` as read. Contents never reach logs; `toString` is redacted.
- `NotificationsViewModel` is started and reset with the student shell (same place as the Hosting listeners), so logging out cancels the listener and in-flight writes and clears everything. The first snapshot (sign-in or restart) is history, not a banner. Later unread arrivals queue newest first, one banner at a time; opening the Notifications tab drops the banner and the queue, and no banner shows while it is open. The unread count comes from the loaded list (up to 100), not a second query.
- Opening the tab clears the badge at once and writes `read: true` for unread records only, once each; "New" tags stay for that visit. A failed write is retried the next time the tab opens, and the badge shows the truth again after the tab is left. Records arriving while the tab is open are marked read too.
- Time text is built by hand in Pacific time (`NotificationRules.timeText`): "Today, 9:05 AM", "Yesterday, 6:40 PM", "Oct 28, 2:10 PM", year only when not the current year. Today and Yesterday are calendar days from the device clock (display only). Tested across Pacific midnight, the 2026-11-01 fall-back day and 2026-03-08.
- `StudentShell` gained `notificationsTab`, `notificationsBadge`, and optional `selectedTab`/`onTabSelected` so the banner can open the tab. Tapping the banner from a details or Create Series screen closes that screen (an unsent review is dropped) and shows the Notifications tab.
- Provisional wording and values: banner duration 5 seconds, "Close notification", "Opens your notifications.", the empty and error texts, the loading line, the "N" tab glyph in the banner, badge description "N unread notifications", the 100-record limit.
