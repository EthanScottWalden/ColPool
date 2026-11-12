# Firebase and Google Maps Platform setup

> **Issue:** #10 (TMP-01). **Sources:** SDD RC-04, RC-28, RISK-08; INTENT §1.3 "Billing comes first"; ARCHITECTURE §2, §6, §8.
> **Who:** the project owner does every console step. Agents have no Google Cloud credentials and never deploy (CLAUDE.md §7).

## 1. What exists

| Item | Value |
|---|---|
| Firebase / Google Cloud project | `colpool` ([console](https://console.firebase.google.com/project/colpool/)) |
| Environments | This one project only. Local dev and all tests use the Emulator Suite (INTENT §1.3). |
| Plan | Blaze (pay-as-you-go) |
| Region | `us-west2` for Firestore and Functions (ARCHITECTURE §2). Set when Firestore is created (#13). Permanent; don't create Firestore early in another region. |
| Billing account | The `colpool` project's Blaze billing account. The Maps APIs use the same account (owner decision 2026-11-11). |
| Budget: whole project | $10 / month, scoped to `colpool`, all services. Email alerts at 50%, 90% and 100% (owner decision 2026-11-11). |
| Budget: Maps (Places API + Routes API) | $5 / month, scoped to `colpool`, billing services Places API (New) + Google Maps Platform - Routes. Email alerts at 50%, 90% and 100% (owner decision 2026-11-11). |
| APIs enabled | Places API (New), Routes API |
| Routes key | Cloud Functions secret `ROUTES_API_KEY` (Secret Manager). Restricted to Routes API. |
| Places key | Android key, restricted to Places API (New). Android-app restriction (package + SHA-1) added in #11. Real value only in uncommitted `local.properties`. |

Budgets **alert**; they don't stop spending. Usage must stay within free allowances by design (RC-28): routes are computed only at review, Places uses session tokens, keys are restricted.

## 2. Console steps

Done once by the owner. Tick the checklist in §3 when done.

1. **Blaze plan.** Firebase console → `colpool` → ⚙ → *Usage and billing* → *Details & settings* → *Modify plan* → Blaze. Link the billing account.
2. **Project budget.** Google Cloud console → *Billing* → *Budgets & alerts* → *Create budget*.
   - Scope: project `colpool`, all services.
   - Amount and alert thresholds: §1.
   - Email alerts to billing admins.
3. **Enable Maps APIs.** Google Cloud console (project `colpool`) → *APIs & Services* → *Library*:
   - **Places API (New)**. The current Places SDK for Android uses it.
   - **Routes API**. Not the legacy Directions API (RC-14).
4. **Maps budget.** *Billing* → *Budgets & alerts* → *Create budget*.
   - Scope: project `colpool`, services **Places API (New)** and **Routes API**. The budget service list shows Routes as **Google Maps Platform - Routes**, not "Routes API". Pick that one.
   - Amount and thresholds: §1.
5. **Routes key (server).** *APIs & Services* → *Credentials* → *Create credentials* → *API key*.
   - Name it `routes-server`.
   - API restrictions: **Routes API** only.
   - Application restriction: none. Cloud Functions has no fixed IP. The compensating controls are the API restriction, the key never leaving Secret Manager, and the Maps budget alert.
   - Copy the value, then store it from a terminal signed in as the owner. Run this in the repo root after #12 adds `firebase.json`, or anywhere with `--project colpool`:
     ```
     firebase functions:secrets:set ROUTES_API_KEY --project colpool
     ```
     Paste the key at the prompt. Never put it in a file, commit, chat, Issue or CI.
6. **Places key (Android).** *Create credentials* → *API key*.
   - Name it `places-android`.
   - API restrictions: **Places API (New)** only.
   - Application restriction: set in #11, when the package name and debug/release SHA-1 exist.
   - Keep the value private. #11 reads it from uncommitted `local.properties` through the Secrets Gradle plugin. A committed placeholder lets CI build without it (ARCHITECTURE §6).

## 3. Verification checklist

Owner, in the console:

- [ ] Firebase console shows plan **Blaze** for `colpool`.
- [ ] *Budgets & alerts* lists both budgets with the §1 amounts and thresholds.
- [ ] *APIs & Services* → *Enabled APIs* lists Places API (New) and Routes API.
- [ ] *Credentials* shows `routes-server` (Routes API only) and `places-android` (Places API (New) only).
- [ ] `firebase functions:secrets:get ROUTES_API_KEY --project colpool` lists at least one version. It shows metadata only. Never run `functions:secrets:access`, which prints the value.

Agent, in the repo:

- [ ] `git grep -nE 'AIza[0-9A-Za-z_-]{35}'` returns nothing (standard Google API key shape).
- [ ] `git grep -nE '"private_key": *"-----BEGIN'` returns nothing (service-account key file content).
- [ ] `git check-ignore` matches `google-services.json`, `app/google-services.json`, `service-account.json`, `colpool-firebase-adminsdk-x.json`, `.env`, `functions/.env.local`, `local.properties`.

## 4. Later Issues that build on this

- **#11:** registers the Android app in Firebase. Adds `google-services.json` (ignored) and the Places key's Android-app restriction. `.gitignore` ignores every `google-services*.json` and `google-services.json*` name, including `.example` and `.template`. A committed placeholder needs a different name or a `!` exception.
- **#12:** adds `firebase.json` and `.firebaserc` (project `colpool`) for the Emulator Suite.
- **#13:** creates Firestore in `us-west2`.
- Route preview: reads `ROUTES_API_KEY` with `defineSecret`. The emulator uses a local override; tests never call the real Routes API.
