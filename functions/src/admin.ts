import { getApps, initializeApp } from "firebase-admin/app";

// Cloud Functions normally initializes the default app, but the Functions
// emulator with firebase-admin v14 does not. Make it explicit and idempotent.
if (getApps().length === 0) {
  initializeApp();
}
