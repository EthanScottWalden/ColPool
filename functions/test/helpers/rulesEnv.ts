import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";

export const PROJECT_ID = "demo-colpool";

const rulesPath = fileURLToPath(new URL("../../../firestore.rules", import.meta.url));

/** Starts a rules test environment against the Firestore emulator (host from env). */
export async function createRulesEnv(): Promise<RulesTestEnvironment> {
  return initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync(rulesPath, "utf8") },
  });
}
