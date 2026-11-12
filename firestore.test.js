const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid, ts) {
  return {
    userId: uid,
    callsign: "GhostAlpha",
    level: 1,
    xp: 0,
    credits: 500,
    strikePoints: 100,
    kills: 0,
    deaths: 0,
    assists: 0,
    wins: 0,
    matchesPlayed: 0,
    scorestreaksCalled: 0,
    ownedItemIds: ["m4a1_default", "skin_m4_standard", "op_vanguard"],
    claimedPassTiers: [],
    equippedPrimaryId: "m4a1_default",
    equippedPrimarySkinId: "skin_m4_standard",
    equippedOperatorSkinId: "op_vanguard",
    battlePassPremium: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied read on users, lobbies, and matches", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("lobbies").where("status", "==", "waiting").get());
  await assertFails(unauthDb.collection("matches").where("participantUids", "array-contains", ALICE_UID).get());
});

test("User profile: owner can create, read, and update; cross-user and ghost fields fail", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, ts))
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ credits: 99999, updatedAt: new Date() }));

  // Ghost field injection fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 1000,
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 750,
      kills: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Squad Lobby: host creates, friend queries waiting code and joins, non-host cannot launch", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const lobbyRef = aliceDb.collection("lobbies").doc("lobby_001");
  await assertSucceeds(
    lobbyRef.set({
      hostId: ALICE_UID,
      lobbyCode: "OPS742",
      mode: "tdm",
      status: "waiting",
      activeMatchId: "",
      memberUids: [ALICE_UID],
      memberRoster: [`${ALICE_UID}|GhostAlpha|1|1|alpha`],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob searches by code + waiting status
  await assertSucceeds(
    bobDb.collection("lobbies")
      .where("lobbyCode", "==", "OPS742")
      .where("status", "==", "waiting")
      .get()
  );

  // Bob joins lobby
  await assertSucceeds(
    bobDb.collection("lobbies").doc("lobby_001").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberRoster: [`${ALICE_UID}|GhostAlpha|1|1|alpha`, `${BOB_UID}|ShadowBob|3|1|alpha`],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Non-host Bob cannot change status to in_match
  await assertFails(
    bobDb.collection("lobbies").doc("lobby_001").update({
      status: "in_match",
      activeMatchId: "match_001",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Host Alice launches match
  await assertSucceeds(
    lobbyRef.update({
      status: "in_match",
      activeMatchId: "match_001",
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Multiplayer Match & Players: participant isolation, own-state updates, terminal lock", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const matchRef = aliceDb.collection("matches").doc("match_001");
  await assertSucceeds(
    matchRef.set({
      hostId: ALICE_UID,
      mode: "domination",
      status: "active",
      alphaScore: 0,
      bravoScore: 0,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Non-participant Charlie cannot read or list match
  await assertFails(charlieDb.collection("matches").doc("match_001").get());

  // Alice and Bob create their own player state docs
  await assertSucceeds(
    aliceDb.collection("matches").doc("match_001").collection("players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      callsign: "GhostAlpha",
      team: "alpha",
      x: 3.5,
      y: 3.5,
      yaw: 0.0,
      health: 100,
      kills: 0,
      deaths: 0,
      assists: 0,
      killPoints: 0,
      activeStreak: "",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot overwrite Alice's player state doc
  await assertFails(
    bobDb.collection("matches").doc("match_001").collection("players").doc(ALICE_UID).update({
      health: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice can query players in her match
  await assertSucceeds(
    aliceDb.collection("matches").doc("match_001").collection("players")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );

  // Host finishes match; subsequent updates blocked by terminal lock
  await assertSucceeds(
    matchRef.update({
      alphaScore: 200,
      bravoScore: 140,
      status: "finished",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    matchRef.update({
      alphaScore: 250,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
