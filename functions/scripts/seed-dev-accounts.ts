// Developer-run script: creates one demo account per sign-in routing case on the EMULATORS only.
// Lives outside src/ so it is never deployed. Not for production: it refuses to run unless both the
// Auth and Firestore emulator hosts are set (loopback only) and the project is a demo- project.
// Idempotent: an account whose email already exists is skipped. Usage: DEVELOPMENT.md "Run the app from Android Studio".
import { initializeApp } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";
import { FieldValue, getFirestore, type Firestore } from "firebase-admin/firestore";
import { COLLECTIONS } from "../src/shared/schema/collections.ts";
import { createInstitutionAccount, EMULATOR_PROJECT_ID } from "./create-institution-account.ts";

export { EMULATOR_PROJECT_ID };
/** Fake, demo-only password shared by every account below. */
export const DEV_PASSWORD = "demo-pass-123";

export class ScriptError extends Error {}

export type DevCase = "student-onboarded" | "student-new" | "no-consent" | "staff";

export interface DevAccount {
  email: string;
  kase: DevCase;
  covers: string;
  firstName?: string;
  lastName?: string;
}

export const DEV_ACCOUNTS: readonly DevAccount[] = [
  { email: "dev.student@calbaptist.edu", kase: "student-onboarded", covers: "Student with a starting address: goes to Search", firstName: "Dev", lastName: "Student" },
  { email: "dev.newstudent@calbaptist.edu", kase: "student-new", covers: "Student without a starting address: goes to Onboarding", firstName: "Dev", lastName: "Newcomer" },
  { email: "dev.noconsent@calbaptist.edu", kase: "no-consent", covers: "Account with no consent record: blocked (RC-06)" },
  { email: "dev.staff@calbaptist.edu", kase: "staff", covers: "Institution staff account: staff routing" },
];

/** A made-up address near campus. Shape: docs/data-model.md §2.2. */
export const DEV_STARTING_ADDRESS = {
  text: "123 Demo Street, Riverside, CA 92504, USA",
  placeId: "demo-place-id-dev-student",
  lat: 33.9296,
  lng: -117.4254,
} as const;

const LOOPBACK = new Set(["127.0.0.1", "localhost", "[::1]", "::1"]);

function hostOf(value: string): string {
  // "127.0.0.1:9099" or "[::1]:9099"; strip a trailing port.
  return value.replace(/:\d+$/, "");
}

/** Pure guard: only the emulators, only a demo- project. */
export function resolveTarget(env: Record<string, string | undefined>): { projectId: string } {
  const authHost = env["FIREBASE_AUTH_EMULATOR_HOST"];
  const fsHost = env["FIRESTORE_EMULATOR_HOST"];
  if (!authHost || !fsHost) {
    throw new ScriptError("Refusing to run: set both FIREBASE_AUTH_EMULATOR_HOST and FIRESTORE_EMULATOR_HOST. This script is for the emulators only.");
  }
  for (const h of [authHost, fsHost]) {
    if (!LOOPBACK.has(hostOf(h))) throw new ScriptError("Refusing to run: the emulator hosts must be on this machine (127.0.0.1 or localhost).");
  }
  const projectId = env["GCLOUD_PROJECT"] ?? EMULATOR_PROJECT_ID;
  if (!projectId.startsWith("demo-")) {
    throw new ScriptError("Refusing to run: the project must be a demo- project.");
  }
  return { projectId };
}

export type SignUpCall = (input: { email: string; password: string; firstName: string; lastName: string }) => Promise<string>;

export interface SeedResult {
  email: string;
  kase: DevCase;
  covers: string;
  status: "created" | "exists";
}

async function userExists(auth: Auth, email: string): Promise<boolean> {
  try {
    await auth.getUserByEmail(email);
    return true;
  } catch (e) {
    if ((e as { code?: string }).code === "auth/user-not-found") return false;
    throw e;
  }
}

/** Starting address for the onboarded student. Only written if missing, so a re-run changes nothing. */
async function ensureStartingAddress(db: Firestore, uid: string): Promise<void> {
  const ref = db.collection(COLLECTIONS.privateDetails).doc(uid);
  if ((await ref.get()).exists) return;
  await ref.set({ startingAddress: { ...DEV_STARTING_ADDRESS }, updatedAt: FieldValue.serverTimestamp() });
}

/** Creates every dev account that does not exist yet. `signUp` is the real callable (injected for tests). */
export async function seedDevAccounts(deps: { auth: Auth; db: Firestore; signUp: SignUpCall }): Promise<SeedResult[]> {
  const results: SeedResult[] = [];
  for (const a of DEV_ACCOUNTS) {
    const exists = await userExists(deps.auth, a.email);
    if (a.kase === "student-onboarded" || a.kase === "student-new") {
      let uid: string;
      if (exists) uid = (await deps.auth.getUserByEmail(a.email)).uid;
      else uid = await deps.signUp({ email: a.email, password: DEV_PASSWORD, firstName: a.firstName as string, lastName: a.lastName as string });
      if (a.kase === "student-onboarded") await ensureStartingAddress(deps.db, uid);
    } else if (!exists && a.kase === "no-consent") {
      await deps.auth.createUser({ email: a.email, password: DEV_PASSWORD });
    } else if (!exists && a.kase === "staff") {
      await createInstitutionAccount({ email: a.email, password: DEV_PASSWORD, displayName: "Dev Staff" }, deps.auth);
    }
    results.push({ email: a.email, kase: a.kase, covers: a.covers, status: exists ? "exists" : "created" });
  }
  return results;
}

/** Calls the real signUp callable on the Functions emulator. Returns the new uid. */
export function callableSignUp(projectId: string, host: string): SignUpCall {
  return async (input) => {
    const res = await fetch(`http://${host}/${projectId}/us-west2/signUp`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ data: { ...input, consent: true } }),
    });
    const body = (await res.json().catch(() => ({}))) as { result?: { uid?: string }; error?: { message?: string } };
    if (!res.ok || !body.result?.uid) {
      throw new ScriptError(`signUp failed (HTTP ${res.status}): ${body.error?.message ?? "no uid returned"}. Are the Functions emulator and its build running?`);
    }
    return body.result.uid;
  };
}

export function formatTable(results: readonly SeedResult[]): string[] {
  const w = Math.max(...results.map((r) => r.email.length), 5);
  const lines = [`${"Email".padEnd(w)}  Status   Covers`];
  for (const r of results) lines.push(`${r.email.padEnd(w)}  ${r.status.padEnd(7)}  ${r.covers}`);
  lines.push("", `Password for every account (fake, demo-only): ${DEV_PASSWORD}`);
  return lines;
}

export interface CliDeps {
  env: Record<string, string | undefined>;
  out: (line: string) => void;
  err: (line: string) => void;
  /** Test hooks; default to the Admin SDK on the emulators and the real callable. */
  auth?: Auth;
  db?: Firestore;
  signUp?: SignUpCall;
}

/** Returns the process exit code. */
export async function main(argv: readonly string[], deps: CliDeps): Promise<number> {
  try {
    if (argv.length > 0) throw new ScriptError(`Unknown argument: ${argv[0]}`);
    const { projectId } = resolveTarget(deps.env);
    const app = deps.auth && deps.db ? undefined : initializeApp({ projectId }, `seed-dev-accounts-${Date.now()}`);
    const auth = deps.auth ?? getAuth(app);
    const db = deps.db ?? getFirestore(app!);
    const signUp = deps.signUp ?? callableSignUp(projectId, deps.env["FUNCTIONS_EMULATOR_HOST"] ?? "127.0.0.1:5001");
    const results = await seedDevAccounts({ auth, db, signUp });
    for (const l of formatTable(results)) deps.out(l);
    return 0;
  } catch (e) {
    deps.err(`Error: ${(e as Error).message}`);
    return 1;
  }
}

if (import.meta.main) {
  process.exit(await main(process.argv.slice(2), { env: process.env, out: (l) => console.log(l), err: (l) => console.error(l) }));
}
