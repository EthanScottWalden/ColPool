import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";

// Fake test accounts only. Never real students.
const DOMAIN = "calbaptist.edu";

// The institution claim is `institution: true` (owner decision 2026-11-16, ARCHITECTURE §2).
// #15's developer script must set this key; firestore.rules isInstitution() checks it.
export const INSTITUTION_CLAIM_KEY = "institution";

export interface Persona {
  name: string;
  /** Null for the unauthenticated persona. */
  uid: string | null;
  email: string | null;
  claims: Record<string, unknown>;
}

export function authedStudent(): Persona {
  // Consent = existence of consents/{uid} (ADR 0002 §1); seeded by seedConsents() in the rules env (#18).
  return { name: "authedStudent", uid: "student-consented", email: `test.student1@${DOMAIN}`, claims: {} };
}

export function studentWithoutConsent(): Persona {
  return { name: "studentWithoutConsent", uid: "student-no-consent", email: `test.student2@${DOMAIN}`, claims: {} };
}

/** A consented student who hosts a series with an active posting (RC-01 case 1). */
export function hostStudent(): Persona {
  return { name: "hostStudent", uid: "student-host", email: `test.host@${DOMAIN}`, claims: {} };
}

/** A consented student who requested the host's series (RC-01 cases 2 and 3). */
export function requesterStudent(): Persona {
  return { name: "requesterStudent", uid: "student-requester", email: `test.requester@${DOMAIN}`, claims: {} };
}

/** A consented student accepted on the same weekday as the requester (RC-01 case 4). */
export function coRiderStudent(): Persona {
  return { name: "coRiderStudent", uid: "student-corider", email: `test.corider@${DOMAIN}`, claims: {} };
}

/** A consented student with no relationship to anyone else. */
export function unrelatedStudent(): Persona {
  return { name: "unrelatedStudent", uid: "student-unrelated", email: `test.unrelated@${DOMAIN}`, claims: {} };
}

/** Every persona that has a consents/{uid} doc in the rules env. */
export const consentedStudents = (): Persona[] => [
  authedStudent(),
  hostStudent(),
  requesterStudent(),
  coRiderStudent(),
  unrelatedStudent(),
];

/**
 * Writes consents/{uid} (rules disabled) for every consented student, plus any extra personas
 * (for example an institution account, to test defense in depth).
 */
export async function seedConsents(env: RulesTestEnvironment, extra: Persona[] = []): Promise<void> {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const p of [...consentedStudents(), ...extra]) {
      await setDoc(doc(db, "consents", p.uid as string), { agreedAt: new Date("2026-09-01T00:00:00Z") });
    }
  });
}

export function institutionUser(): Persona {
  return {
    name: "institutionUser",
    uid: "institution-user",
    email: `test.staff@${DOMAIN}`,
    claims: { [INSTITUTION_CLAIM_KEY]: true },
  };
}

/** Institution account whose claim value is the string "true" (rules fail closed: any value counts). */
export function institutionStringClaimUser(): Persona {
  return {
    name: "institutionStringClaimUser",
    uid: "institution-string-claim",
    email: `test.staff2@${DOMAIN}`,
    claims: { [INSTITUTION_CLAIM_KEY]: "true" },
  };
}

export function unauthenticated(): Persona {
  return { name: "unauthenticated", uid: null, email: null, claims: {} };
}

export const allPersonas = (): Persona[] => [
  authedStudent(),
  studentWithoutConsent(),
  institutionUser(),
  unauthenticated(),
];

/** Rules-test context (Firestore) for a persona. */
export function rulesContext(env: RulesTestEnvironment, p: Persona) {
  if (p.uid === null) return env.unauthenticatedContext();
  return env.authenticatedContext(p.uid, { ...p.claims, email: p.email ?? undefined });
}

/**
 * Creates the persona in the Auth emulator and returns an ID token for callable tests.
 * Uses the emulator REST API (host from FIREBASE_AUTH_EMULATOR_HOST). Null persona -> null.
 * Custom claims are set via the emulator's admin endpoint.
 */
export async function authEmulatorIdToken(p: Persona): Promise<string | null> {
  if (p.uid === null || p.email === null) return null;
  const host = process.env["FIREBASE_AUTH_EMULATOR_HOST"] ?? "127.0.0.1:9099";
  const base = `http://${host}`;
  const key = "fake-api-key";
  const json = { "content-type": "application/json" };

  const signUp = await fetch(`${base}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${key}`, {
    method: "POST",
    headers: json,
    body: JSON.stringify({ email: p.email, password: "test-password-1", returnSecureToken: true }),
  });
  if (!signUp.ok) throw new Error(`Auth emulator signUp failed: ${signUp.status}`);
  const { localId } = (await signUp.json()) as { localId: string };

  if (Object.keys(p.claims).length > 0) {
    const set = await fetch(`${base}/identitytoolkit.googleapis.com/v1/projects/demo-colpool/accounts:update`, {
      method: "POST",
      headers: { ...json, authorization: "Bearer owner" },
      body: JSON.stringify({ localId, customAttributes: JSON.stringify(p.claims) }),
    });
    if (!set.ok) throw new Error(`Auth emulator claim update failed: ${set.status}`);
  }

  const signIn = await fetch(`${base}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${key}`, {
    method: "POST",
    headers: json,
    body: JSON.stringify({ email: p.email, password: "test-password-1", returnSecureToken: true }),
  });
  if (!signIn.ok) throw new Error(`Auth emulator signIn failed: ${signIn.status}`);
  return ((await signIn.json()) as { idToken: string }).idToken;
}
