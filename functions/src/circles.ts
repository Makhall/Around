import "./admin";

import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";

import {
  JOIN_ATTEMPT_WINDOW_MS,
  JOIN_MAX_FAILURES,
  MAX_CIRCLES_PER_USER,
  MAX_MEMBERS_PER_CIRCLE,
} from "./constants";
import { fail, type Reason } from "./errors";
import { issueInviteCode, revokeInviteCodes } from "./invites";
import { deleteMemberRealtimeData, mirrorMember } from "./mirror";
import {
  hashInviteCode,
  normalizeAvatar,
  normalizeCircleName,
  normalizeInviteCode,
  requireAuth,
  requireData,
  requireString,
  sanitizeDisplayName,
} from "./util";

type FailCode = Parameters<typeof fail>[0];

async function countUserCircles(uid: string): Promise<number> {
  const snapshot = await getFirestore()
    .collection("circles")
    .where("memberUids", "array-contains", uid)
    .limit(MAX_CIRCLES_PER_USER + 1)
    .get();
  return snapshot.size;
}

async function ensureNotRateLimited(uid: string): Promise<void> {
  const snapshot = await getFirestore()
    .doc(`joinAttempts/${uid}`)
    .get();
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
      "resource-exhausted",
      "Too many failed join attempts. Try again later.",
      "rate_limited",
    );
  }
}

async function recordJoinFailure(uid: string): Promise<void> {
  const db = getFirestore();
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
  code: FailCode,
  message: string,
  reason: Reason,
): Promise<never> {
  await recordJoinFailure(uid);
  return fail(code, message, reason);
}

async function clearJoinFailures(uid: string): Promise<void> {
  try {
    await getFirestore().doc(`joinAttempts/${uid}`).delete();
  } catch {
    // Best-effort only; the window expires on its own.
  }
}

export const createCircle = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = requireData(request);
  const name = normalizeCircleName(requireString(data, "name"));
  const db = getFirestore();

  const profileSnapshot = await db.doc(`users/${uid}`).get();
  if (!profileSnapshot.exists) {
    fail("failed-precondition", "Complete your profile first", "profile_missing");
  }

  if ((await countUserCircles(uid)) >= MAX_CIRCLES_PER_USER) {
    fail(
      "resource-exhausted",
      `You can join at most ${MAX_CIRCLES_PER_USER} Circles`,
      "too_many_circles",
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
  const invite = await issueInviteCode(db, circleRef.id, uid);

  return {
    circleId: circleRef.id,
    circleName: name,
    inviteCode: invite.code,
    expiresAt: invite.expiresAt,
  };
});

export const rotateInviteCode = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = requireData(request);
  const circleId = requireString(data, "circleId");
  const db = getFirestore();

  const circleSnapshot = await db.doc(`circles/${circleId}`).get();
  if (!circleSnapshot.exists) {
    fail("not-found", "Circle not found", "circle_not_found");
  }
  if (circleSnapshot.get("ownerUid") !== uid) {
    fail("permission-denied", "Only the owner can create invite codes", "not_owner");
  }

  await revokeInviteCodes(db, circleId);
  const invite = await issueInviteCode(db, circleId, uid);

  return {
    inviteCode: invite.code,
    expiresAt: invite.expiresAt,
  };
});

export const joinCircle = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = requireData(request);
  const code = normalizeInviteCode(requireString(data, "code"));
  if (code.length !== 8) {
    return failJoin(uid, "not-found", "Invite code not found", "code_not_found");
  }

  await ensureNotRateLimited(uid);

  const db = getFirestore();
  const inviteRef = db.collection("inviteCodes").doc(hashInviteCode(code));
  const inviteSnapshot = await inviteRef.get();
  const invite = inviteSnapshot.data();
  if (!invite) {
    return failJoin(uid, "not-found", "Invite code not found", "code_not_found");
  }

  if (invite.revoked === true) {
    return failJoin(uid, "failed-precondition", "Invite code was revoked", "code_revoked");
  }

  const expiresAt = invite.expiresAt as Timestamp | undefined;
  if (!expiresAt || expiresAt.toMillis() <= Date.now()) {
    return failJoin(uid, "failed-precondition", "Invite code has expired", "code_expired");
  }

  const uses = (invite.uses as number | undefined) ?? 0;
  const maxUses = (invite.maxUses as number | undefined) ?? MAX_MEMBERS_PER_CIRCLE;
  if (uses >= maxUses) {
    return failJoin(
      uid,
      "resource-exhausted",
      "Invite code has no uses left",
      "code_exhausted",
    );
  }

  const circleId = invite.circleId;
  if (typeof circleId !== "string") {
    fail("internal", "Invite code is corrupt", "code_not_found");
  }

  const circleRef = db.doc(`circles/${circleId}`);
  const circleSnapshot = await circleRef.get();
  if (!circleSnapshot.exists) {
    return failJoin(uid, "not-found", "Circle no longer exists", "circle_not_found");
  }

  const memberRef = circleRef.collection("members").doc(uid);
  const existingMember = await memberRef.get();
  if (existingMember.exists) {
    fail("already-exists", "You are already in this Circle", "already_member");
  }

  if ((await countUserCircles(uid)) >= MAX_CIRCLES_PER_USER) {
    fail(
      "resource-exhausted",
      `You can join at most ${MAX_CIRCLES_PER_USER} Circles`,
      "too_many_circles",
    );
  }

  const profileSnapshot = await db.doc(`users/${uid}`).get();
  if (!profileSnapshot.exists) {
    fail("failed-precondition", "Complete your profile first", "profile_missing");
  }
  const profile = profileSnapshot.data() ?? {};

  const result = await db.runTransaction(async (transaction) => {
    const freshInviteSnapshot = await transaction.get(inviteRef);
    const freshInvite = freshInviteSnapshot.data();
    if (!freshInvite) {
      fail("not-found", "Invite code not found", "code_not_found");
    }
    const freshExpiresAt = freshInvite.expiresAt as Timestamp | undefined;
    if (
      freshInvite.revoked === true ||
      !freshExpiresAt ||
      freshExpiresAt.toMillis() <= Date.now()
    ) {
      fail(
        "failed-precondition",
        "Invite code is no longer valid",
        "code_expired",
      );
    }
    const freshUses = (freshInvite.uses as number | undefined) ?? 0;
    const freshMaxUses =
      (freshInvite.maxUses as number | undefined) ?? MAX_MEMBERS_PER_CIRCLE;
    if (freshUses >= freshMaxUses) {
      fail(
        "resource-exhausted",
        "Invite code has no uses left",
        "code_exhausted",
      );
    }

    const freshCircleSnapshot = await transaction.get(circleRef);
    const freshCircle = freshCircleSnapshot.data();
    if (!freshCircle) {
      fail("not-found", "Circle no longer exists", "circle_not_found");
    }
    const memberUids = (freshCircle.memberUids as string[] | undefined) ?? [];
    if (memberUids.includes(uid)) {
      fail("already-exists", "You are already in this Circle", "already_member");
    }
    if (memberUids.length >= MAX_MEMBERS_PER_CIRCLE) {
      fail("resource-exhausted", "This Circle is full", "circle_full");
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
});

export const leaveCircle = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = requireData(request);
  const circleId = requireString(data, "circleId");
  const db = getFirestore();

  const circleSnapshot = await db.doc(`circles/${circleId}`).get();
  if (!circleSnapshot.exists) {
    fail("not-found", "Circle not found", "circle_not_found");
  }
  if (circleSnapshot.get("ownerUid") === uid) {
    fail(
      "failed-precondition",
      "The owner cannot leave this Circle yet",
      "owner_cannot_leave",
    );
  }

  const memberRef = db.doc(`circles/${circleId}/members/${uid}`);
  const memberSnapshot = await memberRef.get();
  if (!memberSnapshot.exists) {
    fail("not-found", "You are not a member of this Circle", "member_not_found");
  }

  await deleteMemberRealtimeData(circleId, uid);

  await db.runTransaction(async (transaction) => {
    const freshCircle = await transaction.get(db.doc(`circles/${circleId}`));
    const freshMember = await transaction.get(memberRef);
    if (!freshCircle.exists || !freshMember.exists) {
      return;
    }
    transaction.delete(memberRef);
    transaction.update(db.doc(`circles/${circleId}`), {
      memberUids: FieldValue.arrayRemove(uid),
      memberCount: FieldValue.increment(-1),
    });
  });

  return { ok: true };
});

export const removeMember = onCall(async (request) => {
  const uid = requireAuth(request);
  const data = requireData(request);
  const circleId = requireString(data, "circleId");
  const targetUid = requireString(data, "targetUid");
  const db = getFirestore();

  const circleRef = db.doc(`circles/${circleId}`);
  const circleSnapshot = await circleRef.get();
  if (!circleSnapshot.exists) {
    fail("not-found", "Circle not found", "circle_not_found");
  }
  if (circleSnapshot.get("ownerUid") !== uid) {
    fail("permission-denied", "Only the owner can remove members", "not_owner");
  }
  if (targetUid === uid) {
    fail(
      "failed-precondition",
      "The owner cannot remove themselves",
      "owner_cannot_leave",
    );
  }

  const memberRef = circleRef.collection("members").doc(targetUid);
  const memberSnapshot = await memberRef.get();
  if (!memberSnapshot.exists) {
    fail("not-found", "Member not found", "member_not_found");
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
});
