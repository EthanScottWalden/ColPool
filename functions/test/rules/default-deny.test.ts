import { after, before, describe, it } from "node:test";
import { assertFails } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs, setLogLevel } from "firebase/firestore";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import { allPersonas, rulesContext, seedConsents } from "../helpers/personas.ts";

setLogLevel("silent"); // denied calls log expected PERMISSION_DENIED noise

const paths = [
  "anything/doc1",
  "seriesDrafts/d1",
  "series/s1/requests/r1",
  "users/student-consented/consent/current",
  "users/student-consented/anything/doc1",
  "profiles/student-consented/sub/doc1",
  "campusPoints/cp1/sub/doc1",
  "options/other",
  "series/s1/roster/student-consented",
  "consent/student-consented",
  "notifications/n1",
];

describe("firestore.rules default deny", () => {
  let env: RulesTestEnvironment;
  before(async () => {
    env = await createRulesEnv();
    await seedConsents(env);
  });
  after(async () => {
    await env.cleanup();
  });

  for (const persona of allPersonas()) {
    describe(persona.name, () => {
      for (const path of paths) {
        it(`denies read and write on ${path}`, async () => {
          const db = rulesContext(env, persona).firestore();
          const ref = doc(db, path);
          await assertFails(getDoc(ref));
          await assertFails(setDoc(ref, { x: 1 }));
          await assertFails(deleteDoc(ref));
        });
      }
      it("denies listing a collection", async () => {
        const db = rulesContext(env, persona).firestore();
        await assertFails(getDocs(collection(db, "seriesDrafts")));
      });
    });
  }
});
