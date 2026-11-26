// Developer-run, read-only check of a deployed (or emulated) ColPool backend (#151).
// Layer A needs no credentials: Rules deny unauthenticated reads and writes, callables refuse anonymous calls.
// Layer B (--signed-in) signs in as an app-created student and checks the allowed paths and input validation.
// Writes nothing, creates no accounts, prints no tokens, keys, passwords or document contents.
// Uses only Node's built-in fetch. Usage and expected output: DEVELOPMENT.md "Real-project deploy".
import { randomBytes } from "node:crypto";

export const EMULATOR_PROJECT_ID = "demo-colpool";
export const REGION = "us-west2";
const LOOPBACK = new Set(["127.0.0.1", "localhost", "[::1]", "::1"]);

export class ScriptError extends Error {}

export interface CliArgs {
  project?: string;
  emulator: boolean;
  firestoreOnly: boolean;
  signedIn: boolean;
  email?: string;
}

export function parseArgs(argv: readonly string[]): CliArgs {
  const out: CliArgs = { emulator: false, firestoreOnly: false, signedIn: false };
  for (let i = 0; i < argv.length; i++) {
    const flag = argv[i] as string;
    if (flag === "--project" || flag === "--email") {
      const value = argv[++i];
      if (value === undefined || value.startsWith("--")) throw new ScriptError(`${flag} needs a value.`);
      if (flag === "--project") out.project = value;
      else out.email = value;
    } else if (flag === "--emulator") out.emulator = true;
    else if (flag === "--firestore-only") out.firestoreOnly = true;
    else if (flag === "--signed-in") out.signedIn = true;
    else throw new ScriptError(`Unknown argument: ${flag.startsWith("--password") ? "--password (use stdin)" : flag}`);
  }
  if (out.signedIn && !out.email) throw new ScriptError("--signed-in needs --email <addr> (the password is read from stdin).");
  if (!out.signedIn && out.email !== undefined) throw new ScriptError("--email is only used with --signed-in.");
  return out;
}

export interface Config {
  projectId: string;
  emulator: boolean;
  firestoreOnly: boolean;
  signedIn: boolean;
  email?: string;
  firestoreBase: string;
  authBase: string;
  functionUrl: (fn: string) => string;
  /** Web API key for sign-in (a public client key). Fake in the emulator. */
  apiKey: string;
}

function hostOf(value: string): string {
  return value.replace(/:\d+$/, "");
}

/** Pure guard: decides what the script may contact. Throws before any network call. */
export function resolveConfig(args: CliArgs, env: Record<string, string | undefined>): Config {
  if (args.emulator) {
    if (args.project !== undefined && args.project !== EMULATOR_PROJECT_ID) {
      throw new ScriptError(`--emulator always uses project ${EMULATOR_PROJECT_ID}; drop --project.`);
    }
    const fs = env["FIRESTORE_EMULATOR_HOST"] ?? "127.0.0.1:8080";
    const au = env["FIREBASE_AUTH_EMULATOR_HOST"] ?? "127.0.0.1:9099";
    const fn = env["FUNCTIONS_EMULATOR_HOST"] ?? "127.0.0.1:5001";
    for (const h of [fs, au, fn]) {
      if (!LOOPBACK.has(hostOf(h))) throw new ScriptError("Refusing to run: the emulator hosts must be on this machine (127.0.0.1 or localhost).");
    }
    return {
      projectId: EMULATOR_PROJECT_ID,
      emulator: true,
      firestoreOnly: args.firestoreOnly,
      signedIn: args.signedIn,
      ...(args.email !== undefined ? { email: args.email } : {}),
      firestoreBase: `http://${fs}`,
      authBase: `http://${au}/identitytoolkit.googleapis.com`,
      functionUrl: (name) => `http://${fn}/${EMULATOR_PROJECT_ID}/${REGION}/${name}`,
      apiKey: "fake-api-key",
    };
  }
  if (!args.project) throw new ScriptError("Pass --project <id> (or --emulator).");
  if (args.project.startsWith("demo-")) throw new ScriptError("Refusing to run: a demo- project is emulator-only. Use --emulator.");
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(args.project)) throw new ScriptError("--project is not a valid project id.");
  const projectId = args.project;
  let apiKey = "";
  if (args.signedIn) {
    apiKey = env["FIREBASE_API_KEY"] ?? "";
    if (!apiKey) throw new ScriptError("--signed-in needs the app's Web API key in the FIREBASE_API_KEY environment variable.");
  }
  return {
    projectId,
    emulator: false,
    firestoreOnly: args.firestoreOnly,
    signedIn: args.signedIn,
    ...(args.email !== undefined ? { email: args.email } : {}),
    firestoreBase: "https://firestore.googleapis.com",
    authBase: "https://identitytoolkit.googleapis.com",
    functionUrl: (name) => `https://${REGION}-${projectId}.cloudfunctions.net/${name}`,
    apiKey,
  };
}

export type Verdict = "PASS" | "FAIL" | "SKIPPED";
export interface CheckResult {
  what: string;
  http: number | null;
  grpc: string;
  verdict: Verdict;
  note?: string;
}

export type FetchFn = (url: string, init?: RequestInit) => Promise<Response>;

interface Expectation {
  http: number;
  /** gRPC-style status in the error body (PERMISSION_DENIED, UNAUTHENTICATED, ...). Omit for success. */
  grpc?: string;
  /** Firestore denials: a 403 whose message blames a disabled API, billing or quota is not a Rules denial, so it is not a pass. */
  rulesDenial?: boolean;
}

const rand = () => randomBytes(10).toString("hex");

/** Reads only the error status and a boolean about the message; never keeps the body. */
async function probe(fetchFn: FetchFn, url: string, init: RequestInit): Promise<{ http: number | null; grpc: string; rules: boolean }> {
  let res: Response;
  try {
    res = await fetchFn(url, { ...init, signal: AbortSignal.timeout(30_000) });
  } catch (e) {
    return { http: null, grpc: `network error (${(e as { cause?: { code?: string } }).cause?.code ?? "no response"})`, rules: false };
  }
  let grpc = res.ok ? "OK" : "-";
  let rules = false;
  try {
    const body: unknown = await res.json();
    // runQuery returns an array; other calls an object. Only the error envelope is inspected.
    const err = (Array.isArray(body) ? (body[0] as { error?: unknown } | undefined)?.error : (body as { error?: unknown } | null)?.error) as
      | { status?: unknown; message?: unknown }
      | undefined;
    if (err && typeof err.status === "string" && /^[A-Z_]{3,40}$/.test(err.status)) grpc = err.status;
    if (err && typeof err.message === "string") rules = !/\b(api|disabled|not been used|billing|quota)\b/i.test(err.message);
  } catch {
    // Not JSON: leave grpc as "-".
  }
  return { http: res.status, grpc, rules };
}

export interface Runner {
  results: CheckResult[];
  expect(what: string, url: string, init: RequestInit, exp: Expectation): Promise<void>;
  skip(what: string, note: string): void;
  add(result: CheckResult): void;
}

function makeRunner(fetchFn: FetchFn, out: (line: string) => void): Runner {
  const results: CheckResult[] = [];
  const record = (r: CheckResult) => {
    results.push(r);
    out(`${r.verdict.padEnd(7)} ${r.what}  [HTTP ${r.http ?? "-"}, ${r.grpc}]${r.note ? `  ${r.note}` : ""}`);
  };
  return {
    results,
    add: record,
    async expect(what, url, init, exp) {
      const p = await probe(fetchFn, url, init);
      const ok = p.http === exp.http && (exp.grpc === undefined || p.grpc === exp.grpc) && (!exp.rulesDenial || p.rules);
      const note = !ok && exp.rulesDenial && p.http === exp.http && p.grpc === exp.grpc ? "403, but the message points at an API, billing or quota problem, not Rules" : undefined;
      record({ what, http: p.http, grpc: p.grpc, verdict: ok ? "PASS" : "FAIL", ...(note ? { note } : {}) });
      if (!ok && !note) out(`        expected HTTP ${exp.http}${exp.grpc ? ` ${exp.grpc}` : ""}`);
    },
    skip(what, note) {
      record({ what, http: null, grpc: "-", verdict: "SKIPPED", note });
    },
  };
}

const JSON_HEADERS = { "content-type": "application/json" };
const DENY: Expectation = { http: 403, grpc: "PERMISSION_DENIED", rulesDenial: true };

/** Layer A: no credentials. Everything here must be refused. */
export async function layerA(cfg: Config, run: Runner): Promise<void> {
  const docs = `${cfg.firestoreBase}/v1/projects/${cfg.projectId}/databases/(default)/documents`;
  const get = (what: string, path: string) => run.expect(`Anonymous ${what} is denied`, `${docs}/${path}`, { method: "GET" }, DENY);

  await get("list series", "series");
  await get("read campusPoints/yeager-center", "campusPoints/yeager-center");
  await get("read options/profile", "options/profile");
  for (const c of ["users", "privateDetails", "profiles", "consents", "profileAccess"]) {
    await get(`read ${c}/<random>`, `${c}/${rand()}`);
  }
  await get("list requests", "requests");
  await run.expect(
    "Anonymous notifications collection-group query is denied",
    `${docs}:runQuery`,
    {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({ structuredQuery: { from: [{ collectionId: "notifications", allDescendants: true }], limit: 1 } }),
    },
    DENY,
  );
  // currentDocument.exists=true makes a write fail with NOT_FOUND if Rules ever allowed it, so nothing can be created.
  for (const c of ["series", "privateDetails", "requests", "users"]) {
    await run.expect(
      `Anonymous write to ${c}/<random> is denied`,
      `${docs}/${c}/postdeploy-${rand()}?currentDocument.exists=true&updateMask.fieldPaths=probe`,
      { method: "PATCH", headers: JSON_HEADERS, body: JSON.stringify({ fields: { probe: { booleanValue: true } } }) },
      DENY,
    );
  }

  if (cfg.firestoreOnly) {
    for (const fn of ["routePreview", "postSeries", "sendRequest", "signUp"]) run.skip(`Anonymous call to ${fn}`, "--firestore-only");
    return;
  }
  const call = (fn: string, exp: Expectation, what: string) =>
    run.expect(what, cfg.functionUrl(fn), { method: "POST", headers: JSON_HEADERS, body: JSON.stringify({ data: {} }) }, exp);
  for (const fn of ["routePreview", "postSeries", "sendRequest"]) {
    await call(fn, { http: 401, grpc: "UNAUTHENTICATED" }, `Anonymous call to ${fn} is refused`);
  }
  // signUp validates the input before createUser (functions/src/signUp.ts), so an empty body creates no account.
  await call("signUp", { http: 400, grpc: "INVALID_ARGUMENT" }, "signUp with an empty body is rejected (no account made)");
}

/** Layer B: signed in as an app-created student. Read-only; bad input is rejected before any write or Routes call. */
export async function layerB(cfg: Config, password: string, run: Runner, fetchFn: FetchFn): Promise<void> {
  let idToken = "";
  let uid = "";
  let status: number | null = null;
  let errCode = "-";
  try {
    const res = await fetchFn(`${cfg.authBase}/v1/accounts:signInWithPassword?key=${encodeURIComponent(cfg.apiKey)}`, {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({ email: cfg.email, password, returnSecureToken: true }),
      signal: AbortSignal.timeout(30_000),
    });
    status = res.status;
    const body = (await res.json()) as { idToken?: unknown; localId?: unknown; error?: { message?: unknown } };
    if (res.ok && typeof body.idToken === "string" && typeof body.localId === "string") {
      idToken = body.idToken;
      uid = body.localId;
    } else if (typeof body.error?.message === "string" && /^[A-Z_]{3,60}/.test(body.error.message)) {
      errCode = body.error.message.split(/[ :]/)[0] as string;
    }
  } catch {
    errCode = "network error";
  }
  const what = "Sign in with email and password";
  if (!idToken) {
    if (status === 403) {
      run.add({ what, http: status, grpc: errCode, verdict: "SKIPPED", note: "Web API key restricted? Layer B skipped, not failed." });
    } else {
      run.add({ what, http: status, grpc: errCode, verdict: "FAIL" });
    }
    return;
  }
  run.add({ what, http: status, grpc: "OK", verdict: "PASS" });

  const auth = { authorization: `Bearer ${idToken}` };
  const docs = `${cfg.firestoreBase}/v1/projects/${cfg.projectId}/databases/(default)/documents`;
  const ok: Expectation = { http: 200 };
  await run.expect("Signed-in student reads own users/<uid>", `${docs}/users/${uid}`, { headers: auth }, ok);
  await run.expect("Signed-in student cannot read privateDetails/<other>", `${docs}/privateDetails/${rand()}`, { headers: auth }, DENY);
  await run.expect("Signed-in student cannot read own consents/<uid>", `${docs}/consents/${uid}`, { headers: auth }, DENY);
  await run.expect("Signed-in student lists series", `${docs}/series?pageSize=1`, { headers: auth }, ok);
  await run.expect("Signed-in student reads campusPoints/yeager-center", `${docs}/campusPoints/yeager-center`, { headers: auth }, ok);

  if (cfg.firestoreOnly) {
    for (const fn of ["routePreview", "postSeries", "sendRequest"]) run.skip(`Signed-in call to ${fn} with bad input`, "--firestore-only");
    return;
  }
  // The bad id fails the id-pattern check before any Routes call or write (routePreview.ts, postSeries.ts, sendRequest.ts).
  const bad = (fn: string, data: Record<string, string>) =>
    run.expect(
      `Signed-in call to ${fn} with a bad id is rejected`,
      cfg.functionUrl(fn),
      { method: "POST", headers: { ...JSON_HEADERS, ...auth }, body: JSON.stringify({ data }) },
      { http: 400, grpc: "INVALID_ARGUMENT" },
    );
  await bad("routePreview", { draftId: "!" });
  await bad("postSeries", { draftId: "!" });
  await bad("sendRequest", { seriesId: "!" });
}

export interface CliDeps {
  env: Record<string, string | undefined>;
  readPassword: () => Promise<string>;
  out: (line: string) => void;
  err: (line: string) => void;
  fetch?: FetchFn;
}

/** Returns the exit code: 0 no FAIL, 1 at least one FAIL, 2 refused or bad usage (nothing contacted). */
export async function main(argv: readonly string[], deps: CliDeps): Promise<number> {
  let cfg: Config;
  let password = "";
  try {
    cfg = resolveConfig(parseArgs(argv), deps.env);
    if (cfg.signedIn) {
      password = await deps.readPassword();
      if (!password) throw new ScriptError("--signed-in needs the password on stdin (one line).");
    }
  } catch (e) {
    deps.err(`Error: ${(e as Error).message}`);
    return 2;
  }
  const fetchFn: FetchFn = deps.fetch ?? ((url, init) => fetch(url, init));
  deps.out(`Post-deploy check against ${cfg.projectId} (${cfg.emulator ? "emulator" : "real project"}). Read-only; nothing is written.`);
  const run = makeRunner(fetchFn, deps.out);
  deps.out("Layer A: no credentials");
  await layerA(cfg, run);
  if (cfg.signedIn) {
    deps.out("Layer B: signed in");
    await layerB(cfg, password, run, fetchFn);
  }
  const fails = run.results.filter((r) => r.verdict === "FAIL").length;
  const skipped = run.results.filter((r) => r.verdict === "SKIPPED").length;
  deps.out(`${run.results.length - fails - skipped} passed, ${fails} failed, ${skipped} skipped.`);
  return fails > 0 ? 1 : 0;
}

async function readFirstLineOfStdin(): Promise<string> {
  let data = "";
  for await (const chunk of process.stdin) {
    data += String(chunk);
    if (data.includes("\n")) break;
  }
  return (data.split("\n")[0] ?? "").replace(/\r$/, "");
}

if (import.meta.main) {
  process.exit(
    await main(process.argv.slice(2), {
      env: process.env,
      readPassword: readFirstLineOfStdin,
      out: (l) => console.log(l),
      err: (l) => console.error(l),
    }),
  );
}
