# ADR 0008: Route preview

- **Status:** Accepted for #28, with provisional items below
- **Issue:** #28 (TMP-16)
- **Scope:** the `routePreview` callable, its Routes API client, expected-time rule and draft write. Code: `functions/src/routePreview.ts`, `functions/src/routePreview/`. Depends on [ADR 0006](0006-series-draft-seat-layout.md) (draft layout) and [ADR 0007](0007-shared-series-validation.md) (validation).

## Contract
- Callable `routePreview`, region `us-west2`, secret `ROUTES_API_KEY` (`defineSecret`, `.value()` read inside the handler, never logged).
- Input `{ series: <untrusted SeriesInput>, draftId?: string }`; `draftId` must match `^[A-Za-z0-9]{20}$`. Output `{ draftId, inputHash, route }`.
- Flow: `requireStudent` → server clock → `loadValidationContext` → `validateSeriesInput` → (with a draft id) draft pre-check → Routes → `validateCampusTimes` → one transaction that writes the draft.
- Errors (details carry codes only, never address text or coordinates):

| Case | Error |
|---|---|
| Not signed in, institution account, no consent | `requireStudent`'s own errors |
| Input invalid, or campus departure before arrival | `invalid-argument` `series-invalid` with #23 `errors` (nothing written) |
| Malformed draft id | `invalid-argument` `draft-id-invalid` |
| Draft missing **or** not the caller's | `not-found` (same answer for both) |
| Draft already posted | `failed-precondition`, `reason: "draft-posted"` |
| No driving route | `invalid-argument` `route-failed`, `errors: [{ code: "routeNotFound", path }]` |
| Routes 429, 5xx or 10 s timeout | `unavailable`, `reason: "routes-unavailable"` |
| Routes other 4xx, or unusable response | `internal` |

`routeNotFound` is defined in the route-preview module; #23's `SERIES_ERROR_CODES` is unchanged. `path` uses #23's grammar: `pickupSpot`, `days.tue.toCampus.stops.1.address`, or the leg path `days.tue.toCampus` when no single address is at fault. Logs carry only the uid, weekday, direction and HTTP status.

## Decisions
- **Waypoints:** to campus is pickup spot, that leg's stops in order, that leg's campus point. The return is the campus point, the return leg's own stops in order (not reversed), the pickup spot. Every waypoint is `location.latLng`, never a place id: campus points have only coordinates, and stored place ids can go stale (L1, lead decision).
- **Request:** `POST https://routes.googleapis.com/directions/v2:computeRoutes`; body `origin`, `destination`, `intermediates`, `travelMode: "DRIVE"`, `routingPreference: "TRAFFIC_UNAWARE"`, `computeAlternativeRoutes: false`, no `departureTime` (RC-14, no live traffic). Headers `X-Goog-Api-Key`, `Content-Type`, and field mask `routes.legs.distanceMeters,routes.legs.duration`. The Google doc page confirms the endpoint, headers, body shape and `"165s"`-style durations; it shows no `legs` mask, `intermediates` or `TRAFFIC_UNAWARE` example, so those names come from the API reference and are checked for real by the live check below.
- **Parsing:** `routes[0].legs[]`. No `routes` is "no route". A missing `distanceMeters` or `duration` is 0 (proto3 JSON omits zero). A wrong leg count or an odd shape is `internal`, never "no route".
- **Per-leg rounding:** `legMin = Math.round(durationSec / 60)` per hop, then add up (data-model §2.11). Android uses the same rule.
- **Dedupe (RC-28 cost measure):** within one call, identical point lists (keyed by `canonicalJson(points)`) are sent once. Monday to Friday with the same route costs 2 calls, not 10. Identical legs give identical results, so this loses nothing.
- **Failure attribution:** only when a leg has no route, each host-entered address on that leg is tested once as a single hop to or from the leg's campus point (results shared across weekdays by the same dedupe); the first one with no route is named, otherwise the leg path. All failing legs are reported together.
- **Draft write:** one transaction, no external I/O inside it; create or overwrite rules in data-model §2.11. Re-checks owner and status inside the transaction.
- **Campus point coordinates** are read from `campusPoints` documents (`loadValidationContext` returns only ids).
- **Emulator:** with `FUNCTIONS_EMULATOR=true` **and** a project id starting `demo-`, the handler uses a straight-line stand-in (great-circle distance times 1.3 at 11 m/s). `emulators:dev` creates the git-ignored `functions/.secret.local` with a fake key so the secret binding loads.

## Provisional and plan
- `functions/test/fixtures/routes/riverside-1stop.json` is **hand-written** in the documented shape, not a real capture (see the README beside it). The Issue's layer 3 live check could not be run by the agent (no key, no network).
- **Merge-before-live-check plan (lead decision):** the PR merges to `dev` with layers 1 and 2 green. The owner then runs the live check ([DEVELOPMENT.md](../../DEVELOPMENT.md) §2), pastes the output into the PR, and replaces the fixture with the real capture in a follow-up commit. Android must not depend on exact Routes numbers before that.
- Unknown until the live check: whether Routes answers an unroutable pair (Honolulu) with 200 and no routes, as assumed, or with a 4xx (which this code would turn into `internal` and the live check would show as FAIL). If it is a 4xx, a follow-up maps that status to "no route".

## Review hardening
- Failure attribution (which address has no route) is capped at `MAX_ATTRIBUTION_CALLS` = 10 Routes calls per review; past that the error names the leg. A hostile all-unroutable input with 7 weekdays and 25 stops per direction costs at most 14 + 10 calls instead of about 370 (RC-28).
