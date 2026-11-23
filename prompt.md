# Handoff: Issue #21 (notification writer), paused

Branch: feature/21-notification-writer. Delete this file in the PR's final commit (CLAUDE.md §5).

## (a) DONE
- functions/src/shared/notifications/: types.ts, validate.ts, text.ts, ids.ts, write.ts, index.ts.
  Typechecks (`cd functions && npx tsc -p tsconfig.test.json --noEmit` clean).
  Follows the settled plan: discriminated NotificationEvent, pure buildNotificationText, deterministic ids,
  writeNotification(writer, db, recipientUid, event) using create() + FieldValue.serverTimestamp(), read=false,
  weekdays stored Monday-first (one weekday for per-weekday events), optional fields omitted.
  Weekday ids are the existing "mon".."sun" from schema/weekday.ts. seriesLine = "[Host first]'s series from [pickup spot]".
  series_ended id = `series_ended_${seriesId}` and series_cancelled id = `series_cancelled_${seriesId}` (per-recipient subcollection).
- firestore.rules: new separate `match /users/{uid}/notifications/{notificationId}` block before the default-deny block
  (get/list for isStudentOwner; update only if affectedKeys hasOnly(['read']) and read is bool and == true; no create/delete).
- functions/test/rules/notifications.test.ts written (all allow/deny cases from the plan).

## (b) PARTLY done
- Rules tests: written but NEVER RUN.

## (c) NOT started
- Unit tests (emulator) in functions/test/functions/notifications.test.ts: text for every type incl. both series_ended audiences;
  1/2/3-day lists; end dates in DST weeks (2026-11-01, 2027-03-14); deterministic ids and duplicate create rejects (ALREADY_EXISTS);
  transaction that throws after write leaves nothing; batch write; createdAt server timestamp and read=false;
  invalid weekday / empty pickup spot rejected; privacy sentinels; static test that module source never mentions
  privateDetails, startingAddress or email (note validate.ts/types.ts comments must not contain those words; types.ts comment says "addresses, no emails" - reword to satisfy the static test if it greps "email").
- docs/adr/0005-notifications.md (decision, alternatives, "Provisional, owner to confirm" list).
- docs/data-model.md: new §2.9 + one row in §1 table (also fix the header line saying notifications are #21).
- docs/ARCHITECTURE.md §2 row: "Notification records and writer | #21 | users/{uid}/notifications, server-rendered text, deterministic ids, read-only flip; ADR 0005"; remove item from §9 if listed.
- Then run `cd functions && npm test` and report counts; push.

## (d) Last test run
Not run (only tsc typecheck). `functions/scripts/sandbox-npm.sh ci` already done in the worktree.

## (e) Decisions made by me / open questions
- Did NOT add a composite index (read + createdAt) to firestore.indexes.json to avoid merge conflicts with #20; "unread newest first" in production would need it. Unread count by where read==false works without it.
- Wording choices: rider_left body "[Rider] left your series on [Day]. A seat opened up." (per-weekday, singular); day_declined body "[Host] declined your request for [Day]."; names are "First Last".
- ID pattern for ids: /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/ (validate.ts); could reject an unusual #20 series id format.
- Provisional items to list in the ADR: layout, rules direction, per-weekday leave/withdraw, conflict/cancelled/host-ended wording, names frozen.
- The `node .claude/hooks/github.mjs issue-view 21` call was blocked by a hook error for me, so I never read the Issue body; I worked from the lead's plan only.

## (f) Merge note
#20 lands first on another branch and touches firestore.rules, docs/data-model.md, ARCHITECTURE §2 and functions/src/shared/.
Merge/rebase from origin/dev before continuing; keep the notifications rules block, §2.9 and the ARCHITECTURE row as separate blocks.
