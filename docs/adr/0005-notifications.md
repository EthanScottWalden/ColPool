# ADR 0005: Notification records and writer

- **Status:** Accepted for #21. Items 1-3 (layout, fields, fixed ids) confirmed final by the owner 2026-11-26; wording items 4-8 stay provisional
- **Issue:** #21 (TMP-28), contract
- **Scope:** `functions/src/shared/notifications/`, the `users/{uid}/notifications` rules block, [data model](../data-model.md) §2.9. Depends on [ADR 0002](0002-firestore-layout.md). No push, email or other channels (RC-09, OQ-05).

## Decision
- **Writer:** `writeNotification(writer, db, recipientUid, event)` adds one `create()` to a Firestore `Transaction` or `WriteBatch` and returns the id. It does not read. Every action that notifies calls it in the same transaction as its change.
- **Inputs:** only person names (first, last), weekday ids, `{hostName, pickupSpotName}`, an end date `YYYY-MM-DD` and a rider uid where the id needs it. It never reads `users` or `privateDetails` and takes no free text.
- **Text:** `buildNotificationText(event)` is pure and server-side. Titles and bodies follow [Appendix B](../backlog-proposal.md) and POLICY §11. Names are "First Last", frozen at event time. End dates are formatted by splitting the string ("Oct 9"), so daylight saving cannot shift them. Day lists read "Tuesday", "Tuesday and Thursday", "Monday, Tuesday and Thursday".
- **Ids:** deterministic (data model §2.9), so a retry or double call fails with ALREADY_EXISTS inside the transaction and rolls back. Series ids are 20-character alphanumeric Firestore ids; the id pattern `[A-Za-z0-9][A-Za-z0-9_-]{0,127}` accepts them.
- **Rules:** recipient may get and list; update only `read`, only to `true`; create and delete denied; institution accounts and unconsented users denied; no collection-group rule.
- **Subcollection name** lives in the notifications module (`NOTIFICATIONS_SUBCOLLECTION`); #13's `collections.ts` is unchanged.
- **Indexes:** none. Unread count: `where read == false`. History: `orderBy createdAt`. "Unread newest first" in one query would need a composite index on `read` and `createdAt`, to be added in #25.
- **Cancelled and ended:** pending weekdays become declined without per-weekday notifications; the single `series_cancelled` or `series_ended` notification covers them (US-06, US-15).

## Alternatives rejected
- Top-level `notifications` collection with `recipientUid`: needs a collection-group-style rule and an index, and widens leak risk.
- Random ids: a retried function would duplicate notifications.
- Callers pass finished text: wording drifts and free text could carry private data.

## Owner decision 2026-11-26: items 1-3 are final for the first real deploy
1. Layout `users/{uid}/notifications/{id}` and the field set (no actor uid, no `null`s).
2. Rules direction: read-only flip to `true`, no client create or delete.
3. Leaving or withdrawing several days writes one notification per weekday (deterministic ids).

## Provisional, owner to confirm (wording only)
Stored wording affects new documents only, so changing it later does not touch what is already stored.
4. Conflict auto-decline text: "[Day] in [Host]'s series was declined automatically because you were accepted on [Day] in another series." It never names the other series.
5. Cancelled to riders: "[Host] cancelled a series you requested to join." (Appendix B says "you were in".)
6. Ended, to the host: "Your series reached its end date on [date]."; to riders: "[Host]'s series reached its end date on [date]."
7. Names frozen at event time; "rider left" body is singular per weekday ("... A seat opened up.") where Appendix B's wording was plural.
8. `seriesLine` is "[Host first name]'s series from [pickup spot]".
