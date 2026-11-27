// Developer-run script: seeds campusPoints/* and options/profile. Lives outside src/ so it is never deployed (same reason as #15).
// Idempotent: each doc is written whole with set() (no merge), so running twice gives identical docs.
// Never deletes: a point that leaves the seed list is left alone; retire one by setting active: false.
// Usage and production steps: DEVELOPMENT.md "Seeding reference data".
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { CAMPUS_POINT_SEED } from "../src/shared/schema/campusPoints.ts";
import { COLLECTIONS } from "../src/shared/schema/collections.ts";
import { buildProfileOptionsDoc } from "../src/shared/schema/options.ts";

export const EMULATOR_PROJECT_ID = "demo-colpool";

export class ScriptError extends Error {}

export interface CliArgs {
  project?: string;
  /** Production only: must equal --project (typed twice on purpose). */
  confirmProject?: string;
}

export type Target = { mode: "emulator"; projectId: string } | { mode: "production"; projectId: string };

export function parseArgs(argv: readonly string[]): CliArgs {
  const out: CliArgs = {};
  for (let i = 0; i < argv.length; i++) {
    const flag = argv[i] as string;
    if (flag === "--project" || flag === "--confirm-project") {
      const value = argv[++i];
      if (value === undefined || value.startsWith("--")) throw new ScriptError(`${flag} needs a value.`);
      if (flag === "--project") out.project = value;
      else out.confirmProject = value;
    } else {
      throw new ScriptError(`Unknown argument: ${flag}`);
    }
  }
  return out;
}

/** Pure guard: decides which project the script may touch. Never reads or prints a key. */
export function resolveTarget(args: CliArgs, env: Record<string, string | undefined>): Target {
  if (env["FIRESTORE_EMULATOR_HOST"]) {
    if (args.project !== undefined && args.project !== EMULATOR_PROJECT_ID) {
      throw new ScriptError(`Emulator mode always uses project ${EMULATOR_PROJECT_ID}; drop --project or unset FIRESTORE_EMULATOR_HOST.`);
    }
    return { mode: "emulator", projectId: EMULATOR_PROJECT_ID };
  }
  if (!args.project) {
    throw new ScriptError("Refusing to run: set FIRESTORE_EMULATOR_HOST for the emulator, or pass --project <id> for production.");
  }
  if (args.project.startsWith("demo-")) {
    throw new ScriptError("Refusing to run: a demo- project is emulator-only. Set FIRESTORE_EMULATOR_HOST.");
  }
  if (args.confirmProject === undefined) {
    throw new ScriptError(`Refusing to run: production needs --confirm-project ${args.project} (the same id again).`);
  }
  if (args.confirmProject !== args.project) {
    throw new ScriptError("Refusing to run: --confirm-project does not match --project.");
  }
  // Credentials: Application Default Credentials (gcloud auth application-default login), or
  // GOOGLE_APPLICATION_CREDENTIALS when set. Nothing about them is read or printed here.
  return { mode: "production", projectId: args.project };
}

/** Writes the 5 campus points and options/profile. Returns the number of campus points written. */
export async function seedReferenceData(db: Firestore): Promise<{ campusPoints: number }> {
  const batch = db.batch();
  for (const { id, ...point } of CAMPUS_POINT_SEED) {
    batch.set(db.collection(COLLECTIONS.campusPoints).doc(id), point);
  }
  batch.set(db.collection(COLLECTIONS.options).doc("profile"), buildProfileOptionsDoc());
  await batch.commit();
  return { campusPoints: CAMPUS_POINT_SEED.length };
}

export interface CliDeps {
  env: Record<string, string | undefined>;
  out: (line: string) => void;
  err: (line: string) => void;
  /** Test hook; defaults to an Admin SDK Firestore for the resolved target. */
  db?: Firestore;
}

/** Returns the process exit code. */
export async function main(argv: readonly string[], deps: CliDeps): Promise<number> {
  try {
    const target = resolveTarget(parseArgs(argv), deps.env);
    deps.out(`Target project: ${target.projectId} (${target.mode}).`);
    const db =
      deps.db ??
      getFirestore(
        initializeApp(
          target.mode === "production"
            ? { projectId: target.projectId, credential: applicationDefault() }
            : { projectId: target.projectId },
          `seed-reference-data-${Date.now()}`,
        ),
      );
    const { campusPoints } = await seedReferenceData(db);
    deps.out(`Seeded ${campusPoints} campus points and options/profile in ${target.projectId} (${target.mode}).`);
    return 0;
  } catch (e) {
    deps.err(`Error: ${(e as Error).message}`);
    return 1;
  }
}

if (import.meta.main) {
  process.exit(await main(process.argv.slice(2), { env: process.env, out: (l) => console.log(l), err: (l) => console.error(l) }));
}
