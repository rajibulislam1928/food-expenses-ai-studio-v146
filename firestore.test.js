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

  // After joining, Bob can create his own linked member row, but cannot create another person's row.
  const joinedMembers = bobDb.collection("messGroups").doc("group_1").collection("members");
  await assertSucceeds(joinedMembers.doc("bob_member").set({
    groupId: "group_1",
    memberUids: [ALICE_UID, BOB_UID],
    name: "Bob",
    linkedUid: BOB_UID,
    role: "MEMBER",
    isActive: true,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));
  await assertFails(joinedMembers.doc("charlie_member").set({
    groupId: "group_1",
    memberUids: [ALICE_UID, BOB_UID],
    name: "Charlie",
    linkedUid: CHARLIE_UID,
    role: "MEMBER",
    isActive: true,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  }));

  // Unfiltered query on messGroups fails (rules are not filters)
  await assertFails(bobDb.collection("messGroups").get());

  // Charlie still cannot read group
  await assertFails(charlieDb.collection("messGroups").doc("group_1").get());
});

test("Members can set only their own bounded profile photo, visible to their group", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = "avatar_group";

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("messGroups").doc(groupId).set({
      name: "Avatar Mess",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "AVT123",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      activeCycleId: "cycle1",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
    const members = db.collection("messGroups").doc(groupId).collection("members");
    for (const [id, uid, name] of [["alice_member", ALICE_UID, "Alice"], ["bob_member", BOB_UID, "Bob"]]) {
      await members.doc(id).set({
        groupId,
        memberUids: [ALICE_UID, BOB_UID],
        name,
        linkedUid: uid,
        role: id === "alice_member" ? "MANAGER" : "MEMBER",
        isActive: true,
        createdAt: pastTimestamp(),
        updatedAt: pastTimestamp(),
      });
    }
  });

  const aliceMember = aliceDb.collection("messGroups").doc(groupId).collection("members").doc("alice_member");
  const bobMember = bobDb.collection("messGroups").doc(groupId).collection("members").doc("bob_member");
  await assertSucceeds(aliceMember.update({ avatarBase64: "Zm9v", updatedAt: pastTimestamp() }));
  const visible = await assertSucceeds(bobDb.collection("messGroups").doc(groupId).collection("members").doc("alice_member").get());
  if (visible.data().avatarBase64 !== "Zm9v") throw new Error("Group member photo was not visible to another member");
  await assertSucceeds(bobMember.update({ avatarBase64: "YmFy", updatedAt: pastTimestamp() }));
  await assertFails(bobDb.collection("messGroups").doc(groupId).collection("members").doc("alice_member").update({ avatarBase64: "YmFy", updatedAt: pastTimestamp() }));
  await assertFails(aliceMember.update({ name: "Changed with photo", avatarBase64: "bmV3", updatedAt: pastTimestamp() }));
  await assertFails(aliceMember.update({ avatarBase64: "x".repeat(90001), updatedAt: pastTimestamp() }));
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
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
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

  // The manager can reopen a closed cycle; a non-manager cannot.
  await assertSucceeds(
    cycleRef.update({
      status: "ACTIVE",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(cycleRef.update({ status: "CLOSED", updatedAt: pastTimestamp() }));
  await assertFails(
    bobDb.collection("messGroups").doc("group_3").collection("cycles").doc("c_closed").update({
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

  // Only the manager can remove audit logs during a reset.
  await assertFails(bobDb.collection("messGroups").doc("group_logs").collection("activityLogs").doc("log_1").delete());
  await assertSucceeds(logsCol.doc("log_1").delete());

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


test("Only the group owner can add a member row", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = "manager_add_group";
  const memberPath = (db, id) => db.collection("messGroups").doc(groupId).collection("members").doc(id);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("messGroups").doc(groupId).set({
      name: "Manager Add Test",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "ADD123",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
  });

  const row = {
    groupId,
    memberUids: [ALICE_UID, BOB_UID],
    name: "New roommate",
    linkedUid: "",
    role: "MEMBER",
    isActive: true,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };
  await assertFails(memberPath(bobDb, "bob_added").set(row));
  await assertSucceeds(memberPath(aliceDb, "alice_added").set(row));
});

test("Manager removal revokes access while retaining historical member and meal records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = "remove_member_group";
  const groupRef = aliceDb.collection("messGroups").doc(groupId);
  const bobGroupRef = bobDb.collection("messGroups").doc(groupId);
  const bobMemberRef = bobGroupRef.collection("members").doc("bob_member");
  const memberRef = groupRef.collection("members").doc("bob_member");
  const mealRef = groupRef.collection("mealEntries").doc("cycle_1_20768_bob_member");

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("messGroups").doc(groupId).set({
      name: "Remove Test",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "RMV123",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
    await db.collection("messGroups").doc(groupId).collection("members").doc("bob_member").set({
      groupId,
      memberUids: [ALICE_UID, BOB_UID],
      name: "Bob",
      linkedUid: BOB_UID,
      role: "MEMBER",
      isActive: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
    await db.collection("messGroups").doc(groupId).collection("mealEntries").doc("cycle_1_20768_bob_member").set({
      groupId,
      cycleId: "cycle_1",
      memberUids: [ALICE_UID, BOB_UID],
      memberId: "bob_member",
      dateEpochDay: 20768,
      lunch: 1,
      dinner: 1,
      guestMeals: 0,
      updatedByUid: ALICE_UID,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
  });

  const unauthorizedRemoval = bobDb.batch();
  unauthorizedRemoval.update(bobGroupRef, { memberUids: [ALICE_UID], updatedAt: pastTimestamp() });
  unauthorizedRemoval.update(bobMemberRef, {
    memberUids: [ALICE_UID],
    linkedUid: "",
    isActive: false,
    isRemoved: true,
    updatedAt: pastTimestamp(),
  });
  await assertFails(unauthorizedRemoval.commit());

  const batch = aliceDb.batch();
  batch.update(groupRef, { memberUids: [ALICE_UID], updatedAt: pastTimestamp() });
  batch.update(memberRef, {
    memberUids: [ALICE_UID],
    linkedUid: "",
    isActive: false,
    isRemoved: true,
    updatedAt: pastTimestamp(),
  });
  await assertSucceeds(batch.commit());

  await assertFails(bobDb.collection("messGroups").doc(groupId).get());
  await assertFails(bobDb.collection("messGroups").doc(groupId).collection("members").doc("bob_member").get());
  const bobMealRef = bobDb.collection("messGroups").doc(groupId).collection("mealEntries").doc("cycle_1_20768_bob_member");
  await assertFails(bobMealRef.get());
  await assertFails(
    bobDb.collection("messGroups").doc(groupId).collection("mealEntries")
      .where("memberUids", "array-contains", BOB_UID).get()
  );

  const retained = await assertSucceeds(memberRef.get());
  if (!retained.data().isRemoved || retained.data().isActive) throw new Error("Removed member was not retained as inactive history");
  const historicalMeal = await assertSucceeds(mealRef.get());
  if (historicalMeal.data().lunch !== 1) throw new Error("Historical meal entry was deleted or changed");
});


test("Only manager or record author may delete member-associated meal and transaction records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupRef = aliceDb.collection("messGroups").doc("delete_history_group");
  const mealRef = groupRef.collection("mealEntries").doc("meal_by_alice");
  const txRef = groupRef.collection("transactions").doc("tx_by_alice");

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("messGroups").doc("delete_history_group").set({
      name: "Delete History Test",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "DEL123",
      currencySymbol: "৳",
      cycleMode: "MONTHLY",
      activeCycleId: "cycle_1",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    });
    await db.collection("messGroups").doc("delete_history_group").collection("mealEntries").doc("meal_by_alice").set({
      groupId: "delete_history_group", cycleId: "cycle_1", memberUids: [ALICE_UID, BOB_UID],
      memberId: "member_1", dateEpochDay: 20768, lunch: 1, dinner: 0, guestMeals: 0,
      updatedByUid: ALICE_UID, createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
    });
    await db.collection("messGroups").doc("delete_history_group").collection("transactions").doc("tx_by_alice").set({
      groupId: "delete_history_group", cycleId: "cycle_1", memberUids: [ALICE_UID, BOB_UID],
      type: "BAZAAR", title: "Rice", amount: 10, paidByMemberId: "member_1", dateEpochDay: 20768,
      createdByUid: ALICE_UID, createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
    });
  });

  await assertFails(bobDb.collection("messGroups").doc("delete_history_group")
    .collection("mealEntries").doc("meal_by_alice").delete());
  await assertFails(bobDb.collection("messGroups").doc("delete_history_group")
    .collection("transactions").doc("tx_by_alice").delete());
  await assertSucceeds(mealRef.delete());
  await assertSucceeds(txRef.delete());
});


test("Only the group manager may add or delete member profiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupRef = aliceDb.collection("messGroups").doc("member_admin_group");
  const aliceMemberRef = groupRef.collection("members").doc("alice_member");
  const bobMemberRef = bobDb.collection("messGroups").doc("member_admin_group").collection("members").doc("bob_member");

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const base = db.collection("messGroups").doc("member_admin_group");
    await base.set({
      name: "Member Admin Test", ownerUid: ALICE_UID, memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "ADM123", currencySymbol: "৳", cycleMode: "MONTHLY",
      activeCycleId: "cycle_1", createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
    });
    const members = base.collection("members");
    await members.doc("alice_member").set({
      groupId: "member_admin_group", memberUids: [ALICE_UID, BOB_UID], name: "Alice",
      linkedUid: ALICE_UID, role: "MANAGER", isActive: true,
      createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
    });
    await members.doc("bob_member").set({
      groupId: "member_admin_group", memberUids: [ALICE_UID, BOB_UID], name: "Bob",
      linkedUid: BOB_UID, role: "MEMBER", isActive: true,
      createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
    });
  });

  await assertFails(bobDb.collection("messGroups").doc("member_admin_group").collection("members").doc("new_member").set({
    groupId: "member_admin_group", memberUids: [ALICE_UID, BOB_UID], name: "Eve",
    linkedUid: "", role: "MEMBER", isActive: true, createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
  }));
  await assertFails(bobMemberRef.delete());
  await assertSucceeds(aliceDb.collection("messGroups").doc("member_admin_group").collection("members").doc("new_member").set({
    groupId: "member_admin_group", memberUids: [ALICE_UID, BOB_UID], name: "Eve",
    linkedUid: "", role: "MEMBER", isActive: true, createdAt: pastTimestamp(), updatedAt: pastTimestamp(),
  }));
  await assertSucceeds(bobMemberRef.get());
  await assertSucceeds(aliceMemberRef.get());
  await assertSucceeds(aliceDb.collection("messGroups").doc("member_admin_group").collection("members").doc("bob_member").delete());
});

test("only enabled editors can write meals and expenses; manager can grant edit access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const groupId = "edit_permission_group";
  const groupRef = aliceDb.collection("messGroups").doc(groupId);
  const bobGroupRef = bobDb.collection("messGroups").doc(groupId);
  const createdAt = pastTimestamp();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const group = db.collection("messGroups").doc(groupId);
    await group.set({
      name: "Permission Test",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      editorUids: [ALICE_UID],
      editPermissionsInitialized: true,
      inviteCode: "ED1T99",
      currencySymbol: "₹",
      cycleMode: "MONTHLY",
      activeCycleId: "cycle_1",
      createdAt,
      updatedAt: createdAt,
    });
    const members = group.collection("members");
    await members.doc("alice_member").set({
      groupId, memberUids: [ALICE_UID, BOB_UID], name: "Alice", linkedUid: ALICE_UID,
      role: "MANAGER", isActive: true, canEditMealsAndExpenses: true,
      createdAt, updatedAt: createdAt,
    });
    await members.doc("bob_member").set({
      groupId, memberUids: [ALICE_UID, BOB_UID], name: "Bob", linkedUid: BOB_UID,
      role: "MEMBER", isActive: true, canEditMealsAndExpenses: false,
      createdAt, updatedAt: createdAt,
    });
  });

  const meal = {
    groupId,
    cycleId: "cycle_1",
    memberUids: [ALICE_UID, BOB_UID],
    memberId: "bob_member",
    dateEpochDay: 20768,
    lunch: 1,
    dinner: 1,
    guestMeals: 0,
    updatedByUid: BOB_UID,
    createdAt,
    updatedAt: pastTimestamp(),
  };
  await assertFails(bobGroupRef.collection("mealEntries").doc("meal_bob").set(meal));

  const transaction = {
    groupId,
    cycleId: "cycle_1",
    memberUids: [ALICE_UID, BOB_UID],
    type: "BAZAAR",
    title: "Rice",
    amount: 100,
    paidByMemberId: "bob_member",
    dateEpochDay: 20768,
    createdByUid: BOB_UID,
    createdAt,
    updatedAt: pastTimestamp(),
  };
  await assertFails(bobGroupRef.collection("transactions").doc("tx_bob").set(transaction));

  const bobMemberAsBob = bobGroupRef.collection("members").doc("bob_member");
  const aliceMemberAsBob = bobGroupRef.collection("members").doc("alice_member");
  // Members cannot grant themselves or others edit access, whether by the member row,
  // the group editor list, or the exact two-document batch used by the app.
  await assertFails(bobMemberAsBob.update({ canEditMealsAndExpenses: true, updatedAt: pastTimestamp() }));
  await assertFails(aliceMemberAsBob.update({ canEditMealsAndExpenses: false, updatedAt: pastTimestamp() }));
  const unauthorizedBatch = bobDb.batch();
  unauthorizedBatch.update(bobMemberAsBob, { canEditMealsAndExpenses: true, updatedAt: pastTimestamp() });
  unauthorizedBatch.update(bobGroupRef, {
    editorUids: [ALICE_UID, BOB_UID], editPermissionsInitialized: true, updatedAt: pastTimestamp(),
  });
  await assertFails(unauthorizedBatch.commit());

  // The manager grants permission with the same atomic member+group update used by the app.
  const managerBatch = aliceDb.batch();
  managerBatch.update(groupRef.collection("members").doc("bob_member"), {
    canEditMealsAndExpenses: true, updatedAt: pastTimestamp(),
  });
  managerBatch.update(groupRef, {
    editorUids: [ALICE_UID, BOB_UID], editPermissionsInitialized: true, updatedAt: pastTimestamp(),
  });
  await assertSucceeds(managerBatch.commit());
  await assertSucceeds(bobGroupRef.collection("mealEntries").doc("meal_bob").set(meal));
  await assertSucceeds(bobGroupRef.collection("transactions").doc("tx_bob").set(transaction));

  // The manager is permanently an editor: direct member-row revocation and removal
  // from the group editor list are both rejected.
  const aliceMemberAsAlice = groupRef.collection("members").doc("alice_member");
  await assertFails(aliceMemberAsAlice.update({ canEditMealsAndExpenses: false, updatedAt: pastTimestamp() }));
  await assertFails(groupRef.update({ editorUids: [BOB_UID], updatedAt: pastTimestamp() }));
  const disableManagerBatch = aliceDb.batch();
  disableManagerBatch.update(aliceMemberAsAlice, { canEditMealsAndExpenses: false, updatedAt: pastTimestamp() });
  disableManagerBatch.update(groupRef, {
    editorUids: [BOB_UID], editPermissionsInitialized: true, updatedAt: pastTimestamp(),
  });
  await assertFails(disableManagerBatch.commit());

  // Even aside from the stored editor list, the manager can still make meal/expense edits.
  const managerMeal = { ...meal, memberId: "alice_member", updatedByUid: ALICE_UID };
  const managerTransaction = { ...transaction, paidByMemberId: "alice_member", createdByUid: ALICE_UID };
  await assertSucceeds(groupRef.collection("mealEntries").doc("meal_alice").set(managerMeal));
  await assertSucceeds(groupRef.collection("transactions").doc("tx_alice").set(managerTransaction));

  // The manager can revoke another member using the app's atomic two-document update.
  const revokeBatch = aliceDb.batch();
  revokeBatch.update(groupRef.collection("members").doc("bob_member"), {
    canEditMealsAndExpenses: false, updatedAt: pastTimestamp(),
  });
  revokeBatch.update(groupRef, {
    editorUids: [ALICE_UID], editPermissionsInitialized: true, updatedAt: pastTimestamp(),
  });
  await assertSucceeds(revokeBatch.commit());
  await assertFails(bobGroupRef.collection("mealEntries").doc("meal_bob_after_revoke").set(meal));
  await assertFails(bobGroupRef.collection("transactions").doc("tx_bob_after_revoke").set(transaction));
});
