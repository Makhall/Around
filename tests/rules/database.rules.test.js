const { readFileSync } = require("node:fs");
const path = require("node:path");
const { after, before, beforeEach, test } = require("node:test");

const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const { get, ref, set, serverTimestamp } = require("firebase/database");

const PROJECT_ID = "around-847b3";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    database: {
      rules: readFileSync(
        path.join(__dirname, "../../firebase/database.rules.json"),
        "utf8",
      ),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearDatabase();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.database();
    await set(ref(db, "circleMembers/c1/alice"), {
      role: "owner",
      joinedAt: 1,
    });
    await set(ref(db, "circleMembers/c1/bob"), {
      role: "member",
      joinedAt: 2,
    });
    await set(ref(db, "locations/c1/alice"), {
      lat: 1,
      lng: 2,
      t: 1,
      ts: 1,
    });
  });
});

test("members can read circle members and locations", async () => {
  const db = testEnv.authenticatedContext("alice").database();
  await assertSucceeds(get(ref(db, "circleMembers/c1")));
  await assertSucceeds(get(ref(db, "locations/c1")));
});

test("outsiders cannot read circle data", async () => {
  const db = testEnv.authenticatedContext("carol").database();
  await assertFails(get(ref(db, "circleMembers/c1")));
  await assertFails(get(ref(db, "locations/c1")));
});

test("members can write their own valid location", async () => {
  const db = testEnv.authenticatedContext("alice").database();
  await assertSucceeds(
    set(ref(db, "locations/c1/alice"), {
      lat: 1.5,
      lng: 2.5,
      acc: 10,
      t: 2,
      ts: serverTimestamp(),
    }),
  );
});

test("members cannot write locations for someone else", async () => {
  const db = testEnv.authenticatedContext("alice").database();
  await assertFails(
    set(ref(db, "locations/c1/bob"), {
      lat: 1.5,
      lng: 2.5,
      t: 2,
      ts: serverTimestamp(),
    }),
  );
});

test("non-members cannot write their own location", async () => {
  const db = testEnv.authenticatedContext("carol").database();
  await assertFails(
    set(ref(db, "locations/c1/carol"), {
      lat: 1.5,
      lng: 2.5,
      t: 2,
      ts: serverTimestamp(),
    }),
  );
});

test("invalid coordinates are rejected", async () => {
  const db = testEnv.authenticatedContext("alice").database();
  await assertFails(
    set(ref(db, "locations/c1/alice"), {
      lat: 200,
      lng: 2.5,
      t: 2,
      ts: serverTimestamp(),
    }),
  );
});
