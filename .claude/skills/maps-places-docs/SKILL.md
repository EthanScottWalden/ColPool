---
name: maps-places-docs
description: >
  Official Google Places SDK for Android docs links for ColPool. Use when building address entry
  (starting address, pickup spot, stops), autocomplete session tokens, the Places API key setup
  with the Secrets Gradle plugin, or Places usage and billing.
---

Check the official page before relying on memory for an API or behavior. Fetch the specific page when you need exact signatures or limits.

| Topic | Link |
|---|---|
| Overview | https://developers.google.com/maps/documentation/places/android-sdk |
| Place Autocomplete (incl. session tokens) | https://developers.google.com/maps/documentation/places/android-sdk/place-autocomplete |
| API key via Secrets Gradle plugin | https://developers.google.com/maps/documentation/places/android-sdk/secrets-gradle-plugin |
| Usage and billing | https://developers.google.com/maps/documentation/places/android-sdk/usage-and-billing |
| Pricing and free allowances | https://developers.google.com/maps/billing-and-pricing/pricing |
| API key security (app restrictions) | https://developers.google.com/maps/api-security-best-practices |

**ColPool constraints** (these win over generic doc examples):
- Only a chosen suggestion is accepted, and its place ID and coordinates are stored with the address (RC-13).
- Use session tokens. No map display, GPS or current location (POLICY §13; RC-28).
- The key ships in the app and is public by design. Protect it with API and Android app restrictions, quotas and budget alerts. Real key only in uncommitted `local.properties`; committed placeholder for CI (ARCHITECTURE §6).
- The starting address is private and used only for ranking (POLICY §3.3).
