import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";

const emulatorMode = Boolean(
  process.env.FIRESTORE_EMULATOR_HOST ||
    process.env.FIREBASE_AUTH_EMULATOR_HOST,
);

function readServiceAccount(): Record<string, unknown> | null {
  const base64 = process.env.FIREBASE_SERVICE_ACCOUNT_BASE64;
  if (base64) {
    try {
      return JSON.parse(Buffer.from(base64, "base64").toString("utf8"));
    } catch {
      throw new Error("FIREBASE_SERVICE_ACCOUNT_BASE64 is not valid base64 JSON");
    }
  }

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (raw) {
    try {
      return JSON.parse(raw);
    } catch {
      throw new Error("FIREBASE_SERVICE_ACCOUNT is not valid JSON");
    }
  }

  return null;
}

const serviceAccount = readServiceAccount();
const projectId =
  process.env.FIREBASE_PROJECT_ID ??
  (typeof serviceAccount?.project_id === "string"
    ? serviceAccount.project_id
    : "around-847b3");

const databaseEmulatorHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
const databaseUrl = databaseEmulatorHost
  ? `http://${databaseEmulatorHost}?ns=${projectId}`
  : (process.env.FIREBASE_DATABASE_URL ??
    `https://${projectId}-default-rtdb.asia-southeast1.firebasedatabase.app`);

if (getApps().length === 0) {
  if (emulatorMode) {
    initializeApp({ projectId, databaseURL: databaseUrl });
  } else if (serviceAccount) {
    initializeApp({
      credential: cert(serviceAccount),
      projectId,
      databaseURL: databaseUrl,
    });
  } else {
    throw new Error(
      "Missing FIREBASE_SERVICE_ACCOUNT_BASE64 or FIREBASE_SERVICE_ACCOUNT. " +
        "Set one in Vercel environment variables.",
    );
  }
}

export const auth = getAuth();
export const db = getFirestore();
export const rtdb = getDatabase();
