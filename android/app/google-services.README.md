# google-services.json

`google-services.json` is the Firebase client config. It is **never committed**
(`.gitignore` blocks every `google-services*.json` name). Only the owner adds it.

## When it is needed

- **Debug + emulators** (the default): not needed. `ColPoolApp` initialises
  `FirebaseApp` with placeholder options and connects to the Emulator Suite.
- **Debug against real Firebase** (`-Pcolpool.useEmulators=false`) and **any
  release build**: required. A build without it starts, then fails at launch
  with a clear error.

## Owner steps

1. Open the Firebase console, project `colpool` -> Project settings -> Your apps
   -> Add app -> Android.
2. Package name: `io.github.ethanscottwalden.colpool`. Nickname: ColPool.
3. Debug signing certificate SHA-1: optional here; also add it to the Places key
   restriction (`docs/setup/firebase.md` section 4). Get it with
   `./gradlew signingReport` (on Windows, run it from `android/`).
4. **Decline Google Analytics** when asked (no Analytics, ADR 0001).
5. Download `google-services.json` and place it at
   `android/app/google-services.json`.
6. Rebuild. The google-services plugin is applied automatically when the file exists.

Do not paste the file's contents into an Issue, PR or chat.
