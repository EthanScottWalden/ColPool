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
- **Where:** port `data/places/PlacesAutocomplete.kt` (`newSession`, `suggestions`, `resolve`, `SelectedAddress`), SDK implementation `data/places/PlacesSdkAutocomplete.kt`, state holder `ui/address/AddressSearchViewModel.kt`, sheet `ui/address/AddressSearchSheet.kt` (`AddressSearchSheet` and stateless `AddressSearchContent`, with an optional `notice` slot for the public-places note). Tests use a fake port. No screen uses the sheet yet (#22, #24).
- **Wiring:** `AppContainer.places` initialises Places once, on first use, with `Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.PLACES_API_KEY)`. The key comes from the Secrets plugin (placeholder `DEFAULT_API_KEY` in CI). With the placeholder the app builds and runs, and the sheet shows its error state.
- **New Places API:** used, to match the key restricted to **Places API (New)** (`docs/setup/firebase.md`). The legacy initialiser would need the legacy Places API enabled and the key restricted to it.
- **Session token:** one `AutocompleteSessionToken` per address entry. The ViewModel creates it when the entry starts and uses it for every suggestion call and the final lookup. A successful lookup ends the Places session, so the ViewModel then starts a new one: a later edit and search never reuses the ended token. A new entry (new ViewModel, or `startNewEntry()`) gets a new token. Requests are cancelled with the coroutine, and out-of-date search responses are ignored.
- **Field mask:** the lookup requests only `ID`, `FORMATTED_ADDRESS`, `LOCATION`.
- **Rules:** suggestions limited to the US; no device-location bias, GPS or map. Input under 3 characters is not sent; typing is debounced 300 ms. Tap to choose (owner decision 2026-11-16): tapping a suggestion resolves it and closes the sheet with the `SelectedAddress`; the only button is "Close". The tapped row shows a progress indicator and further taps are ignored. If the lookup fails the sheet stays open with the suggestions and an error above them. The sheet calls `onConfirm(SelectedAddress)` and the caller closes it. Call `startNewEntry()` or use a fresh ViewModel for each entry: reusing a ViewModel that still holds a selection re-fires `onConfirm` with the old address. While a lookup runs, the other rows are disabled and TalkBack announces "Looking up address…". Only a resolved suggestion is ever returned; editing clears a selection or error. Typed text, suggestions and coordinates are never logged (RC-05).

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
