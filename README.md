testing testing

- hello from claude peggle master
- main test: extreme fever achieved
- staging test: orange pegs remaining: 25
- dev test: the peg board has been rearranged

## Running tests

Backend tests (Functions and Security Rules) run against the Firebase Emulator Suite.

- Needs Node >= 24.12 and JDK 21.
- One-time: download the Firestore emulator jar `cloud-firestore-emulator-v1.22.0.jar` into `.cache/firebase/emulators/` (git-ignored) and check its SHA-256 is `9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c`. Repeat if the `firebase-tools` pin changes.
- Run: `cd functions && npm ci && npm test` (Rules tests and function unit tests; Auth + Firestore emulators)
- End-to-end callable test through the Functions emulator: `cd functions && npm run test:e2e` (not runnable inside the Claude Code sandbox)
- Start the emulators by hand: `cd functions && npm run emulators`
- The emulator project ID is `demo-colpool` (offline-only; no real Firebase project is used).

## Continuous integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and on pushes to `dev`, `staging` and `main`:

- **android:** `assembleDebug`, the JVM unit tests and the Roborazzi screenshot checks (`verifyRoborazziDebug`), JDK 21.
- **functions:** `npm ci`, `npm test` (Rules and function unit tests) and `npm run test:e2e` (Functions emulator). The Firestore emulator jar is downloaded and its SHA-256 checked on every run.
- **android-instrumented:** the instrumented smoke test on an emulator, only for PRs into `staging`. Informational, not a required check.
- **ci-ok:** passes only if both jobs pass. This is the one status check to require.

CI uses no secrets, has read-only repository access and never deploys. Failed runs upload the test reports as artifacts.

Details: [ADR 0003](docs/adr/0003-functions-emulator-setup.md), `DEVELOPMENT.md` §2.
