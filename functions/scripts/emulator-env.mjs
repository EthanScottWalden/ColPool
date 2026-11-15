// No-shell launcher for the Firebase CLI. Keeps emulator jars, npm and CLI config
// inside the repo (.cache/, git-ignored) so the agent sandbox and CI behave the same.
import { spawn } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const functionsDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const cache = join(functionsDir, "..", ".cache");

process.env.FIREBASE_EMULATORS_PATH ??= join(cache, "firebase", "emulators");
process.env.XDG_CONFIG_HOME ??= join(cache, "xdg-config");
process.env.NO_UPDATE_NOTIFIER ??= "1";

const child = spawn(
  join(functionsDir, "node_modules", ".bin", "firebase"),
  process.argv.slice(2),
  { cwd: join(functionsDir, ".."), stdio: "inherit", shell: false },
);
for (const sig of ["SIGINT", "SIGTERM"]) process.on(sig, () => child.kill(sig));
child.on("exit", (code, signal) => process.exit(code ?? (signal ? 1 : 0)));
