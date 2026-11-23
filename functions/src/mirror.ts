import "./admin";

import { getDatabase } from "firebase-admin/database";
import { logger } from "firebase-functions";

export async function mirrorMember(
  circleId: string,
  uid: string,
  role: "owner" | "member",
): Promise<void> {
  try {
    await getDatabase()
      .ref(`circleMembers/${circleId}/${uid}`)
      .set({ role, joinedAt: Date.now() });
  } catch (error) {
    logger.error("Failed to mirror membership to RTDB", {
      circleId,
      uid,
      error,
    });
  }
}

/**
 * Removes all RTDB data for a member. Called before Firestore membership removal
 * so a failed cleanup never leaves stale RTDB access behind.
 */
export async function deleteMemberRealtimeData(
  circleId: string,
  uid: string,
): Promise<void> {
  const updates: Record<string, null> = {
    [`circleMembers/${circleId}/${uid}`]: null,
    [`locations/${circleId}/${uid}`]: null,
    [`history/${circleId}/${uid}`]: null,
  };

  try {
    await getDatabase().ref().update(updates);
  } catch (error) {
    logger.error("Failed to delete RTDB member data", {
      circleId,
      uid,
      error,
    });
    throw error;
  }
}
