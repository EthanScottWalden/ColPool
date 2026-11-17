import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { main, parseArgs, resolveTarget, seedReferenceData, ScriptError } from "../../scripts/seed-reference-data.ts";
import { CAMPUS_POINT_SEED } from "../../src/shared/schema/campusPoints.ts";
import { buildProfileOptionsDoc, MAJOR_OPTIONS } from "../../src/shared/schema/options.ts";

const app = initializeApp({ projectId: "demo-colpool" }, "seed-reference-data-test");
const db = getFirestore(app);
const EXTRA = "test-extra-point";

async function snapshot() {
  const points = await db.collection("campusPoints").get();
  const options = await db.doc("options/profile").get();
  return { points: points.docs.map((d) => [d.id, d.data()]), options: options.data() };
}

describe("seed-reference-data (emulator)", () => {
  before(() => {
    assert.ok(process.env["FIRESTORE_EMULATOR_HOST"], "run via emulators:exec");
  });
  after(async () => {
    await db.doc(`campusPoints/${EXTRA}`).delete();
    for (const p of CAMPUS_POINT_SEED) await db.doc(`campusPoints/${p.id}`).delete();
    await db.doc("options/profile").delete();
  });

  it("writes the 5 owner-provided points with exact fields and no placeId", async () => {
    await seedReferenceData(db);
    assert.equal(CAMPUS_POINT_SEED.length, 5);
    for (const { id, ...expected } of CAMPUS_POINT_SEED) {
      const data = (await db.doc(`campusPoints/${id}`).get()).data();
      assert.deepEqual(data, expected);
      assert.equal(data?.["active"], true);
      assert.ok(!("placeId" in (data ?? {})));
    }
    const yeager = (await db.doc("campusPoints/yeager-center").get()).data();
    assert.equal(yeager?.["lat"], 33.9291442351809);
    assert.equal(yeager?.["lng"], -117.42722312301498);
    assert.equal((await db.doc("campusPoints/events-center").get()).data()?.["lat"], 33.927733947753204);
  });

  it("writes options/profile matching options.ts, with id arrays and 127 majors", async () => {
    await seedReferenceData(db);
    const data = (await db.doc("options/profile").get()).data();
    assert.deepEqual(data, buildProfileOptionsDoc());
    assert.equal(data?.["majors"].length, 127);
    assert.equal(data?.["majorIds"].length, 127);
    assert.equal(MAJOR_OPTIONS.length, 127);
    assert.deepEqual(data?.["genderIds"], data?.["genders"].map((e: { id: string }) => e.id));
  });

  it("is idempotent", async () => {
    await seedReferenceData(db);
    const first = await snapshot();
    await seedReferenceData(db);
    assert.deepEqual(await snapshot(), first);
  });

  it("does not delete or alter a pre-existing extra point", async () => {
    const extra = { name: "Extra", lat: 1, lng: 2, order: 99, active: true };
    await db.doc(`campusPoints/${EXTRA}`).set(extra);
    await seedReferenceData(db);
    assert.deepEqual((await db.doc(`campusPoints/${EXTRA}`).get()).data(), extra);
  });

  it("main seeds via the CLI path and prints a summary", async () => {
    const out: string[] = [];
    const code = await main([], { env: { FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080" }, out: (l) => out.push(l), err: () => {}, db });
    assert.equal(code, 0);
    assert.match(out[0] ?? "", /Seeded 5 campus points/);
  });
});

describe("seed-reference-data guard (pure)", () => {
  const prod = { GOOGLE_APPLICATION_CREDENTIALS: "/outside/key.json" };
  it("refuses with no emulator env and no --project", () => {
    assert.throws(() => resolveTarget({}, {}), ScriptError);
    assert.throws(() => resolveTarget({}, prod), ScriptError);
  });
  it("refuses a demo- project in production mode", () => {
    assert.throws(() => resolveTarget({ project: "demo-anything" }, prod), ScriptError);
  });
  it("refuses production without credentials", () => {
    assert.throws(() => resolveTarget({ project: "real-proj" }, {}), ScriptError);
  });
  it("accepts the emulator and a real project with credentials", () => {
    assert.deepEqual(resolveTarget({}, { FIRESTORE_EMULATOR_HOST: "h:1" }), { mode: "emulator", projectId: "demo-colpool" });
    assert.deepEqual(resolveTarget({ project: "real-proj" }, prod), { mode: "production", projectId: "real-proj" });
  });
  it("emulator mode rejects a different --project", () => {
    assert.throws(() => resolveTarget({ project: "real-proj" }, { FIRESTORE_EMULATOR_HOST: "h:1" }), ScriptError);
  });
  it("parseArgs rejects unknown flags", () => {
    assert.throws(() => parseArgs(["--force"]), ScriptError);
    assert.throws(() => parseArgs(["--project"]), ScriptError);
    assert.deepEqual(parseArgs(["--project", "x"]), { project: "x" });
  });
  it("main refuses and exits 1 without a target", async () => {
    const errs: string[] = [];
    assert.equal(await main([], { env: {}, out: () => {}, err: (l) => errs.push(l) }), 1);
    assert.match(errs[0] ?? "", /Refusing/);
  });
});
