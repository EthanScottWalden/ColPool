import type { CampusPointDoc } from "./types.ts";

// Source: owner-provided Google Maps pins, 2026-11-16 (docs/data-model.md §2.7). Coordinates are verbatim, not rounded.
// Ids are the Firestore doc ids under `campusPoints/`; other Issues (route preview, Create Series) reference them.
// Adding or removing a point is a product change; to retire one, set `active: false`, never delete (RC-24 spirit).

export const CAMPUS_POINT_SEED: readonly (CampusPointDoc & { id: string })[] = [
  { id: "yeager-center", name: "Yeager Center", order: 1, lat: 33.9291442351809, lng: -117.42722312301498, active: true },
  { id: "music-building", name: "Music Building", order: 2, lat: 33.92768415162394, lng: -117.42566509459762, active: true },
  { id: "health-science", name: "Health Science", order: 3, lat: 33.9248353057463, lng: -117.42887115540034, active: true },
  { id: "lancer-plaza", name: "Lancer Plaza", order: 4, lat: 33.92874664207741, lng: -117.42216055893344, active: true },
  { id: "events-center", name: "Events Center", order: 5, lat: 33.927733947753204, lng: -117.42333726656787, active: true },
];

export const CAMPUS_POINT_IDS: readonly string[] = CAMPUS_POINT_SEED.map((p) => p.id);
