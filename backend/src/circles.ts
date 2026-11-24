import { FieldValue, Timestamp } from "firebase-admin/firestore";

import {
  JOIN_ATTEMPT_WINDOW_MS,
  JOIN_MAX_FAILURES,
  MAX_CIRCLES_PER_USER,
  MAX_MEMBERS_PER_CIRCLE,
} from "./constants";
import { fail, type ApiErrorCode } from "./errors";
import { db } from "./firebase";
import { issueInviteCode, revokeInviteCodes } from "./invites";
import { deleteMemberRealtimeData, mirrorMember } from "./mirror";
import {
  hashInviteCode,
  normalizeAvatar,
  normalizeCircleName,
  normalizeInviteCode,
  requireString,
  sanitizeDisplayName,
} from "./util";

async function countUserCircles(uid: string): Promise<number> {
  const snapshot = await db
    .collection("circles")
    .where("memberUids", "array-contains", uid)
    .limit(MAX_CIRCLES_PER_USER + 1)
    .get();
  return snapshot.size;
}

async function ensureNotRateLimited(uid: string): Promise<void> {
  const snapshot = await db.doc(`joinAttempts/${uid}`).get();
  if (!snapshot.exists) {
    return;
  }

  const windowStart = snapshot.get("windowStart") as Timestamp | undefined;
  const count = (snapshot.get("count") as number | undefined) ?? 0;
  if (
    windowStart &&
    Date.now() - windowStart.toMillis() < JOIN_ATTEMPT_WINDOW_MS &&
    count >= JOIN_MAX_FAILURES
  ) {
    fail(
      429,
      "rate_limited",
      "Too many failed join attempts. Try again later.",
    );
  }
}

async function recordJoinFailure(uid: string): Promise<void> {
  const ref = db.doc(`joinAttempts/${uid}`);

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) {
      transaction.set(ref, {
        count: 1,
        windowStart: Timestamp.now(),
      });
      return;
    }

    const windowStart = snapshot.get("windowStart") as Timestamp | undefined;
    const count = (snapshot.get("count") as number | undefined) ?? 0;
    if (
      !windowStart ||
      Date.now() - windowStart.toMillis() >= JOIN_ATTEMPT_WINDOW_MS
    ) {
      transaction.set(ref, {
        count: 1,
        windowStart: Timestamp.now(),
      });
    } else {
      transaction.update(ref, { count: count + 1 });
    }
  });
}

async function failJoin(
  uid: string,
  status: number,
  code: ApiErrorCode,
  message: string,
): Promise<never> {
  await recordJoinFailure(uid);
  return fail(status, code, message);
}

async function clearJoinFailures(uid: string): Promise<void> {
  try {
    await db.doc(`joinAttempts/${uid}`).delete();
  } catch {
    // Best-effort only; the window expires on its own.
  }
}

export async function createCircle(
  uid: string,
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const name = normalizeCircleName(requireString(data, "name"));

  const profileSnapshot = await db.doc(`users/${uid}`).get();
  if (!profileSnapshot.exists) {
    fail(412, "profile_missing", "Complete your profile first");
  }

  if ((await countUserCircles(uid)) >= MAX_CIRCLES_PER_USER) {
    fail(
      409,
      "too_many_circles",
      `You can join at most ${MAX_CIRCLES_PER_USER} Circles`,
    );
  }

  const profile = profileSnapshot.data() ?? {};
  const circleRef = db.collection("circles").doc();
  const memberData = {
    displayName: sanitizeDisplayName(profile.displayName),
    avatar: normalizeAvatar(profile.avatar),
    role: "owner",
    joinedAt: FieldValue.serverTimestamp(),
    sharing: false,
    historyEnabled: true,
    placeAlerts: true,
    allowRing: true,
  };

  const batch = db.batch();
  batch.set(circleRef, {
    name,
    ownerUid: uid,
    createdAt: FieldValue.serverTimestamp(),
    memberCount: 1,
    memberUids: [uid],
  });
  batch.set(circleRef.collection("members").doc(uid), memberData);
  await batch.commit();

  await mirrorMember(circleRef.id, uid, "owner");
  const invite = await issueInviteCode(circleRef.id, uid);

  return {
    circleId: circleRef.id,
    circleName: name,
    inviteCode: invite.code,
    expiresAt: invite.expiresAt,
  };
}

export async function rotateInviteCode(
  uid: string,
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const circleId = requireString(data, "circleId");

  const circleSnapshot = await db.doc(`circles/${circleId}`).get();
  if (!circleSnapshot.exists) {
    fail(404, "circle_not_found", "Circle not found");
  }
  if (circleSnapshot.get("ownerUid") !== uid) {
    fail(403, "not_owner", "Only the owner can create invite codes");
  }

  await revokeInviteCodes(circleId);
  const invite = await issueInviteCode(circleId, uid);

  return {
    inviteCode: invite.code,
    expiresAt: invite.expiresAt,
  };
}

export async function joinCircle(
  uid: string,
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const code = normalizeInviteCode(requireString(data, "code"));
  if (code.length !== 8) {
    return failJoin(uid, 404, "code_not_found", "Invite code not found");
  }

  await ensureNotRateLimited(uid);

  const inviteRef = db.collection("inviteCodes").doc(hashInviteCode(code));
  const inviteSnapshot = await inviteRef.get();
  const invite = inviteSnapshot.data();
  if (!invite) {
    return failJoin(uid, 404, "code_not_found", "Invite code not found");
  }

  if (invite.revoked === true) {
    return failJoin(uid, 410, "code_revoked", "Invite code was replaced");
  }

  const expiresAt = invite.expiresAt as Timestamp | undefined;
  if (!expiresAt || expiresAt.toMillis() <= Date.now()) {
    return failJoin(uid, 410, "code_expired", "Invite code has expired");
  }

  const uses = (invite.uses as number | undefined) ?? 0;
  const maxUses = (invite.maxUses as number | undefined) ?? MAX_MEMBERS_PER_CIRCLE;
  if (uses >= maxUses) {
    return failJoin(uid, 410, "code_exhausted", "Invite code has no uses left");
  }

  const circleId = invite.circleId;
  if (typeof circleId !== "string") {
    fail(500, "internal", "Invite code is corrupt");
  }

  const circleRef = db.doc(`circles/${circleId}`);
  const circleSnapshot = await circleRef.get();
  if (!circleSnapshot.exists) {
    return failJoin(uid, 404, "circle_not_found", "Circle no longer exists");
  }

  const memberRef = circleRef.collection("members").doc(uid);
  const existingMember = await memberRef.get();
  if (existingMember.exists) {
    fail(409, "already_member", "You are already in this Circle");
  }

  if ((await countUserCircles(uid)) >= MAX_CIRCLES_PER_USER) {
    fail(
      409,
      "too_many_circles",
      `You can join at most ${MAX_CIRCLES_PER_USER} Circles`,
    );
  }

  const profileSnapshot = await db.doc(`users/${uid}`).get();
  if (!profileSnapshot.exists) {
    fail(412, "profile_missing", "Complete your profile first");
  }
  const profile = profileSnapshot.data() ?? {};

  const result = await db.runTransaction(async (transaction) => {
    const freshInviteSnapshot = await transaction.get(inviteRef);
    const freshInvite = freshInviteSnapshot.data();
    if (!freshInvite) {
      fail(404, "code_not_found", "Invite code not found");
    }
    const freshExpiresAt = freshInvite.expiresAt as Timestamp | undefined;
    if (
      freshInvite.revoked === true ||
      !freshExpiresAt ||
      freshExpiresAt.toMillis() <= Date.now()
    ) {
      fail(410, "code_expired", "Invite code is no longer valid");
    }
    const freshUses = (freshInvite.uses as number | undefined) ?? 0;
    const freshMaxUses =
      (freshInvite.maxUses as number | undefined) ?? MAX_MEMBERS_PER_CIRCLE;
    if (freshUses >= freshMaxUses) {
      fail(410, "code_exhausted", "Invite code has no uses left");
    }

    const freshCircleSnapshot = await transaction.get(circleRef);
    const freshCircle = freshCircleSnapshot.data();
    if (!freshCircle) {
      fail(404, "circle_not_found", "Circle no longer exists");
    }
    const memberUids = (freshCircle.memberUids as string[] | undefined) ?? [];
    if (memberUids.includes(uid)) {
      fail(409, "already_member", "You are already in this Circle");
    }
    if (memberUids.length >= MAX_MEMBERS_PER_CIRCLE) {
      fail(409, "circle_full", "This Circle is full");
    }

    transaction.update(inviteRef, { uses: FieldValue.increment(1) });
    transaction.update(circleRef, {
      memberUids: FieldValue.arrayUnion(uid),
      memberCount: FieldValue.increment(1),
    });
    transaction.set(memberRef, {
      displayName: sanitizeDisplayName(profile.displayName),
      avatar: normalizeAvatar(profile.avatar),
      role: "member",
      joinedAt: FieldValue.serverTimestamp(),
      sharing: false,
      historyEnabled: true,
      placeAlerts: true,
      allowRing: true,
    });

    return { circleName: freshCircle.name as string };
  });

  await mirrorMember(circleId, uid, "member");
  await clearJoinFailures(uid);

  return {
    circleId,
    circleName: result.circleName,
  };
}

export async function leaveCircle(
  uid: string,
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const circleId = requireString(data, "circleId");

  const circleRef = db.doc(`circles/${circleId}`);
  const circleSnapshot = await circleRef.get();
  if (!circleSnapshot.exists) {
    fail(404, "circle_not_found", "Circle not found");
  }
  if (circleSnapshot.get("ownerUid") === uid) {
    fail(
      409,
      "owner_cannot_leave",
      "The owner cannot leave this Circle yet",
    );
  }

  const memberRef = circleRef.collection("members").doc(uid);
  const memberSnapshot = await memberRef.get();
  if (!memberSnapshot.exists) {
    fail(404, "member_not_found", "You are not a member of this Circle");
  }

  await deleteMemberRealtimeData(circleId, uid);

  await db.runTransaction(async (transaction) => {
    const freshCircle = await transaction.get(circleRef);
    const freshMember = await transaction.get(memberRef);
    if (!freshCircle.exists || !freshMember.exists) {
      return;
    }
    transaction.delete(memberRef);
    transaction.update(circleRef, {
      memberUids: FieldValue.arrayRemove(uid),
      memberCount: FieldValue.increment(-1),
    });
  });

  return { ok: true };
}

export async function removeMember(
  uid: string,
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const circleId = requireString(data, "circleId");
  const targetUid = requireString(data, "targetUid");

  const circleRef = db.doc(`circles/${circleId}`);
  const circleSnapshot = await circleRef.get();
  if (!circleSnapshot.exists) {
    fail(404, "circle_not_found", "Circle not found");
  }
  if (circleSnapshot.get("ownerUid") !== uid) {
    fail(403, "not_owner", "Only the owner can remove members");
  }
  if (targetUid === uid) {
    fail(409, "owner_cannot_leave", "The owner cannot remove themselves");
  }

  const memberRef = circleRef.collection("members").doc(targetUid);
  const memberSnapshot = await memberRef.get();
  if (!memberSnapshot.exists) {
    fail(404, "member_not_found", "Member not found");
  }

  await deleteMemberRealtimeData(circleId, targetUid);

  await db.runTransaction(async (transaction) => {
    const freshCircle = await transaction.get(circleRef);
    const freshMember = await transaction.get(memberRef);
    if (!freshCircle.exists || !freshMember.exists) {
      return;
    }
    transaction.delete(memberRef);
    transaction.update(circleRef, {
      memberUids: FieldValue.arrayRemove(targetUid),
      memberCount: FieldValue.increment(-1),
    });
  });

  return { ok: true };
}
