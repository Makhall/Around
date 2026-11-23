const assert = require("node:assert/strict");
const { after, afterEach, test } = require("node:test");

process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";

const { initializeApp, deleteApp } = require("firebase/app");
const {
  connectAuthEmulator,
  getAuth,
  signInAnonymously,
} = require("firebase/auth");
const {
  collection,
  connectFirestoreEmulator,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  serverTimestamp,
  setDoc,
} = require("firebase/firestore");
const {
  connectFunctionsEmulator,
  getFunctions,
  httpsCallable,
} = require("firebase/functions");

const PROJECT_ID = "around-847b3";
const REGION = "asia-southeast1";

const apps = [];
let userCounter = 0;

async function createUser(name, avatar) {
  const app = initializeApp(
    { apiKey: "fake-api-key", projectId: PROJECT_ID },
    `around-test-${Date.now()}-${userCounter++}`,
  );
  apps.push(app);

  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", {
    disableWarnings: true,
  });
  const credential = await signInAnonymously(auth);
  const uid = credential.user.uid;

  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);

  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);

  if (name) {
    await setDoc(doc(db, "users", uid), {
      displayName: name,
      avatar: avatar ?? { type: "emoji", value: "🙂" },
      locale: "en",
      createdAt: serverTimestamp(),
      settings: { shareBattery: true },
    });
  }

  return { app, auth, db, functions, uid };
}

function callable(user, name) {
  return httpsCallable(user.functions, name);
}

async function expectError(promise, reason) {
  try {
    await promise;
  } catch (error) {
    assert.equal(
      error.details?.reason,
      reason,
      `Expected reason ${reason}, got ${JSON.stringify(error.details)} (${error.code}: ${error.message})`,
    );
    return;
  }
  assert.fail(`Expected callable to fail with ${reason}`);
}

async function createCircle(user, name = "Test Circle") {
  const result = await callable(user, "createCircle")({ name });
  return result.data;
}

afterEach(async () => {
  while (apps.length > 0) {
    await deleteApp(apps.pop());
  }
});

after(async () => {
  // The emulator is torn down by `firebase emulators:exec`.
});

test("createCircle creates an owned Circle with an invite code", async () => {
  const alice = await createUser("Alice");
  const created = await createCircle(alice, "Family");

  assert.match(created.inviteCode, /^[A-Z2-9]{8}$/);
  assert.ok(created.circleId);
  assert.equal(created.circleName, "Family");
  assert.ok(created.expiresAt > Date.now());

  const circle = await getDoc(doc(alice.db, "circles", created.circleId));
  assert.equal(circle.data().memberCount, 1);
  assert.deepEqual(circle.data().memberUids, [alice.uid]);

  const members = await getDocs(
    collection(alice.db, "circles", created.circleId, "members"),
  );
  assert.equal(members.size, 1);
  assert.equal(members.docs[0].id, alice.uid);
  assert.equal(members.docs[0].data().role, "owner");
});

test("a second user can join by code and see both members", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice, "Friends");

  const joined = await callable(bob, "joinCircle")({
    code: created.inviteCode,
  });

  assert.equal(joined.data.circleId, created.circleId);
  assert.equal(joined.data.circleName, "Friends");

  const circle = await getDoc(doc(bob.db, "circles", created.circleId));
  assert.equal(circle.data().memberCount, 2);
  assert.deepEqual(
    [...circle.data().memberUids].sort(),
    [alice.uid, bob.uid].sort(),
  );

  const members = await getDocs(
    collection(bob.db, "circles", created.circleId, "members"),
  );
  assert.equal(members.size, 2);
  const bobMember = members.docs.find((document) => document.id === bob.uid);
  assert.equal(bobMember.data().role, "member");
});

test("an unknown invite code returns code_not_found", async () => {
  const bob = await createUser("Bob");
  await expectError(
    callable(bob, "joinCircle")({ code: "ZZZZZZZZ" }),
    "code_not_found",
  );
});

test("rotating the invite code revokes the old code", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice, "Rotate Test");

  const rotated = await callable(alice, "rotateInviteCode")({
    circleId: created.circleId,
  });

  assert.notEqual(rotated.data.inviteCode, created.inviteCode);

  await expectError(
    callable(bob, "joinCircle")({ code: created.inviteCode }),
    "code_revoked",
  );

  const joined = await callable(bob, "joinCircle")({
    code: rotated.data.inviteCode,
  });
  assert.equal(joined.data.circleId, created.circleId);
});

test("the owner cannot leave their own Circle", async () => {
  const alice = await createUser("Alice");
  const created = await createCircle(alice);

  await expectError(
    callable(alice, "leaveCircle")({ circleId: created.circleId }),
    "owner_cannot_leave",
  );
});

test("a member can leave and immediately loses access", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice);
  await callable(bob, "joinCircle")({ code: created.inviteCode });

  await callable(bob, "leaveCircle")({ circleId: created.circleId });

  await assert.rejects(
    getDoc(doc(bob.db, "circles", created.circleId)),
    (error) => error.code === "permission-denied",
  );
  await assert.rejects(
    getDocs(collection(bob.db, "circles", created.circleId, "members")),
    (error) => error.code === "permission-denied",
  );
});

test("the owner can remove a member and revoke access", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice);
  await callable(bob, "joinCircle")({ code: created.inviteCode });

  await callable(alice, "removeMember")({
    circleId: created.circleId,
    targetUid: bob.uid,
  });

  const circle = await getDoc(doc(alice.db, "circles", created.circleId));
  assert.equal(circle.data().memberCount, 1);
  assert.deepEqual(circle.data().memberUids, [alice.uid]);

  await assert.rejects(
    getDoc(doc(bob.db, "circles", created.circleId)),
    (error) => error.code === "permission-denied",
  );
});

test("creating a Circle without a profile is rejected", async () => {
  const carol = await createUser(null);
  await expectError(
    callable(carol, "createCircle")({ name: "No Profile" }),
    "profile_missing",
  );
});

test("repeated invalid join attempts are rate limited", async () => {
  const bob = await createUser("Bob");

  for (let attempt = 0; attempt < 10; attempt += 1) {
    await expectError(
      callable(bob, "joinCircle")({ code: "ZZZZZZZZ" }),
      "code_not_found",
    );
  }

  await expectError(
    callable(bob, "joinCircle")({ code: "ZZZZZZZZ" }),
    "rate_limited",
  );
});
