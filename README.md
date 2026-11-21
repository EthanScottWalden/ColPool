# ColPool

ColPool is a native Android app for California Baptist University commuters. Students host and join recurring round-trip carpools to campus, called **ride series**. Riders search for series near their starting address and request the weekdays they want, and hosts accept or decline each weekday. The university can export a CSV of *scheduled* trips.

Core rule: **the client reads, the backend writes.** Seats, requests, consent, permissions and notifications change only through Cloud Functions, and Security Rules enforce that.

## Stack

- **Android app:** Kotlin, Jetpack Compose, Material 3 (min SDK 26, target SDK 36). Lives in `android/`.
- **Backend:** Firebase Auth, Cloud Firestore and Cloud Functions (TypeScript, Node 22 runtime). Lives in `functions/`; rules in `firestore.rules`.
- **Maps:** Places SDK for address entry (in the app) and Routes API for route previews (from Functions only).

## Documentation

| Read | For |
|---|---|
| [SPEC.md](SPEC.md) | Product behavior and rules (SDD). Wins on product. |
| [INTENT.md](INTENT.md) | Implementation plan: phases, sequencing, guardrails |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Components, boundaries, decisions, accepted risks |
| [docs/POLICY.md](docs/POLICY.md) | Domain, privacy and safety rules, non-goals |
| [docs/ROADMAP.md](docs/ROADMAP.md) | The six phases at a glance |
| [docs/data-model.md](docs/data-model.md), [docs/adr/](docs/adr/) | Firestore layout and recorded decisions |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Machine setup, build, run, test, sandbox |
| [CLAUDE.md](CLAUDE.md) | How the Claude Code agents work on this repo |

What to build now is in the [GitHub Project](https://github.com/users/EthanScottWalden/projects/1) and its Issues.

## Getting started

Full setup (Windows + WSL2, a second Windows profile, the emulators) is in [DEVELOPMENT.md](DEVELOPMENT.md) §1–2. The things that most often go wrong:

- **Open the repo root** (`ColPool`) in Android Studio, not `android/`. It is the Gradle root.
- **Gradle JDK:** use Android Studio's bundled JetBrains Runtime (Settings → Build Tools → Gradle → Gradle JDK). A JRE without `jlink`, such as the one VS Code's Java extension ships, fails the build.
- **Emulators:** for now, debug builds use a local Firebase Emulator Suite (offline project `demo-colpool`) instead of a real Firebase project. Running from Android Studio on Windows starts and seeds them in WSL automatically. This will change once the real Firebase and Google Cloud setup is ready for general use.

### What works without access to the owner's Google Cloud project

Almost everything. All tests, and the app itself, run on the local emulators and need no Google account, keys or secrets.

**Address search is the exception.** The address sheet (Onboarding, Edit Profile) calls Google's Places API, which runs on the project owner's Google Cloud project and needs that project's API key. Without it the app still builds and runs, but the sheet shows "Couldn't search addresses right now". So you can't finish Onboarding for a new account or change an address.

To test without address search, sign in with a seeded account that already has an address: `dev.student@calbaptist.edu` (password `demo-pass-123`, a fake, emulator-only account). All seeded test accounts are listed in [DEVELOPMENT.md](DEVELOPMENT.md) §2 (the table of `dev.*@calbaptist.edu` accounts).

**To get address search working,** ask the project owner. Two things are needed, because the key is restricted to builds the owner has approved:

1. **The owner gives you the API key.** Put it in a file named `local.properties` at the repo root, as `PLACES_API_KEY=<key>`. Git ignores this file; never commit it.
2. **You send the owner your debug signing fingerprint (SHA-1),** and the owner adds it to the key. Each computer, and each Windows account, has its own debug signing key, so each one needs its own entry. To get yours, run `.\gradlew.bat signingReport` (Windows) or `./gradlew signingReport` from the repo root and copy the `SHA1:` line under `Variant: debug`. The fingerprint isn't secret.

Changes to the key can take a few minutes to apply. The owner-side steps are in [docs/setup/firebase.md](docs/setup/firebase.md).

## Running tests

Backend tests (Functions and Security Rules) run against the Firebase Emulator Suite.

- Needs Node >= 24.12 and JDK 21.
- One-time: download the Firestore emulator jar `cloud-firestore-emulator-v1.22.0.jar` into `.cache/firebase/emulators/` (git-ignored) and check its SHA-256 is `9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c`. Repeat if the `firebase-tools` pin changes.
- Run: `cd functions && npm ci && npm test` (Rules tests and function unit tests; Auth + Firestore emulators)
- End-to-end callable test through the Functions emulator: `cd functions && npm run test:e2e` (not runnable inside the Claude Code sandbox)
- Start the emulators by hand: `cd functions && npm run emulators`
- The emulator project ID is `demo-colpool` (offline-only; no real Firebase project is used).

Android tests run from the repo root:

- JVM unit tests (Robolectric): `./gradlew testDebugUnitTest`
- Screenshot tests (Roborazzi): `./gradlew verifyRoborazziDebug`; refresh the images in `android/app/src/test/screenshots/` with `./gradlew recordRoborazziDebug`
- Instrumented smoke test on an emulator or device: `./gradlew connectedDebugAndroidTest`

## Branches

`feature/*`, `fix/*` and `docs/*` → `dev` → `staging` → `main`. Every change reaches `dev` through a pull request with a green `ci-ok` check. `staging` and `main` are promoted by the owner after review. Details: [CLAUDE.md](CLAUDE.md) §5.

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and on pushes to `dev`, `staging` and `main`:

- **android:** `assembleDebug`, the JVM unit tests and the Roborazzi screenshot checks (`verifyRoborazziDebug`), JDK 21.
- **functions:** `npm ci`, `npm test` (Rules and function unit tests) and `npm run test:e2e` (Functions emulator). The Firestore emulator jar is downloaded and its SHA-256 checked on every run.
- **android-instrumented:** the instrumented smoke test on an emulator, only for PRs into `staging`. Informational, not a required check.
- **ci-ok:** passes only if both jobs pass. This is the one status check to require.

CI uses no secrets, has read-only repository access and never deploys. Failed runs upload the test reports as artifacts.

Details: [ADR 0003](docs/adr/0003-functions-emulator-setup.md), `DEVELOPMENT.md` §2.
