// Developer-run script: creates an institution Auth account and sets the institution claim.
// Lives outside src/ so it is never part of the deployed Functions codebase. Never callable from the app.
// Institution accounts get no consents/users/profiles/privateDetails docs (RC-06, RC-07).
// Usage and production steps: DEVELOPMENT.md "Institution accounts". The password comes from stdin only.
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";

/** Rules treat any `institution` claim as institution (ARCHITECTURE §2); this script sets `true`. */
export const INSTITUTION_CLAIM_KEY = "institution";
export const EMULATOR_PROJECT_ID = "demo-colpool";
export const EMAIL_DOMAIN = "@calbaptist.edu";
export const MIN_PASSWORD_LENGTH = 8;

export class ScriptError extends Error {}

export interface CliArgs {
  email: string;
  displayName?: string;
  project?: string;
  /** Production only: must equal --project (typed twice on purpose). */
  confirmProject?: string;
}

export type Target = { mode: "emulator"; projectId: string } | { mode: "production"; projectId: string };

export function normalizeEmail(raw: string): string {
  const email = raw.trim().toLowerCase();
  const at = email.indexOf("@");
  if (at < 1 || email.slice(at) !== EMAIL_DOMAIN || email.includes(" ")) {
    throw new ScriptError(`Email must be a ${EMAIL_DOMAIN} address.`);
  }
  return email;
}

export function validatePassword(password: string): void {
  if (password.length < MIN_PASSWORD_LENGTH) {
    throw new ScriptError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
  }
}

export function parseArgs(argv: readonly string[]): CliArgs {
  const out: Partial<CliArgs> = {};
  for (let i = 0; i < argv.length; i++) {
    const flag = argv[i] as string;
    if (flag === "--email" || flag === "--display-name" || flag === "--project" || flag === "--confirm-project") {
      const value = argv[++i];
      if (value === undefined || value.startsWith("--")) throw new ScriptError(`${flag} needs a value.`);
      if (flag === "--email") out.email = value;
      else if (flag === "--display-name") out.displayName = value;
      else if (flag === "--project") out.project = value;
      else out.confirmProject = value;
    } else {
      // Includes --password: the password is read from stdin only.
      throw new ScriptError(`Unknown argument: ${flag.startsWith("--password") ? "--password (use stdin)" : flag}`);
    }
  }
  if (out.email === undefined) throw new ScriptError("--email <addr> is required.");
  return out as CliArgs;
}

/** Pure guard: decides which project the script may touch. Never reads or prints a key. */
export function resolveTarget(args: Pick<CliArgs, "project" | "confirmProject">, env: Record<string, string | undefined>): Target {
  if (env["FIREBASE_AUTH_EMULATOR_HOST"]) {
    if (args.project !== undefined && args.project !== EMULATOR_PROJECT_ID) {
      throw new ScriptError(`Emulator mode always uses project ${EMULATOR_PROJECT_ID}; drop --project or unset FIREBASE_AUTH_EMULATOR_HOST.`);
    }
    return { mode: "emulator", projectId: EMULATOR_PROJECT_ID };
  }
  if (!args.project) {
    throw new ScriptError("Refusing to run: set FIREBASE_AUTH_EMULATOR_HOST for the emulator, or pass --project <id> for production.");
  }
  if (args.project.startsWith("demo-")) {
    throw new ScriptError("Refusing to run: a demo- project is emulator-only. Set FIREBASE_AUTH_EMULATOR_HOST.");
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

export interface AccountInput {
  email: string;
  password: string;
  displayName?: string;
}

/** Creates the Auth user and sets the claim. Refuses an existing email; rolls back if the claim fails. */
export async function createInstitutionAccount(input: AccountInput, auth: Auth): Promise<{ uid: string; email: string }> {
  const email = normalizeEmail(input.email);
  validatePassword(input.password);

  let exists = true;
  try {
    await auth.getUserByEmail(email);
  } catch (e) {
    if ((e as { code?: string }).code === "auth/user-not-found") exists = false;
    else throw e;
  }
  if (exists) throw new ScriptError(`An account with ${email} already exists. Refusing to convert an existing account.`);

  const user = await auth.createUser({
    email,
    password: input.password,
    ...(input.displayName ? { displayName: input.displayName } : {}),
  });
  try {
    await auth.setCustomUserClaims(user.uid, { [INSTITUTION_CLAIM_KEY]: true });
  } catch (e) {
    await auth.deleteUser(user.uid);
    throw new ScriptError(`Setting the institution claim failed; the new user was deleted. (${(e as Error).message})`);
  }
  return { uid: user.uid, email };
}

export interface CliDeps {
  env: Record<string, string | undefined>;
  readPassword: () => Promise<string>;
  out: (line: string) => void;
  err: (line: string) => void;
  /** Test hook; defaults to an Admin SDK Auth for the resolved target. */
  auth?: Auth;
}

/** Returns the process exit code. */
export async function main(argv: readonly string[], deps: CliDeps): Promise<number> {
  try {
    const args = parseArgs(argv);
    const target = resolveTarget(args, deps.env);
    const email = normalizeEmail(args.email);
    deps.out(`Target project: ${target.projectId} (${target.mode}).`);
    const password = await deps.readPassword();
    validatePassword(password);
    const auth =
      deps.auth ??
      getAuth(
        initializeApp(
          target.mode === "production"
            ? { projectId: target.projectId, credential: applicationDefault() }
            : { projectId: target.projectId },
          `create-institution-account-${Date.now()}`,
        ),
      );
    const { uid } = await createInstitutionAccount(
      { email, password, ...(args.displayName ? { displayName: args.displayName } : {}) },
      auth,
    );
    deps.out(`Created ${email} in ${target.projectId} (${target.mode}).`);
    deps.out(`uid: ${uid}`);
    deps.out("institution claim set");
    return 0;
  } catch (e) {
    deps.err(`Error: ${e instanceof ScriptError ? e.message : (e as Error).message}`);
    return 1;
  }
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
  const code = await main(process.argv.slice(2), {
    env: process.env,
    readPassword: readFirstLineOfStdin,
    out: (l) => console.log(l),
    err: (l) => console.error(l),
  });
  process.exit(code);
}
