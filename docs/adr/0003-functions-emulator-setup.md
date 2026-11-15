# ADR 0003: Functions toolchain and emulator harness

- **Status:** Accepted (owner decisions, 2026-11-14)
- **Issue:** #12 (TMP-03)
- **Scope:** `functions/`, `firebase.json`, `firestore.rules`, the test harness. No contracts or real functions (those are #13 and later).

## Decisions

### Runtime and language
- **TypeScript**, one npm package in `functions/` with a committed `package-lock.json` and exact pins (no `^` or `~`).
- **Functions runtime Node 22** (`"engines": {"node": "22"}`, `"runtime": "nodejs22"` in `firebase.json`). `@types/node` 22 with target and lib ES2023, so APIs newer than Node 22 fail to compile.
- **Dev and CI use Node >=24.12** (`devEngines`, `onFail: warn`), because `@firebase/rules-unit-testing` 6.0.0 needs it. Local Node today is 26.13.1.
- **Follow-up:** move the runtime to Node 24 before Node 22 support ends in April 2027.
- TypeScript 7.0.2 works with the configuration used here (`erasableSyntaxOnly`, NodeNext); the 6.0.3 fallback was not needed.

### Test runner
- Node's built-in **`node:test`** runs `.ts` files directly (type stripping), so there is no extra test dependency. `erasableSyntaxOnly` keeps the code strip-compatible.
- `npm test` runs `pretest` (build, then typecheck with `tsconfig.test.json`), then `firebase emulators:exec` with Auth, Firestore and Functions and `node --test --test-concurrency=1 functions/test/*/*.test.ts` (paths are relative to the repo root, where the Firebase CLI runs).
- `npm run emulators` starts the same emulators for manual work.

### Approved library list (owner, 2026-11-14)
Anything else is L2 (`DEVELOPMENT.md` §2).
- dependencies: firebase-functions 7.4.0, firebase-admin 14.5.1
- devDependencies: firebase-tools 15.36.0 (local, no global install), typescript 7.0.2, @types/node 22.20.5, @firebase/rules-unit-testing 6.0.0, firebase 13.3.0

### Emulator configuration
- Project ID **`demo-colpool`** (`.firebaserc` and the tests). The `demo-` prefix keeps the emulators offline-only: calls to real services for this project fail. This matches the Android debug default (ADR 0001).
- Ports match ADR 0001: Auth 9099, Firestore 8080, Functions 5001. Emulator UI off. `singleProjectMode` on.
- Region `us-west2` for functions (`setGlobalOptions`). `firebase.json` has no Firestore location or database creation (L3).
- `firestore.rules` is **default deny all** and is never loosened as scaffolding. `firestore.indexes.json` is empty.

### Project-local caches and launcher
`functions/scripts/emulator-env.mjs` starts the Firebase CLI without a shell and sets (only if unset) `FIREBASE_EMULATORS_PATH=<repo>/.cache/firebase/emulators`, `XDG_CONFIG_HOME=<repo>/.cache/xdg-config` and `NO_UPDATE_NOTIFIER=1`. `/.cache/` is git-ignored. `functions/scripts/sandbox-npm.sh` runs npm with `<repo>/.cache/npm` for the agent sandbox, where `~/.npm` is read-only.

### Firestore emulator jar
The sandbox does not allow `storage.googleapis.com`, so the owner downloads the jar once, outside the sandbox, into `<repo>/.cache/firebase/emulators/cloud-firestore-emulator-v1.22.0.jar` and checks its SHA-256:
`9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c`.
firebase-tools skips the checksum on cached jars, so this check is manual. **Repeat the download and check whenever the firebase-tools pin changes** (the expected jar version and hash change with it). CI (#54) downloads it itself.

### Sample callable
`ping` (requires auth, returns `{ ok: true }`) is exported **only** when `FUNCTIONS_EMULATOR === "true"`, so it is never part of a deployed codebase. It exists to prove the harness.

### Test helpers
`functions/test/helpers/personas.ts` provides `authedStudent()`, `studentWithoutConsent()`, `institutionUser()` and `unauthenticated()`, each usable as a Rules context and as an Auth-emulator user with an ID token. Fake `@calbaptist.edu` addresses only. Two open items are marked in code: `TODO(#13)` how consent is marked, and `TODO(#13/#15)` the institution claim key (provisional constant, test helpers only).

## Consequences
- Later Issues add rules, tests and functions on this harness; each rule ships with allow and deny tests.
- Sandbox limitation found while building this: the Functions emulator listens on a Unix socket under the temp folder, which the agent sandbox blocks (`listen EPERM`). Tests that call functions (`ping`) therefore run on the owner's machine and in CI, not in the agent sandbox. Rules tests run everywhere (`--only auth,firestore`).
