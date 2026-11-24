// Developer/owner-run live check of the Routes API (#28, layer 3). Never run in CI or by an agent: it
// needs the real key and spends a few Routes calls (RC-28). Usage: DEVELOPMENT.md §2 "Routes live check".
//   ROUTES_API_KEY=... npm run routes:live-check -- [--capture <path>]
// Prints UTC time, call count, meters, seconds, miles, minutes and PASS/FAIL. Never prints the key or headers.
// `--capture` writes only the body of the first response (no key, no headers) for the saved fixture.
import { writeFileSync } from "node:fs";
import { CAMPUS_POINT_SEED } from "../src/shared/schema/campusPoints.ts";
import { createHttpRoutesClient, type FetchLike, type LatLng } from "../src/routePreview/routesClient.ts";

const args = process.argv.slice(2);
const capIdx = args.indexOf("--capture");
const capturePath = capIdx >= 0 ? args[capIdx + 1] : undefined;
if (capIdx >= 0 && (capturePath === undefined || capturePath.startsWith("--"))) {
  console.error("--capture needs a file path.");
  process.exit(2);
}
const key = process.env["ROUTES_API_KEY"];
if (!key) {
  console.error("Set ROUTES_API_KEY in the environment (see DEVELOPMENT.md §2, Routes live check).");
  process.exit(2);
}

// Public landmarks, approximate coordinates.
const MISSION_INN: LatLng = { lat: 33.9813, lng: -117.374 };
const RIVERSIDE_PLAZA: LatLng = { lat: 33.9588, lng: -117.4005 };
const ALOHA_TOWER: LatLng = { lat: 21.3069, lng: -157.866 };
const yeager = CAMPUS_POINT_SEED.find((p) => p.id === "yeager-center")!;
const YEAGER: LatLng = { lat: yeager.lat, lng: yeager.lng };

let calls = 0;
let captured = false;
const countingFetch: FetchLike = async (url, init) => {
  calls++;
  const res = await fetch(url, init);
  if (capturePath !== undefined && !captured) {
    captured = true;
    writeFileSync(capturePath, `${JSON.stringify(JSON.parse(await res.clone().text()), null, 2)}\n`);
  }
  return res;
};
const client = createHttpRoutesClient(key, countingFetch);

console.log(`Routes live check, ${new Date().toISOString()} (UTC)`);
let pass = true;

try {
  const r = await client.computeRoute([MISSION_INN, RIVERSIDE_PLAZA, YEAGER]);
  if (!r.ok) throw new Error("no route for the Riverside test");
  const meters = r.legs.reduce((s, l) => s + l.meters, 0);
  const seconds = r.legs.reduce((s, l) => s + l.durationSec, 0);
  const miles = meters / 1609.344;
  const minutes = seconds / 60;
  const ok = miles >= 1 && miles <= 30 && minutes >= 2 && minutes <= 60;
  console.log(`Riverside via 1 stop: meters=${meters} seconds=${seconds} miles=${miles.toFixed(1)} minutes=${minutes.toFixed(1)} ${ok ? "PASS" : "FAIL"}`);
  pass &&= ok;
} catch (e) {
  console.log(`Riverside via 1 stop: FAIL (${e instanceof Error ? e.constructor.name : "error"}${(e as { status?: number }).status ? ` status ${(e as { status?: number }).status}` : ""})`);
  pass = false;
}

try {
  const r = await client.computeRoute([ALOHA_TOWER, YEAGER]);
  console.log(`Honolulu pickup to campus: ${r.ok ? "FAIL (a route was returned)" : "PASS (no route, would report routeNotFound at pickupSpot)"}`);
  pass &&= !r.ok;
} catch (e) {
  console.log(`Honolulu pickup to campus: FAIL (${e instanceof Error ? e.constructor.name : "error"}${(e as { status?: number }).status ? ` status ${(e as { status?: number }).status}` : ""}); the API did not answer "no route" with a 200`);
  pass = false;
}

console.log(`Calls made: ${calls}${capturePath !== undefined ? `; first response saved to ${capturePath}` : ""}`);
console.log(pass ? "RESULT: PASS" : "RESULT: FAIL");
process.exit(pass ? 0 : 1);
