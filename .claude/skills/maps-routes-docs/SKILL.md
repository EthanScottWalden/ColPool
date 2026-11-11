---
name: maps-routes-docs
description: >
  Official Google Maps Routes API docs links for ColPool. Use when working on the route-preview
  function (Compute Routes per weekday and direction), waypoints and stops, field masks, or
  Routes usage and billing.
---

Check the official page before relying on memory for an API or behavior. Fetch the specific page when you need exact request fields or limits.

| Topic | Link |
|---|---|
| Overview | https://developers.google.com/maps/documentation/routes |
| Compute Routes | https://developers.google.com/maps/documentation/routes/compute_route_directions |
| Origins, destinations, waypoints (place IDs) | https://developers.google.com/maps/documentation/routes/specify_location |
| Field masks (request only what's needed) | https://developers.google.com/maps/documentation/routes/choose_fields |
| Usage and billing | https://developers.google.com/maps/documentation/routes/usage-and-billing |
| Pricing and free allowances | https://developers.google.com/maps/billing-and-pricing/pricing |
| March 2025 changes (Directions is Legacy) | https://developers.google.com/maps/billing-and-pricing/march-2025 |
| API key security | https://developers.google.com/maps/api-security-best-practices |

**ColPool constraints** (these win over generic doc examples):
- Called only by the route-preview function, once per weekday × direction. No live traffic. Never the legacy Directions API (ARCHITECTURE §2, §5; RC-14).
- Search, viewing a series and the export never call a Maps service (RC-28).
- Results are stored on the draft, and posting saves exactly what was reviewed. Never recalculated after posting (RC-12).
- The key lives only in the backend's secret storage (ARCHITECTURE §6).
