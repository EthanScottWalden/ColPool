import { describe, it, before } from "node:test";
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { main as seedMain } from "../../scripts/seed-reference-data.ts";
import { getFirestore } from "firebase-admin/firestore";
import { initializeApp } from "firebase-admin/app";

// Rehearses DEVELOPMENT.md "Real-project deploy" step "post-deploy check" against all three emulators.
// Fake data only. The student is created through the signUp callable, exactly as the app does.
const script = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "scripts", "post-deploy-check.ts");
const EMAIL = `test.postdeploy.${Date.now()}@calbaptist.edu`;
const PASSWORD = "fake-pass-for-e2e-1";

function run(args: string[], stdin?: string): Promise<{ code: number; out: string }> {
  return new Promise((resolve) => {
    const child = execFile(process.execPath, [script, ...args], { env: process.env }, (err, stdout, stderr) => {
      resolve({ code: err ? ((err as { code?: number }).code ?? 1) : 0, out: stdout + stderr });
    });
    child.stdin?.end(stdin ?? "");
  });
}

describe("post-deploy-check against the emulators", () => {
  before(async () => {
    const db = getFirestore(initializeApp({ projectId: "demo-colpool" }, "post-deploy-e2e"));
    assert.equal(await seedMain([], { env: process.env, out: () => {}, err: (l) => assert.fail(l), db }), 0);
    const fn = process.env["FUNCTIONS_EMULATOR_HOST"] ?? "127.0.0.1:5001";
    const res = await fetch(`http://${fn}/demo-colpool/us-west2/signUp`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ data: { email: EMAIL, password: PASSWORD, firstName: "Test", lastName: "Postdeploy", consent: true } }),
    });
    assert.equal(res.status, 200);
  });

  it("Layer A passes with no credentials", async () => {
    const r = await run(["--emulator"]);
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /0 failed/);
  });

  it("Layer A and B pass for a signed-in student, and print no token", async () => {
    const r = await run(["--emulator", "--signed-in", "--email", EMAIL], `${PASSWORD}\n`);
    assert.equal(r.code, 0, r.out);
    assert.match(r.out, /PASS .*Signed-in student reads own users/);
    assert.match(r.out, /0 failed, 0 skipped/);
    assert.ok(!r.out.includes(PASSWORD) && !/eyJ/.test(r.out));
  });

  it("a wrong password is a FAIL with a nonzero exit", async () => {
    const r = await run(["--emulator", "--signed-in", "--email", EMAIL], "wrong-password-1\n");
    assert.equal(r.code, 1, r.out);
    assert.match(r.out, /FAIL .*Sign in/);
  });
});
