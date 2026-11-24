import { FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

import { PLACE_ALERT_DEDUPE_WINDOW_MS } from "./constants";
import { fail } from "./errors";
import { db } from "./firebase";
import { requireString } from "./util";

interface DeviceToken {
  uid: string;
  docId: string;
  token: string;
}

function emulatorMode(): boolean {
  return Boolean(
    process.env.FIREBASE_AUTH_EMULATOR_HOST ||
      process.env.FIRESTORE_EMULATOR_HOST,
  );
}

export async function sendPlaceEvent(
  uid: string,
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const circleId = requireString(data, "circleId");
  const placeId = requireString(data, "placeId");
  const type = requireString(data, "type");

  if (type !== "arrive" && type !== "leave") {
    fail(400, "invalid_request", "Event type must be arrive or leave");
  }

  const circleRef = db.doc(`circles/${circleId}`);
  const memberRef = circleRef.collection("members").doc(uid);
  const placeRef = circleRef.collection("places").doc(placeId);

  const [circleSnapshot, memberSnapshot, placeSnapshot] = await Promise.all([
    circleRef.get(),
    memberRef.get(),
    placeRef.get(),
  ]);

  if (!circleSnapshot.exists) {
    fail(404, "not_found", "Circle not found");
  }
  if (!memberSnapshot.exists) {
    fail(403, "not_found", "You are not a member of this Circle");
  }
  if (!placeSnapshot.exists) {
    fail(404, "not_found", "Place not found");
  }

  const actorName =
    (memberSnapshot.get("displayName") as string | undefined) ?? "Someone";
  const circleName =
    (circleSnapshot.get("name") as string | undefined) ?? "Circle";
  const placeName =
    (placeSnapshot.get("name") as string | undefined) ?? "a place";

  const bucket = Math.floor(Date.now() / PLACE_ALERT_DEDUPE_WINDOW_MS);
  const eventId = `${uid}_${placeId}_${type}_${bucket}`;
  const eventRef = circleRef.collection("events").doc(eventId);

  let deduped = false;
  await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(eventRef);
    if (existing.exists) {
      deduped = true;
      return;
    }
    transaction.set(eventRef, {
      type,
      uid,
      placeId,
      placeName,
      createdAt: FieldValue.serverTimestamp(),
    });
  });

  if (deduped) {
    return { ok: true, deduped: true, sent: 0 };
  }

  if (emulatorMode() || process.env.FCM_DISABLED === "true") {
    return { ok: true, deduped: false, sent: 0, skipped: "emulator" };
  }

  const members = await circleRef.collection("members").get();
  const recipients = members.docs.filter(
    (member) => member.id !== uid && member.get("placeAlerts") !== false,
  );

  const tokenDocs: DeviceToken[] = [];
  await Promise.all(
    recipients.map(async (member) => {
      const devices = await db
        .collection("users")
        .doc(member.id)
        .collection("devices")
        .get();
      devices.forEach((device) => {
        const token = device.get("fcmToken");
        if (typeof token === "string" && token.length > 0) {
          tokenDocs.push({ uid: member.id, docId: device.id, token });
        }
      });
    }),
  );

  if (tokenDocs.length === 0) {
    return { ok: true, deduped: false, sent: 0 };
  }

  try {
    const result = await getMessaging().sendEachForMulticast({
      tokens: tokenDocs.map((entry) => entry.token),
      data: {
        kind: "place_alert",
        circleId,
        circleName,
        placeId,
        placeName,
        eventType: type,
        actorName,
        eventId,
      },
      android: { priority: "high" },
    });

    const invalid = new Map<string, DeviceToken>();
    result.responses.forEach((response, index) => {
      const code = response.error?.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        const entry = tokenDocs[index];
        if (entry) {
          invalid.set(`${entry.uid}/${entry.docId}`, entry);
        }
      }
    });

    await Promise.all(
      Array.from(invalid.values()).map((entry) =>
        db
          .collection("users")
          .doc(entry.uid)
          .collection("devices")
          .doc(entry.docId)
          .delete()
          .catch(() => undefined),
      ),
    );

    return {
      ok: true,
      deduped: false,
      sent: result.successCount,
      failed: result.failureCount,
    };
  } catch (error) {
    console.error("Place alert FCM send failed", error);
    return { ok: true, deduped: false, sent: 0, failed: tokenDocs.length };
  }
}
