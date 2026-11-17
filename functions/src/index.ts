import { setGlobalOptions } from "firebase-functions/v2";
import { ping as pingFunction } from "./ping.js";

setGlobalOptions({ region: "us-west2" });

// Emulator-only: see ping.ts.
export const ping = process.env["FUNCTIONS_EMULATOR"] === "true" ? pingFunction : undefined;

export { signUp } from "./signUp.js";
