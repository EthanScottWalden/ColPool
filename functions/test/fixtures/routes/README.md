# Routes API fixtures

`riverside-1stop.json` is **PROVISIONAL**. It is hand-written in the documented Compute Routes response shape (`routes[].legs[].distanceMeters` and `duration` such as `"612s"`), not a real capture. The numbers are made up.

The owner replaces it with a real response using the live check (DEVELOPMENT.md §2, "Routes live check"):

```
cd functions && ROUTES_API_KEY="$(gcloud secrets versions access latest --secret=ROUTES_API_KEY --project colpool)" npm run routes:live-check -- --capture test/fixtures/routes/riverside-1stop.json
```

The capture holds only the first response body: no key, no headers. The fixture test fails if the file contains `AIza` or `key`.
