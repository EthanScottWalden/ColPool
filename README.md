testing testing

- hello from claude peggle master
- main test: extreme fever achieved
- staging test: orange pegs remaining: 25
- dev test: the peg board has been rearranged
## Running tests

Backend tests (Functions and Security Rules) run against the Firebase Emulator Suite.

- Needs Node >= 24.12 and JDK 21.
- One-time: download the Firestore emulator jar `cloud-firestore-emulator-v1.22.0.jar` into `.cache/firebase/emulators/` (git-ignored) and check its SHA-256 is `9b6498b7f62714d67f48f59b3818883cd682dbcd46b9f59511de81c97bb5166c`. Repeat if the `firebase-tools` pin changes.
- Run: `cd functions && npm ci && npm test`
- Start the emulators by hand: `cd functions && npm run emulators`
- The emulator project ID is `demo-colpool` (offline-only; no real Firebase project is used).

Details: [ADR 0003](docs/adr/0003-functions-emulator-setup.md), `DEVELOPMENT.md` §2.
