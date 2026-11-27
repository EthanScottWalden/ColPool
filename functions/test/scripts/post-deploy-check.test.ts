import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { layerA, main, RULES_DENIAL, parseArgs, resolveConfig, ScriptError, type CliDeps, type FetchFn } from "../../scripts/post-deploy-check.ts";

const KEY = { FIREBASE_API_KEY: "fake-key" };

describe("post-deploy-check arguments and refusals (pure)", () => {
  it("parseArgs", () => {
    assert.deepEqual(parseArgs(["--project", "p-x1234"]), { project: "p-x1234", emulator: false, firestoreOnly: false, signedIn: false });
    assert.deepEqual(parseArgs(["--emulator", "--firestore-only", "--signed-in", "--email", "a@b.c"]), {
      emulator: true, firestoreOnly: true, signedIn: true, email: "a@b.c",
    });
    assert.throws(() => parseArgs(["--signed-in"]), ScriptError);
    assert.throws(() => parseArgs(["--email", "a@b.c"]), ScriptError);
    assert.throws(() => parseArgs(["--password", "x"]), /use stdin/);
    assert.throws(() => parseArgs(["--project"]), ScriptError);
    assert.throws(() => parseArgs(["--nope"]), ScriptError);
  });

  it("refuses without --project and without --emulator", () => {
    assert.throws(() => resolveConfig(parseArgs([]), {}), ScriptError);
  });
  it("refuses a demo- project unless --emulator", () => {
    assert.throws(() => resolveConfig(parseArgs(["--project", "demo-colpool"]), {}), /demo-/);
  });
  it("--emulator rejects another project and non-loopback hosts", () => {
    assert.throws(() => resolveConfig(parseArgs(["--emulator", "--project", "colpool"]), {}), ScriptError);
    assert.throws(() => resolveConfig(parseArgs(["--emulator"]), { FIRESTORE_EMULATOR_HOST: "10.0.0.5:8080" }), /this machine/);
  });
  it("--signed-in on a real project needs FIREBASE_API_KEY", () => {
    assert.throws(() => resolveConfig(parseArgs(["--project", "colpool", "--signed-in", "--email", "a@b.c"]), {}), /FIREBASE_API_KEY/);
    assert.equal(resolveConfig(parseArgs(["--project", "colpool", "--signed-in", "--email", "a@b.c"]), KEY).apiKey, "fake-key");
  });
  it("real project URLs", () => {
    const c = resolveConfig(parseArgs(["--project", "colpool"]), {});
    assert.equal(c.firestoreBase, "https://firestore.googleapis.com");
    assert.equal(c.functionUrl("signUp"), "https://us-west2-colpool.cloudfunctions.net/signUp");
  });
  it("emulator URLs use the env hosts", () => {
    const c = resolveConfig(parseArgs(["--emulator"]), { FIRESTORE_EMULATOR_HOST: "127.0.0.1:9", FUNCTIONS_EMULATOR_HOST: "localhost:7" });
    assert.equal(c.firestoreBase, "http://127.0.0.1:9");
    assert.equal(c.functionUrl("signUp"), "http://localhost:7/demo-colpool/us-west2/signUp");
  });
});

function deps(over: Partial<CliDeps> & { fetch: FetchFn }): CliDeps & { lines: string[] } {
  const lines: string[] = [];
  return { env: {}, readPassword: async () => "pw", out: (l) => lines.push(l), err: (l) => lines.push(l), lines, ...over };
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status });
const denied = () => json(403, { error: { code: 403, status: "PERMISSION_DENIED", message: "Missing or insufficient permissions." } });

describe("post-deploy-check main (fake fetch)", () => {
  it("refuses before any network call and exits 2", async () => {
    let calls = 0;
    const fetch: FetchFn = async () => { calls++; return denied(); };
    for (const argv of [[], ["--project", "demo-colpool"], ["--project", "colpool", "--signed-in", "--email", "a@b.c"]]) {
      const d = deps({ fetch });
      assert.equal(await main(argv, d), 2);
    }
    assert.equal(calls, 0);
  });

  it("Layer A firestore-only: all denials pass; a 200 or a non-Rules 403 fails", async () => {
    const all = deps({ fetch: async () => denied() });
    assert.equal(await main(["--project", "colpool", "--firestore-only"], all), 0);
    assert.match(all.lines.join("\n"), /SKIPPED.*signUp/);

    const open = deps({ fetch: async (url) => (String(url).includes("/series") ? json(200, {}) : denied()) });
    assert.equal(await main(["--project", "colpool", "--firestore-only"], open), 1);

    const apiOff = deps({ fetch: async () => json(403, { error: { status: "PERMISSION_DENIED", message: "Cloud Firestore API has not been used in project 1 before or it is disabled." } }) });
    assert.equal(await main(["--project", "colpool", "--firestore-only"], apiOff), 1);
    assert.match(apiOff.lines.join("\n"), /not a Rules denial/);
  });

  it("RULES_DENIAL accepts the real and emulator denial texts and nothing else", () => {
    assert.ok(RULES_DENIAL.test("Missing or insufficient permissions."));
    assert.ok(RULES_DENIAL.test("\nfalse for 'list' @ L156, false for 'list' @ L186"));
    for (const m of ["Permission denied on resource project 1.", "Request had insufficient authentication scopes.", "The caller does not have permission", "quota exceeded"]) {
      assert.ok(!RULES_DENIAL.test(m), m);
    }
  });

  it("Layer A sends only GET, runQuery POST and PATCH probes with currentDocument.exists=true", async () => {
    const seen: string[] = [];
    await layerA(resolveConfig(parseArgs(["--project", "colpool", "--firestore-only"]), {}), { results: [], add() {}, skip() {}, async expect(_w, url, init) { seen.push(`${init.method} ${url}`); } });
    const patches = seen.filter((s) => s.startsWith("PATCH"));
    assert.equal(patches.length, 4);
    for (const p of patches) assert.match(p, /currentDocument\.exists=true/);
    assert.ok(seen.some((s) => s.startsWith("POST") && s.endsWith(":runQuery")));
    assert.ok(seen.every((s) => /^(GET|POST|PATCH) /.test(s)));
  });

  it("Layer B: key-restricted sign-in (403) is SKIPPED, a bad password is FAIL, and nothing secret is printed", async () => {
    const base = (signIn: Response): FetchFn => async (url) => (String(url).includes("accounts:signInWithPassword") ? signIn : denied());
    const argv = ["--project", "colpool", "--firestore-only", "--signed-in", "--email", "a@b.c"];
    const skipped = deps({ env: KEY, fetch: base(json(403, { error: { message: "API_KEY_HTTP_REFERRER_BLOCKED" } })), readPassword: async () => "s3cret-pw" });
    assert.equal(await main(argv, skipped), 0);
    assert.match(skipped.lines.join("\n"), /SKIPPED.*Sign in/);
    const bad = deps({ env: KEY, fetch: base(json(400, { error: { message: "INVALID_LOGIN_CREDENTIALS" } })), readPassword: async () => "s3cret-pw" });
    assert.equal(await main(argv, bad), 1);
    for (const d of [skipped, bad]) assert.ok(!d.lines.join("\n").includes("s3cret-pw") && !d.lines.join("\n").includes("fake-key"));
  });

  it("Layer B: passes with the expected answers and never prints the token", async () => {
    const fetch: FetchFn = async (url, init) => {
      const u = String(url);
      if (u.includes("accounts:signInWithPassword")) return json(200, { idToken: "TOKEN-VALUE", localId: "uid1" });
      const authed = (init?.headers as Record<string, string> | undefined)?.["authorization"] === "Bearer TOKEN-VALUE";
      if (!authed) return u.endsWith("/signUp") ? json(400, { error: { status: "INVALID_ARGUMENT" } }) : u.includes("cloudfunctions") ? json(401, { error: { status: "UNAUTHENTICATED" } }) : denied();
      if (u.includes("cloudfunctions")) return json(400, { error: { status: "INVALID_ARGUMENT" } });
      if (u.includes("/privateDetails/") || u.includes("/consents/")) return denied();
      return json(200, { name: "x", fields: { secret: { stringValue: "DOC-CONTENT" } } });
    };
    const d = deps({ env: KEY, fetch });
    assert.equal(await main(["--project", "colpool", "--signed-in", "--email", "a@b.c"], d), 0);
    const text = d.lines.join("\n");
    assert.ok(!text.includes("TOKEN-VALUE") && !text.includes("DOC-CONTENT"));
    assert.match(text, /Signed-in call to sendRequest with a bad id is rejected/);
  });
});
