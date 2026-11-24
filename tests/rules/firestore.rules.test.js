const { readFileSync } = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, test } = require("node:test");

const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} = require("firebase/firestore");

const PROJECT_ID = "around-847b3";

const avatar = { type: "emoji", value: "🙂" };

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(
        path.join(__dirname, "../../firebase/firestore.rules"),
        "utf8",
      ),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "users/alice"), {
      displayName: "Alice",
      avatar,
      locale: "en",
      createdAt: new Date(),
      settings: { shareBattery: true },
    });
    await setDoc(doc(db, "users/bob"), {
      displayName: "Bob",
      avatar,
      locale: "en",
      createdAt: new Date(),
      settings: { shareBattery: true },
    });
    await setDoc(doc(db, "circles/c1"), {
      name: "Test Circle",
      ownerUid: "alice",
      createdAt: new Date(),
      memberCount: 2,
      memberUids: ["alice", "bob"],
    });
    await setDoc(doc(db, "circles/c1/members/alice"), {
      displayName: "Alice",
      avatar,
      role: "owner",
      joinedAt: new Date(),
      sharing: false,
      allowRing: true,
    });
    await setDoc(doc(db, "circles/c1/members/bob"), {
      displayName: "Bob",
      avatar,
      role: "member",
      joinedAt: new Date(),
      sharing: false,
      allowRing: true,
    });
    await setDoc(doc(db, "inviteCodes/abc123"), {
      circleId: "c1",
      createdBy: "alice",
      revoked: false,
      uses: 0,
      maxUses: 20,
    });
    await setDoc(doc(db, "userSecrets/alice"), {
      recoveryId: "secret",
    });
    await setDoc(doc(db, "joinAttempts/bob"), {
      count: 1,
    });
  });
});

test("signed-out users cannot read circles", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "circles/c1")));
});

test("a member can read the circle and its member list", async () => {
  const db = testEnv.authenticatedContext("alice").firestore();
  await assertSucceeds(getDoc(doc(db, "circles/c1")));
  await assertSucceeds(getDocs(collection(db, "circles/c1/members")));
});

test("the circles list query works for members", async () => {
  const db = testEnv.authenticatedContext("alice").firestore();
  const result = await assertSucceeds(
    getDocs(
      query(
        collection(db, "circles"),
        where("memberUids", "array-contains", "alice"),
      ),
    ),
  );
  if (result.size !== 1) {
    throw new Error(`Expected 1 circle, got ${result.size}`);
  }
});

test("outsiders cannot read the circle or member list", async () => {
  const db = testEnv.authenticatedContext("carol").firestore();
  await assertFails(getDoc(doc(db, "circles/c1")));
  await assertFails(getDocs(collection(db, "circles/c1/members")));
});

test("members cannot write circle documents", async () => {
  const db = testEnv.authenticatedContext("alice").firestore();
  await assertFails(updateDoc(doc(db, "circles/c1"), { name: "Renamed" }));
});

test("users can read their own profile but not someone else's", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  const bobDb = testEnv.authenticatedContext("bob").firestore();

  await assertSucceeds(getDoc(doc(aliceDb, "users/alice")));
  await assertFails(getDoc(doc(bobDb, "users/alice")));
});

test("users can update their own profile but not someone else's", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  await assertSucceeds(
    updateDoc(doc(aliceDb, "users/alice"), { displayName: "Alice Updated" }),
  );

  await assertFails(
    updateDoc(doc(aliceDb, "users/bob"), { displayName: "Hacked" }),
  );
});

test("members can update only their own allowed member fields", async () => {
  const db = testEnv.authenticatedContext("alice").firestore();

  await assertSucceeds(
    updateDoc(doc(db, "circles/c1/members/alice"), { sharing: true }),
  );
  await assertSucceeds(
    updateDoc(doc(db, "circles/c1/members/alice"), { historyEnabled: false }),
  );
  await assertSucceeds(
    updateDoc(doc(db, "circles/c1/members/alice"), { placeAlerts: false }),
  );
  await assertSucceeds(
    updateDoc(doc(db, "circles/c1/members/alice"), {
      displayName: "Alice",
      avatar: { type: "color", value: "#006A6A" },
    }),
  );
  await assertFails(
    updateDoc(doc(db, "circles/c1/members/alice"), { role: "member" }),
  );
  await assertFails(
    updateDoc(doc(db, "circles/c1/members/bob"), { sharing: true }),
  );
});

test("members manage Places but cannot write alert events", async () => {
  const db = testEnv.authenticatedContext("alice").firestore();
  const outsider = testEnv.authenticatedContext("carol").firestore();
  const place = doc(db, "circles/c1/places/home");

  await assertSucceeds(
    setDoc(place, {
      name: "Home",
      lat: 1,
      lng: 2,
      radiusM: 150,
      createdBy: "alice",
      createdAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(doc(db, "circles/c1/places/small"), {
      name: "Small",
      lat: 1,
      lng: 2,
      radiusM: 50,
      createdBy: "alice",
      createdAt: serverTimestamp(),
    }),
  );
  await assertFails(
    setDoc(doc(outsider, "circles/c1/places/outside"), {
      name: "Outside",
      lat: 1,
      lng: 2,
      radiusM: 150,
      createdBy: "carol",
      createdAt: serverTimestamp(),
    }),
  );
  await assertSucceeds(updateDoc(place, { name: "Home 2" }));
  await assertSucceeds(updateDoc(place, { radiusM: 300 }));
  await assertFails(updateDoc(place, { radiusM: 2000 }));
  await assertFails(updateDoc(place, { name: "" }));
  await assertFails(updateDoc(place, { createdBy: "bob" }));
  await assertSucceeds(getDocs(collection(db, "circles/c1/events")));
  await assertFails(
    setDoc(doc(db, "circles/c1/events/e1"), {
      type: "arrive",
      uid: "alice",
      placeId: "home",
      createdAt: serverTimestamp(),
    }),
  );
});

test("invite codes, user secrets, and join attempts are server-only", async () => {
  const db = testEnv.authenticatedContext("alice").firestore();
  await assertFails(getDoc(doc(db, "inviteCodes/abc123")));
  await assertFails(getDoc(doc(db, "userSecrets/alice")));
  await assertFails(getDoc(doc(db, "joinAttempts/bob")));
});
