import { rtdb } from "./firebase";

export async function mirrorMember(
  circleId: string,
  uid: string,
  role: "owner" | "member",
): Promise<void> {
  try {
    await rtdb
      .ref(`circleMembers/${circleId}/${uid}`)
      .set({ role, joinedAt: Date.now() });
  } catch (error) {
    console.error("Failed to mirror membership to RTDB", {
      circleId,
      uid,
      error,
    });
  }
}

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
    await rtdb.ref().update(updates);
  } catch (error) {
    console.error("Failed to delete RTDB member data", {
      circleId,
      uid,
      error,
    });
    throw error;
  }
}
