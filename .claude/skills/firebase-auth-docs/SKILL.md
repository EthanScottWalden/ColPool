---
name: firebase-auth-docs
description: >
  Official Firebase Authentication docs links for ColPool. Use when working on sign-up, sign-in,
  sign-out, the institution custom claim, the developer account script, consent-gated accounts,
  or connecting the app or tests to the Auth emulator.
---

Check the official page before relying on memory for an API or behavior. Fetch the specific page when you need exact signatures or limits.

| Topic | Link |
|---|---|
| Overview | https://firebase.google.com/docs/auth |
| Email + password on Android (sign-in) | https://firebase.google.com/docs/auth/android/password-auth |
| Current user, sign-out, ID tokens on Android | https://firebase.google.com/docs/auth/android/manage-users |
| Custom claims (institution role) | https://firebase.google.com/docs/auth/admin/custom-claims |
| Admin SDK: create and delete users (sign-up function, cleanup) | https://firebase.google.com/docs/auth/admin/manage-users |
| Auth emulator | https://firebase.google.com/docs/emulator-suite/connect_auth |

**ColPool constraints** (these win over generic doc examples):
- Students never create accounts from the app. The sign-up Cloud Function creates the account, user records and consent record in one step (ARCHITECTURE §3.2, §5).
- `@calbaptist.edu` only. No email verification, no password reset (POLICY §2).
- Only the developer script sets the institution claim (ARCHITECTURE §6; RC-07).
- A student account without a consent record can't use the app (RC-06).
