---
name: firestore-docs
description: >
  Official Cloud Firestore docs links for ColPool. Use when designing collections, writing or
  testing Security Rules, using transactions for seats and permissions, adding listeners or
  indexes, or connecting to the Firestore emulator.
---

Check the official page before relying on memory for an API or behavior. Fetch the specific page when you need exact signatures or limits.

| Topic | Link |
|---|---|
| Overview | https://firebase.google.com/docs/firestore |
| Data model | https://firebase.google.com/docs/firestore/data-model |
| Security Rules: get started | https://firebase.google.com/docs/firestore/security/get-started |
| Security Rules: conditions (auth, claims, `get()`) | https://firebase.google.com/docs/firestore/security/rules-conditions |
| Rules unit tests (emulator) | https://firebase.google.com/docs/rules/unit-tests |
| Transactions and batched writes | https://firebase.google.com/docs/firestore/manage-data/transactions |
| Real-time listeners | https://firebase.google.com/docs/firestore/query-data/listen |
| Indexes | https://firebase.google.com/docs/firestore/query-data/indexing |
| Locations (permanent) | https://firebase.google.com/docs/firestore/locations |
| Firestore emulator | https://firebase.google.com/docs/emulator-suite/connect_firestore |

**ColPool constraints** (these win over generic doc examples):
- The client reads; the backend writes. Client writes are limited to ARCHITECTURE §3.4, never opened as scaffolding.
- Every rule has emulator tests for allow *and* deny cases before the feature merges (INTENT §1.3).
- Seats, request state, timestamps, search fields and permissions change together in one transaction (ARCHITECTURE §4, §7).
- Nothing is deleted. Server timestamps decide time (ARCHITECTURE §7).
- The database location (`us-west2`) is permanent. Creating the database is L3 (CLAUDE.md §7).
