import { HISTORY_MAX_POINTS_PER_MEMBER, HISTORY_RETENTION_MS } from "./constants";
import { db, rtdb } from "./firebase";

export async function pruneHistory(
  now: number = Date.now(),
): Promise<Record<string, unknown>> {
  const cutoff = now - HISTORY_RETENTION_MS;
  const updates: Record<string, null> = {};
  let membersScanned = 0;
  let pointsRemoved = 0;
  let pointsTrimmed = 0;

  const circles = await db.collection("circles").get();
  for (const circle of circles.docs) {
    const members = await circle.ref.collection("members").get();
    for (const member of members.docs) {
      membersScanned += 1;
      const historyRef = rtdb.ref("history").child(circle.id).child(member.id);

      const expired = await historyRef.orderByChild("t").endAt(cutoff).get();
      const expiredCount = expired.numChildren();
      expired.forEach((child) => {
        updates[`history/${circle.id}/${member.id}/${child.key}`] = null;
        pointsRemoved += 1;
      });

      const remaining = (await historyRef.orderByChild("t").get()).numChildren() -
        expiredCount;
      const toTrim = remaining - HISTORY_MAX_POINTS_PER_MEMBER;
      if (toTrim > 0) {
        const all = await historyRef.orderByChild("t").get();
        let trimmedForMember = 0;
        all.forEach((child) => {
          if (trimmedForMember >= toTrim) {
            return;
          }
          const key = `history/${circle.id}/${member.id}/${child.key}`;
          if (updates[key] === undefined) {
            updates[key] = null;
            trimmedForMember += 1;
            pointsTrimmed += 1;
          }
        });
      }
    }
  }

  if (Object.keys(updates).length > 0) {
    await rtdb.ref().update(updates);
  }

  return {
    ok: true,
    cutoff,
    retentionMs: HISTORY_RETENTION_MS,
    circlesScanned: circles.size,
    membersScanned,
    pointsRemoved,
    pointsTrimmed,
  };
}
