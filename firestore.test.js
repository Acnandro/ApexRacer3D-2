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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

const sampleLobby = (hostId = ALICE_UID) => ({
  lobbyCode: "RAC123",
  name: "Pro Grand Prix",
  hostId: hostId,
  hostName: "Alice Racer",
  status: "waiting",
  maxPlayers: 4,
  playerCount: 1,
  trackId: "pro_speedway",
  createdAt: new Date(),
  updatedAt: new Date(),
});

const samplePlayer = (userId = ALICE_UID) => ({
  userId: userId,
  displayName: "Alice",
  carColor: "#E53935",
  isReady: true,
  isHost: true,
  posX: 0.0,
  posY: 0.0,
  posZ: 10.0,
  rotY: 0.0,
  speedKmh: 120.5,
  isNitro: false,
  isBraking: false,
  currentLap: 1,
  checkpointIndex: 2,
  lapTime: 14.2,
  bestLapTime: 38.5,
  updatedAt: new Date(),
});

const sampleLeaderboard = (userId = ALICE_UID) => ({
  userId: userId,
  displayName: "Alice",
  trackId: "pro_speedway",
  bestLapTime: 38.52,
  vehicleClass: "Formula V",
  recordedAt: new Date(),
});

test("Unauthenticated user: cannot read lobbies", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("lobbies").get());
});

test("Authenticated user: can create valid lobby where hostId == auth.uid", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("lobbies").doc("lobby_1").set(sampleLobby(ALICE_UID)));
});

test("Authenticated user: cannot create lobby with another user as host", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("lobbies").doc("lobby_fake").set(sampleLobby(BOB_UID)));
});

test("Player state: can create own player state in lobby", async () => {
  // Seed lobby
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("lobbies").doc("lobby_1").set(sampleLobby(ALICE_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("lobbies").doc("lobby_1").collection("players").doc(ALICE_UID).set(samplePlayer(ALICE_UID))
  );
});

test("Player state: cannot write player state for another user", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("lobbies").doc("lobby_1").set(sampleLobby(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("lobbies").doc("lobby_1").collection("players").doc(ALICE_UID).set(samplePlayer(ALICE_UID))
  );
});

test("Leaderboard: user can record their own lap time", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("leaderboards").doc("record_alice_speedway").set(sampleLeaderboard(ALICE_UID))
  );
});

test("Leaderboard: unauthenticated user can read leaderboards", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("leaderboards").get());
});

test("Leaderboard: user cannot overwrite another user's lap time", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("leaderboards").doc("record_alice").set(sampleLeaderboard(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("leaderboards").doc("record_alice").set(sampleLeaderboard(BOB_UID))
  );
});
