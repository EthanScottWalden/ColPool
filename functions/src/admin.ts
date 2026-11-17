import { getApps, initializeApp, type App } from "firebase-admin/app";

/** The default Admin app, created on first use. */
export function adminApp(): App {
  return getApps().find((a) => a.name === "[DEFAULT]") ?? initializeApp();
}
