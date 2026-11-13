const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("messGroups").get());
  await assertFails(unauthDb.collection("messInvites").doc("ABC123").get());
  await assertFails(
    unauthDb.collection("messGroups").doc("g1").set({
      name: "Rogue Mess",
      ownerUid: ALICE_UID,
    })
  );
});

test("User profile enforces strict PII owner isolation and shadow field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      activeGroupId: "",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot read Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Alice cannot inject ghost field (Shadow Update test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Invite codes forbid listing, allow exact get, and allow invited user to join group", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const batch = aliceDb.batch();
  const groupRef = aliceDb.collection("messGroups").doc("group_1");
  const inviteRef = aliceDb.collection("messInvites").doc("KM7P2X");
  const cycleRef = groupRef.collection("cycles").doc("cycle_1");

  batch.set(groupRef, {
    name: "Green Hostel Mess",
    ownerUid: ALICE_UID,
    memberUids: [ALICE_UID],
    inviteCode: "KM7P2X",
    currencySymbol: "৳",
    cycleMode: "MONTHLY",
    activeCycleId: "cycle_1",
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  });
  batch.set(inviteRef, {
    inviteCode: "KM7P2X",
    groupId: "group_1",
    groupName: "Green Hostel Mess",
    ownerUid: ALICE_UID,
    createdAt: pastTimestamp(),
  });
  batch.set(cycleRef, {
    groupId: "group_1",
    memberUids: [ALICE_UID],
    label: "Nov 2026",
    cycleType: "MONTHLY",
    startDateEpochDay: 20758,
    endDateEpochDay: 20787,
    status: "ACTIVE",
    createdByUid: ALICE_UID,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  });
  await assertSucceeds(batch.commit());

  // Listing invites is strictly forbidden
  await assertFails(bobDb.collection("messInvites").get());

  // Bob can look up exact invite code
  await assertSucceeds(bobDb.collection("messInvites").doc("KM7P2X").get());

  // Bob cannot read group before joining
  await assertFails(bobDb.collection("messGroups").doc("group_1").get());

  // Bob cannot inject Charlie when joining
  await assertFails(
    bobDb.collection("messGroups").doc("group_1").update({
      memberUids: [ALICE_UID, CHARLIE_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Bob joins himself cleanly
  await assertSucceeds(
    bobDb.collection("messGroups").doc("group_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Bob can now read and query groups he belongs to
  await assertSucceeds(
    bobDb.collection("messGroups").where("memberUids", "array-contains", BOB_UID).get()
  );

  // Unfiltered query on messGroups fails (rules are not filters)
  await assertFails(bobDb.collection("messGroups").get());

  // Charlie still cannot read group
  await assertFails(charlieDb.collection("messGroups").doc("group_1").get());
});

test("Transactions enforce positive amounts, valid types, and member isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("messGroups").doc("group_2").set({
      name: "Oak Mess",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      inviteCode: "OAK123",
      currencySymbol: "৳",
      cycleMode: "WEEKLY",
      activeCycleId: "c1",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  const txCol = aliceDb.collection("messGroups").doc("group_2").collection("transactions");

  // Negative or zero amount rejected
  await assertFails(
    txCol.doc("tx_bad").set({
      groupId: "group_2",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      type: "BAZAAR",
      title: "Rice & Fish",
      amount: 0,
      paidByMemberId: "m1",
      dateEpochDay: 20768,
      createdByUid: ALICE_UID,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Valid transaction succeeds
  await assertSucceeds(
    txCol.doc("tx_ok").set({
      groupId: "group_2",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      type: "BAZAAR",
      title: "Rice & Fish",
      notes: "10kg Miniket + Rohu",
      amount: 1450.5,
      paidByMemberId: "m1",
      dateEpochDay: 20768,
      createdByUid: ALICE_UID,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Matching member query succeeds
  await assertSucceeds(
    txCol.where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Outsider Charlie cannot read or write
  await assertFails(
    charlieDb.collection("messGroups").doc("group_2").collection("transactions").doc("tx_ok").get()
  );
});

test("Closed cycle enforces terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const groupRef = aliceDb.collection("messGroups").doc("group_3");
  const cycleRef = groupRef.collection("cycles").doc("c_closed");

  await assertSucceeds(
    groupRef.set({
      name: "Maple Mess",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      inviteCode: "MPL999",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      activeCycleId: "c_closed",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(
    cycleRef.set({
      groupId: "group_3",
      memberUids: [ALICE_UID],
      label: "Oct 2026",
      cycleType: "MONTHLY",
      startDateEpochDay: 20727,
      endDateEpochDay: 20757,
      status: "ACTIVE",
      createdByUid: ALICE_UID,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Close cycle
  await assertSucceeds(
    cycleRef.update({
      status: "CLOSED",
      updatedAt: pastTimestamp(),
    })
  );

  // Cannot reopen or rename closed cycle
  await assertFails(
    cycleRef.update({
      status: "ACTIVE",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    cycleRef.update({
      label: "Hacked Oct",
      updatedAt: pastTimestamp(),
    })
  );
});

test("MealEntries enforce member updates, disallow createdAt mutation, and allow newly joined group members", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const groupRef = aliceDb.collection("messGroups").doc("group_meals");
  await assertSucceeds(
    groupRef.set({
      name: "Sunrise Mess",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      inviteCode: "SUN123",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      activeCycleId: "c1",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("messInvites").doc("SUN123").set({
      inviteCode: "SUN123",
      groupId: "group_meals",
      groupName: "Sunrise Mess",
      ownerUid: ALICE_UID,
      createdAt: pastTimestamp(),
    })
  );

  const mealRef = groupRef.collection("mealEntries").doc("c1_20768_m1");
  const createdTs = pastTimestamp();
  await assertSucceeds(
    mealRef.set({
      groupId: "group_meals",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      memberId: "m1",
      dateEpochDay: 20768,
      lunch: 1,
      dinner: 1,
      guestMeals: 0,
      updatedByUid: ALICE_UID,
      createdAt: createdTs,
      updatedAt: pastTimestamp(),
    })
  );

  // Overwriting createdAt on update fails
  await assertFails(
    mealRef.set({
      groupId: "group_meals",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      memberId: "m1",
      dateEpochDay: 20768,
      lunch: 1,
      dinner: 1,
      guestMeals: 1,
      updatedByUid: ALICE_UID,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Partial update without createdAt succeeds
  await assertSucceeds(
    mealRef.update({
      lunch: 1,
      dinner: 1,
      guestMeals: 1,
      updatedByUid: ALICE_UID,
      memberUids: [ALICE_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Legacy meal entry containing optional breakfast field can still be updated without breakfast
  const legacyMealRef = groupRef.collection("mealEntries").doc("c1_20767_m1");
  await assertSucceeds(
    legacyMealRef.set({
      groupId: "group_meals",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      memberId: "m1",
      dateEpochDay: 20767,
      breakfast: 1,
      lunch: 1,
      dinner: 1,
      guestMeals: 0,
      updatedByUid: ALICE_UID,
      createdAt: createdTs,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    legacyMealRef.update({
      lunch: 2,
      dinner: 1,
      guestMeals: 0,
      updatedByUid: ALICE_UID,
      memberUids: [ALICE_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Bob joins group and updates existing meal entry even before memberUids propagation
  await assertSucceeds(
    bobDb.collection("messGroups").doc("group_meals").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("messGroups").doc("group_meals").collection("mealEntries").doc("c1_20768_m1").update({
      lunch: 1,
      dinner: 1,
      guestMeals: 0,
      updatedByUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Outsider Charlie still fails
  await assertFails(
    charlieDb.collection("messGroups").doc("group_meals").collection("mealEntries").doc("c1_20768_m1").get()
  );
});

test("ActivityLogs enforce actorUid authenticity, immutable content, and member isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const groupRef = aliceDb.collection("messGroups").doc("group_logs");
  await assertSucceeds(
    groupRef.set({
      name: "Emerald Logs Mess",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      inviteCode: "LOG123",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      activeCycleId: "c1",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  const logsCol = groupRef.collection("activityLogs");
  const createdTs = pastTimestamp();

  // Valid activity log succeeds
  await assertSucceeds(
    logsCol.doc("log_1").set({
      groupId: "group_logs",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      category: "MEAL",
      action: "UPDATED",
      actorUid: ALICE_UID,
      actorName: "Alice",
      targetMemberName: "Rahim",
      title: "Meals Updated · Rahim",
      changeDetail: "Lunch 1 -> 2, Dinner 1 (3 meals)",
      dateEpochDay: 20768,
      createdAt: createdTs,
    })
  );

  // Spoofed actorUid is rejected
  await assertFails(
    logsCol.doc("log_spoof").set({
      groupId: "group_logs",
      cycleId: "c1",
      memberUids: [ALICE_UID],
      category: "EXPENSE",
      action: "ADDED",
      actorUid: BOB_UID,
      actorName: "Bob",
      title: "Bazaar Added",
      changeDetail: "৳500",
      dateEpochDay: 20768,
      createdAt: pastTimestamp(),
    })
  );

  // Tampering with actorName or changeDetail on update is rejected
  await assertFails(
    logsCol.doc("log_1").update({
      actorName: "Bob",
      changeDetail: "Tampered",
    })
  );

  // Delete is rejected
  await assertFails(logsCol.doc("log_1").delete());

  // Outsider Charlie cannot list all activity logs, query Alice's logs, or read Alice's log doc
  await assertFails(
    charlieDb.collection("messGroups").doc("group_logs").collection("activityLogs").get()
  );
  await assertFails(
    charlieDb.collection("messGroups").doc("group_logs").collection("activityLogs")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("messGroups").doc("group_logs").collection("activityLogs").doc("log_1").get()
  );
  await assertFails(
    bobDb.collection("messGroups").doc("group_logs").collection("activityLogs").doc("log_1").get()
  );
});

