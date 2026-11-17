// One command for local development: builds Functions, starts Auth + Firestore + Functions emulators,
// waits for the ports, seeds reference data and the demo accounts, prints the account table, then keeps
// the emulators in the foreground. Ctrl+C (or Stop in the IDE) shuts them down. Data is not persisted.
// No shell is used. Emulator-only: the seed scripts refuse anything else.
import { spawn, spawnSync } from "node:child_process";
import { connect } from "node:net";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const functionsDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const PROJECT = "demo-colpool";
const PORTS = [9099, 8080, 5001];
const WAIT_MS = 180_000;
const bin = (name) => join(functionsDir, "node_modules", ".bin", name);

// Same build as the `preemulators` hook.
const build = spawnSync(bin("tsc"), ["-p", "tsconfig.json"], { cwd: functionsDir, stdio: "inherit", shell: false });
if (build.status !== 0) process.exit(build.status ?? 1);

const emulators = spawn(
  process.execPath,
  [join(functionsDir, "scripts", "emulator-env.mjs"), "emulators:start", "--only", "auth,firestore,functions", "--project", PROJECT],
  { cwd: functionsDir, stdio: "inherit", shell: false },
);
let stopping = false;
let failed = false;
for (const sig of ["SIGINT", "SIGTERM", "SIGHUP"]) {
  process.on(sig, () => {
    stopping = true;
    emulators.kill(sig === "SIGHUP" ? "SIGTERM" : sig);
  });
}
emulators.on("exit", (code, signal) => process.exit(failed ? 1 : (code ?? (signal ? (stopping ? 0 : 1) : 0))));

const portOpen = (port) =>
  new Promise((ok) => {
    const s = connect({ port, host: "127.0.0.1" });
    s.once("connect", () => (s.destroy(), ok(true)));
    s.once("error", () => (s.destroy(), ok(false)));
  });

async function waitForPorts() {
  const deadline = Date.now() + WAIT_MS;
  while (Date.now() < deadline) {
    if ((await Promise.all(PORTS.map(portOpen))).every(Boolean)) return;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Emulators did not answer on ports ${PORTS.join(", ")} within ${WAIT_MS / 1000}s.`);
}

function runSeed(script) {
  return new Promise((ok) => {
    const child = spawn(process.execPath, [join(functionsDir, "scripts", script)], {
      cwd: functionsDir,
      stdio: "inherit",
      shell: false,
      env: {
        ...process.env,
        FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099",
        FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080",
        GCLOUD_PROJECT: PROJECT,
      },
    });
    child.on("exit", (code) => ok(code === 0));
  });
}

try {
  await waitForPorts();
  // Functions are loaded a moment after the port opens; retry the account seed once if signUp isn't ready.
  if (!(await runSeed("seed-reference-data.ts"))) throw new Error("seed-reference-data failed.");
  let seeded = await runSeed("seed-dev-accounts.ts");
  for (let i = 0; !seeded && i < 5 && !stopping; i++) {
    await new Promise((r) => setTimeout(r, 3000));
    seeded = await runSeed("seed-dev-accounts.ts");
  }
  if (!seeded) throw new Error("seed-dev-accounts failed.");
  console.log("\nEmulators are ready and seeded. Press Ctrl+C (or Stop) to shut down.");
} catch (e) {
  console.error(`\n${e.message}`);
  failed = true;
  stopping = true;
  emulators.kill("SIGTERM");
  setTimeout(() => process.exit(1), 10_000).unref();
}
