# ADR 0004: Sign-in, role routing and app shells

- **Status:** Accepted (owner decisions, 2026-11-17)
- **Issue:** #19 (TMP-06)
- **Scope:** `android/` only. No new backend function and no Security Rules change.

## Decisions

1. **Routing signal: token claim plus a server read.**
   - Staff are recognised by the `institution` claim on the ID token. Any value counts, matching Rules and functions (#18). Staff go to Export Carpool Data and never read `privateDetails`.
   - Students: the app reads their own `privateDetails` doc with `get(Source.SERVER)`.
     - PERMISSION_DENIED means no consent record (Rules require it). The app signs out and shows on Sign in: "This account has no data-sharing consent record, so it can't use ColPool."
     - Document missing (or no starting address in it): Onboarding.
     - Starting address present: Search.
   - Any network or other error shows a retryable error screen (Try again, Log out). It never routes to a student or staff screen.
   - The existing Rules already allow this read (`allow get: if isStudentOwner(uid)`), and a missing doc is not an error, so nothing changes in Rules.
2. **Splash: the Android system splash** (`androidx.core:core-splashscreen` 1.2.0, from Google Maven), held with `setKeepOnScreenCondition` until routing resolves. No Compose splash and no tap-to-continue.
3. **Firestore cache: memory only.** `AppContainer` sets `MemoryCacheSettings` before first use, so nothing is persisted to disk (a signed-out device keeps no student data).
4. **Temporary Log out on the Profile placeholder tab**, visibly marked temporary (`TODO(#27)` replaces it). The Onboarding placeholder has the same temporary Log out (`TODO(#22)`).

## Notes
- Sign-up calls the `signUp` callable (#14) and then signs in with the same credentials. Callable reasons map to messages in `AuthScreen.kt`.
- Footer tabs use letter glyphs instead of icons: `material-icons` is not an approved dependency.
