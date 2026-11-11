---
name: cloud-functions-docs
description: >
  Official Firebase Cloud Functions docs links for ColPool. Use when writing or testing a callable
  function, the daily end-of-series job, secrets such as the Routes key, function region, or
  running functions on the emulator.
---

Check the official page before relying on memory for an API or behavior. Fetch the specific page when you need exact signatures or limits.

| Topic | Link |
|---|---|
| Overview | https://firebase.google.com/docs/functions |
| Callable functions (`onCall`, auth context, errors) | https://firebase.google.com/docs/functions/callable |
| TypeScript setup | https://firebase.google.com/docs/functions/typescript |
| Scheduled functions (daily job, time zone) | https://firebase.google.com/docs/functions/schedule-functions |
| Config and secrets (Routes key) | https://firebase.google.com/docs/functions/config-env |
| Locations / region | https://firebase.google.com/docs/functions/locations |
| Unit testing | https://firebase.google.com/docs/functions/unit-testing |
| Functions emulator | https://firebase.google.com/docs/emulator-suite/connect_functions |

**ColPool constraints** (these win over generic doc examples):
- Every trusted action is a callable function, including the export. No HTTP endpoints for the app (ARCHITECTURE §2).
- Region `us-west2`. The daily job runs shortly after midnight America/Los_Angeles and is idempotent (ARCHITECTURE §2, §3.2).
- The Routes key lives only in secret storage. Nothing in the repo or CI (ARCHITECTURE §6).
- Shared modules exist once under `functions/src/shared/` and change only through their contract Issue (ARCHITECTURE §3.3).
- Never deploy. Deploys are human-only (CLAUDE.md §7).
