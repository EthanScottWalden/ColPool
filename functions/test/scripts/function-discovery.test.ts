import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// Rehearses what `firebase deploy` does: load the built lib/index.js outside the emulator and list its endpoints.
// Needs `npm run build` first (pretest does it).
const indexJs = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "lib", "index.js");

describe("function discovery (deploy rehearsal)", () => {
  it("exports exactly signUp, routePreview, postSeries, sendRequest, all in us-west2 (no ping)", () => {
    const env = { ...process.env };
    delete env["FUNCTIONS_EMULATOR"];
    const script = `const m = await import(${JSON.stringify(pathToFileURL(indexJs).href)});
      console.log(JSON.stringify(Object.entries(m).filter(([, v]) => v !== undefined).map(([k, v]) => [k, v?.__endpoint?.region ?? null, v?.__endpoint?.callableTrigger ? "callable" : "other"])));`;
    const out = execFileSync(process.execPath, ["--input-type=module", "-e", script], { env, encoding: "utf8" });
    const rows = (JSON.parse(out.trim().split("\n").pop() as string) as [string, string[] | null, string][]).sort((a, b) => a[0].localeCompare(b[0]));
    assert.deepEqual(
      rows.map((r) => r[0]),
      ["postSeries", "routePreview", "sendRequest", "signUp"],
    );
    for (const [name, region, kind] of rows) {
      assert.deepEqual(region, ["us-west2"], `${name} region`);
      assert.equal(kind, "callable", `${name} trigger`);
    }
  });
});
