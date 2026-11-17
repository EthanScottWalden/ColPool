import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { buildProfileOptionsDoc } from "../../src/shared/schema/index.ts";
import {
  authedStudent,
  consentedStudents,
  hostStudent,
  institutionUser,
  seedConsents,
  studentWithoutConsent,
  type Persona,
} from "./personas.ts";

export const ADDRESS = { text: "123 Test St, Riverside, CA", placeId: "test-place-1", lat: 33.93, lng: -117.4 };

/** Seeds consents, options, one campus point, and a users/profiles/schedules doc per account (fake data). */
export async function seedBase(env: RulesTestEnvironment, extraConsent: Persona[] = []): Promise<void> {
  await seedConsents(env, extraConsent);
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "options", "profile"), buildProfileOptionsDoc());
    await setDoc(doc(db, "campusPoints", "cp1"), { name: "Test Lot", lat: 33.9, lng: -117.4, order: 1, active: true });
    const everyone = [...consentedStudents(), institutionUser(), studentWithoutConsent()];
    for (const p of everyone) {
      const uid = p.uid as string;
      await setDoc(doc(db, "users", uid), { email: p.email, createdAt: new Date("2026-09-01T00:00:00Z") });
      await setDoc(doc(db, "profiles", uid), {
        firstName: "Test",
        lastName: p.name,
        updatedAt: new Date("2026-09-01T00:00:00Z"),
      });
      await setDoc(doc(db, "schedules", uid), { entries: {} });
    }
    await setDoc(doc(db, "privateDetails", authedStudent().uid as string), {
      startingAddress: ADDRESS,
      updatedAt: new Date("2026-09-01T00:00:00Z"),
    });
  });
}

/** Writes profileAccess/{ownerId} (rules disabled). */
export async function seedAccess(env: RulesTestEnvironment, ownerId: string, data: Record<string, unknown>): Promise<void> {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "profileAccess", ownerId), data);
  });
}

export const HOST_UID = hostStudent().uid as string;
