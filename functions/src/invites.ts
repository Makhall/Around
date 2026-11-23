import "./admin";

import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";

import {
  INVITE_CODE_MAX_USES,
  INVITE_CODE_TTL_MS,
} from "./constants";
import { generateInviteCode, hashInviteCode } from "./util";

export interface IssuedInvite {
  code: string;
  expiresAt: number;
}

export async function issueInviteCode(
  db: Firestore,
  circleId: string,
  createdBy: string,
): Promise<IssuedInvite> {
  const code = generateInviteCode();
  const expiresAt = Timestamp.fromMillis(Date.now() + INVITE_CODE_TTL_MS);

  await db.collection("inviteCodes").doc(hashInviteCode(code)).set({
    circleId,
    createdBy,
    createdAt: FieldValue.serverTimestamp(),
    expiresAt,
    maxUses: INVITE_CODE_MAX_USES,
    uses: 0,
    revoked: false,
  });

  return { code, expiresAt: expiresAt.toMillis() };
}

export async function revokeInviteCodes(
  db: Firestore,
  circleId: string,
): Promise<void> {
  const existing = await db
    .collection("inviteCodes")
    .where("circleId", "==", circleId)
    .get();

  if (existing.empty) {
    return;
  }

  const batch = db.batch();
  existing.docs.forEach((doc) => {
    if (doc.get("revoked") !== true) {
      batch.update(doc.ref, { revoked: true });
    }
  });
  await batch.commit();
}
