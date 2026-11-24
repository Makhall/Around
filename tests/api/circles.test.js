const assert = require("node:assert/strict");
const { after, afterEach, before, test } = require("node:test");

const PROJECT_ID = "around-847b3";
const BASE_URL = "http://127.0.0.1:3100";

process.env.FIREBASE_PROJECT_ID = PROJECT_ID;
process.env.FIREBASE_DATABASE_URL =
  "https://around-847b3-default-rtdb.asia-southeast1.firebasedatabase.app";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";
process.env.FIREBASE_DATABASE_EMULATOR_HOST ??= "127.0.0.1:9000";

const { startServer, stopServer } = require("../../backend/dist/dev-server");

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
  connectDatabaseEmulator,
  get,
  getDatabase,
  ref,
} = require("firebase/database");

const apps = [];
let server;
let userCounter = 0;

before(async () => {
  server = await startServer(3100);
});

after(async () => {
  if (server) {
    await stopServer(server);
  }
});

async function createUser(name, avatar) {
  const app = initializeApp(
    {
      apiKey: "fake-api-key",
      projectId: PROJECT_ID,
      databaseURL:
        "https://around-847b3-default-rtdb.asia-southeast1.firebasedatabase.app",
    },
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
  const database = getDatabase(app);
  connectDatabaseEmulator(database, "127.0.0.1", 9000);

  if (name) {
    await setDoc(doc(db, "users", uid), {
      displayName: name,
      avatar: avatar ?? { type: "emoji", value: "🙂" },
      locale: "en",
      createdAt: serverTimestamp(),
      settings: { shareBattery: true },
    });
  }

  return { app, auth, db, database, uid, user: credential.user };
}

async function callApi(user, path, body) {
  const token = await user.user.getIdToken();
  const response = await fetch(`${BASE_URL}/api/${path}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body ?? {}),
  });
  const payload = await response.json();
  if (!response.ok) {
    const error = new Error(
      payload.error?.message ?? `API returned ${response.status}`,
    );
    error.code = payload.error?.code;
    error.status = response.status;
    throw error;
  }
  return payload;
}

async function expectError(promise, code) {
  try {
    await promise;
  } catch (error) {
    assert.equal(
      error.code,
      code,
      `Expected ${code}, got ${error.code}: ${error.message}`,
    );
    return;
  }
  assert.fail(`Expected API call to fail with ${code}`);
}

async function createCircle(user, name = "Test Circle") {
  return callApi(user, "createCircle", { name });
}

afterEach(async () => {
  while (apps.length > 0) {
    await deleteApp(apps.pop());
  }
});

test("unauthenticated requests are rejected", async () => {
  const response = await fetch(`${BASE_URL}/api/createCircle`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ name: "No Auth" }),
  });
  assert.equal(response.status, 401);
  const payload = await response.json();
  assert.equal(payload.error.code, "unauthenticated");
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

  const mirror = await get(
    ref(alice.database, `circleMembers/${created.circleId}/${alice.uid}`),
  );
  assert.equal(mirror.val()?.role, "owner");
});

test("a second user can join by code and see both members", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice, "Friends");

  const joined = await callApi(bob, "joinCircle", {
    code: created.inviteCode,
  });

  assert.equal(joined.circleId, created.circleId);
  assert.equal(joined.circleName, "Friends");

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
    callApi(bob, "joinCircle", { code: "ZZZZZZZZ" }),
    "code_not_found",
  );
});

test("rotating the invite code revokes the old code", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice, "Rotate Test");

  const rotated = await callApi(alice, "rotateInviteCode", {
    circleId: created.circleId,
  });

  assert.notEqual(rotated.inviteCode, created.inviteCode);

  await expectError(
    callApi(bob, "joinCircle", { code: created.inviteCode }),
    "code_revoked",
  );

  const joined = await callApi(bob, "joinCircle", {
    code: rotated.inviteCode,
  });
  assert.equal(joined.circleId, created.circleId);
});

test("the owner cannot leave their own Circle", async () => {
  const alice = await createUser("Alice");
  const created = await createCircle(alice);

  await expectError(
    callApi(alice, "leaveCircle", { circleId: created.circleId }),
    "owner_cannot_leave",
  );
});

test("a member can leave and immediately loses access", async () => {
  const alice = await createUser("Alice");
  const bob = await createUser("Bob");
  const created = await createCircle(alice);
  await callApi(bob, "joinCircle", { code: created.inviteCode });

  await callApi(bob, "leaveCircle", { circleId: created.circleId });

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
  await callApi(bob, "joinCircle", { code: created.inviteCode });

  await callApi(alice, "removeMember", {
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
    callApi(carol, "createCircle", { name: "No Profile" }),
    "profile_missing",
  );
});

test("repeated invalid join attempts are rate limited", async () => {
  const bob = await createUser("Bob");

  for (let attempt = 0; attempt < 10; attempt += 1) {
    await expectError(
      callApi(bob, "joinCircle", { code: "ZZZZZZZZ" }),
      "code_not_found",
    );
  }

  await expectError(
    callApi(bob, "joinCircle", { code: "ZZZZZZZZ" }),
    "rate_limited",
  );
});
