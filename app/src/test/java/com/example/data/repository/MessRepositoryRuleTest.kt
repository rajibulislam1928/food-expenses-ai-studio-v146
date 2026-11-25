package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CycleMode
import com.example.data.model.MessGroup
import com.example.data.model.MessMember
import com.example.data.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun managerReset_clearsOperationalRecordsAndKeepsMemberPhotos() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val uid = signInTestUser("reset_$suffix@test.com")
    val repo = MessRepository(firestore, auth)
    val groupId = repo.createMessGroup(
      name = "Reset Test $suffix",
      currencySymbol = "₹",
      cycleMode = CycleMode.WEEKLY,
      creatorMemberName = "Manager",
      initialMemberNames = listOf("Rahim"),
    ).getOrThrow()
    val groupRef = firestore.collection("messGroups").document(groupId)
    val group = groupRef.get().await().toObject(MessGroup::class.java)!!
    val managerMember = groupRef.collection("members")
      .whereArrayContains("memberUids", uid).whereEqualTo("linkedUid", uid).get().await()
      .documents.first().toObject(MessMember::class.java)!!
    val currentCycleId = group.activeCycleId
    repo.updateMyMemberPhoto(groupId, managerMember, "cGhvdG8=").getOrThrow()

    repo.upsertMealEntry(
      group = group,
      cycleId = currentCycleId,
      memberId = managerMember.id,
      dateEpochDay = LocalDate.now().toEpochDay(),
      lunch = 1.0,
      dinner = 1.0,
      guestMeals = 0.0,
      targetMemberName = managerMember.name,
      actorName = managerMember.name,
    ).getOrThrow()
    repo.addTransaction(
      group = group,
      cycleId = currentCycleId,
      type = TransactionType.BAZAAR,
      title = "Rice",
      notes = "",
      amount = 50.0,
      paidByMemberId = managerMember.id,
      dateEpochDay = LocalDate.now().toEpochDay(),
      actorName = managerMember.name,
      targetMemberName = managerMember.name,
    ).getOrThrow()
    repo.resetGroupData(groupId).getOrThrow()
    val savedGroup = groupRef.get().await().toObject(MessGroup::class.java)!!
    assertEquals(group.name, savedGroup.name)
    assertEquals(group.memberUids, savedGroup.memberUids)
    assertEquals("৳", savedGroup.currencySymbol)
    assertEquals(CycleMode.MONTHLY.name, savedGroup.cycleMode)
    assertEquals(6, savedGroup.weekStartDay)
    assertEquals(-1L, savedGroup.customWeekStartEpochDay)
    assertTrue(savedGroup.activeCycleId != currentCycleId)
    assertEquals(0, groupRef.collection("mealEntries").whereArrayContains("memberUids", uid).get().await().size())
    assertEquals(0, groupRef.collection("transactions").whereArrayContains("memberUids", uid).get().await().size())
    assertEquals(0, groupRef.collection("activityLogs").whereArrayContains("memberUids", uid).get().await().size())
    val cycles = groupRef.collection("cycles").whereArrayContains("memberUids", uid).get().await()
    assertEquals(1, cycles.size())
    assertEquals("ACTIVE", cycles.documents.single().getString("status"))
    val members = groupRef.collection("members").whereArrayContains("memberUids", uid).get().await()
    assertEquals(2, members.size())
    assertEquals("cGhvdG8=", members.documents.first { it.getString("linkedUid") == uid }.getString("avatarBase64"))
  }

  @Test
  fun createMessGroup_authenticatedUser_createsGroupCycleAndMember() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val uid = signInTestUser("alice_$suffix@test.com")
    val repo = MessRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice", "alice@test.com").getOrThrow() }
    val groupId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createMessGroup(
            name = "Emerald Hostel $suffix",
            currencySymbol = "৳",
            cycleMode = CycleMode.MONTHLY,
            creatorMemberName = "Alice",
            initialMemberNames = listOf("Rahim", "Karim"),
          )
          .getOrThrow()
      }

    val groups =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserGroups(uid).first { list -> list.any { it.id == groupId } }
      }
    assertTrue(groups.any { it.id == groupId && it.ownerUid == uid })

    val members =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupMembers(groupId, uid).first { it.size >= 3 }
      }
    assertEquals(3, members.size)
  }

  @Test
  fun joinByInviteCode_crossUserIsolationAndJoinFlow_succeedsForInvitedUser() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("alice_join_$suffix@test.com")
    val aliceRepo = MessRepository(firestore, auth)

    val groupId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createMessGroup(
            name = "North Wing $suffix",
            currencySymbol = "৳",
            cycleMode = CycleMode.WEEKLY,
            creatorMemberName = "Alice",
          )
          .getOrThrow()
      }

    val group =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeUserGroups(aliceUid).first { it.any { g -> g.id == groupId } }
      }.first { it.id == groupId }

    // Charlie (uninvited) cannot observe Alice's group cycles
    val charlieUid = signInTestUser("charlie_$suffix@test.com")
    val charlieRepo = MessRepository(firestore, auth)
    val charlieGroups =
      withTimeout(FLOW_TIMEOUT_MS) { charlieRepo.observeUserGroups(charlieUid).first() }
    assertTrue(charlieGroups.none { it.id == groupId })

    // Bob cannot read the group before joining, but the invite flow must still succeed.
    val bobUid = signInTestUser("bob_$suffix@test.com")
    val groupRef = firestore.collection("messGroups").document(groupId)
    val preJoinRead = runCatching { groupRef.get(Source.SERVER).await() }
    assertTrue("A non-member must not read group data before joining", preJoinRead.isFailure)
    val bobRepo = MessRepository(firestore, auth)
    val joinedId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.joinMessGroupByInviteCode(group.inviteCode, "Bob").getOrThrow()
      }
    assertEquals(groupId, joinedId)

    val bobGroups =
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeUserGroups(bobUid).first { it.any { g -> g.id == groupId } }
      }
    assertTrue(bobGroups.any { it.id == groupId && bobUid in it.memberUids })
    val joinedGroupSnapshot = groupRef.get(Source.SERVER).await()
    val joinedGroup = joinedGroupSnapshot.toObject(MessGroup::class.java)!!
    assertTrue(aliceUid in joinedGroup.editorUids)
    assertTrue(bobUid in joinedGroup.editorUids)
    assertTrue(joinedGroup.editPermissionsInitialized)
  }

  @Test
  fun upsertMealAndTransaction_authenticatedMember_persistsAndEmits() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val uid = signInTestUser("meals_$suffix@test.com")
    val repo = MessRepository(firestore, auth)

    val groupId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createMessGroup(
            name = "Bazaar Mess $suffix",
            currencySymbol = "৳",
            cycleMode = CycleMode.MONTHLY,
            creatorMemberName = "Alice",
          )
          .getOrThrow()
      }

    val group =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserGroups(uid).first { it.any { g -> g.id == groupId } }
      }.first { it.id == groupId }

    val members =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupMembers(groupId, uid).first { it.isNotEmpty() }
      }
    val memberId = members.first().id
    val today = LocalDate.now().toEpochDay()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo
        .upsertMealEntry(
          group = group,
          cycleId = group.activeCycleId,
          memberId = memberId,
          dateEpochDay = today,
          lunch = 1.0,
          dinner = 1.0,
          guestMeals = 1.0,
        )
        .getOrThrow()

      repo
        .addTransaction(
          group = group,
          cycleId = group.activeCycleId,
          type = TransactionType.BAZAAR,
          title = "Chicken & Rice",
          notes = "5kg Miniket + 2kg Chicken",
          amount = 950.0,
          paidByMemberId = memberId,
          dateEpochDay = today,
        )
        .getOrThrow()
    }

    val meals =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupMealEntries(groupId, uid).first { it.isNotEmpty() }
      }
    assertEquals(3.0, meals.first().totalMeals(), 0.001)

    val txs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupTransactions(groupId, uid).first { it.isNotEmpty() }
      }
    assertEquals(950.0, txs.first().amount, 0.001)
  }

  @Test
  fun mealEdit_offlineCacheMissFailsClearlyWithoutWriting() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val uid = signInTestUser("offline_meal_$suffix@test.com")
    val repo = MessRepository(firestore, auth)
    val groupId = "offline_group_$suffix"
    val groupRef = firestore.collection("messGroups").document(groupId)
    val inviteCode = "O${suffix.take(5).uppercase()}"
    val now = com.google.firebase.Timestamp.now()
    groupRef.set(
      mapOf(
        "name" to "Offline cache test",
        "ownerUid" to uid,
        "memberUids" to listOf(uid),
        "inviteCode" to inviteCode,
        "currencySymbol" to "৳",
        "cycleMode" to CycleMode.MONTHLY.name,
        "activeCycleId" to "cycle_$suffix",
        "createdAt" to now,
        "updatedAt" to now,
      )
    ).await()
    val group = groupRef.get(Source.SERVER).await().toObject(MessGroup::class.java)!!
    val date = LocalDate.now().toEpochDay()
    val memberId = "member_$suffix"
    val entryId = "${group.activeCycleId}_${date}_$memberId"
    val mealRef = firestore.collection("messGroups").document(group.id)
      .collection("mealEntries").document(entryId)

    firestore.disableNetwork().await()
    try {
      val result =
        repo.upsertMealEntry(
          group = group,
          cycleId = group.activeCycleId,
          memberId = memberId,
          dateEpochDay = date,
          lunch = 1.0,
          dinner = 0.0,
          guestMeals = 0.0,
        )
      assertTrue(result.isFailure)
      val failure = result.exceptionOrNull()
      assertTrue(
        "Unexpected offline failure: ${failure?.javaClass?.simpleName}: ${failure?.message}",
        failure?.message?.contains("isn't in the offline cache") == true,
      )
    } finally {
      firestore.enableNetwork().await()
    }

    assertFalse(mealRef.get(Source.SERVER).await().exists())
  }

  @Test
  fun applyDayPresetToMembers_mixedCreateAndUpdate_succeedsWithoutPermissionDenied() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("preset_alice_$suffix@test.com")
    val aliceRepo = MessRepository(firestore, auth)

    val groupId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createMessGroup(
            name = "Preset Mess $suffix",
            currencySymbol = "৳",
            cycleMode = CycleMode.MONTHLY,
            creatorMemberName = "Alice",
            initialMemberNames = listOf("Rahim"),
          )
          .getOrThrow()
      }

    val group =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeUserGroups(aliceUid).first { it.any { g -> g.id == groupId } }
      }.first { it.id == groupId }

    val members =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeGroupMembers(groupId, aliceUid).first { it.size >= 2 }
      }
    val today = LocalDate.now().toEpochDay()

    // Create meal entry for first member only
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo
        .upsertMealEntry(
          group = group,
          cycleId = group.activeCycleId,
          memberId = members.first().id,
          dateEpochDay = today,
          lunch = 1.0,
          dinner = 0.0,
          guestMeals = 0.0,
        )
        .getOrThrow()
    }

    // Apply day preset across all members (mix of existing + new)
    val presetEntries =
      members.map { m ->
        com.example.data.model.MealEntry(
          groupId = groupId,
          cycleId = group.activeCycleId,
          memberId = m.id,
          dateEpochDay = today,
          lunch = 1.0,
          dinner = 1.0,
          guestMeals = 0.0,
        )
      }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo
        .applyDayPresetToMembers(group, group.activeCycleId, today, presetEntries)
        .getOrThrow()
      // Second run (all existing docs) must also succeed
      aliceRepo
        .applyDayPresetToMembers(
          group,
          group.activeCycleId,
          today,
          presetEntries.map { it.copy(guestMeals = 1.0) },
        )
        .getOrThrow()
    }

    val savedMeals =
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeGroupMealEntries(groupId, aliceUid).first { entries ->
          entries.size == 2 && entries.all { it.guestMeals == 1.0 }
        }
      }
    assertEquals(2, savedMeals.size)

    // Newly joined roommate Bob can update Alice's existing meal entry even before Alice propagates memberUids
    signInTestUser("preset_bob_$suffix@test.com")
    val bobRepo = MessRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.joinMessGroupByInviteCode(group.inviteCode, "Bob").getOrThrow()
    }
    val bobUid = auth.currentUser!!.uid
    val bobGroup =
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeUserGroups(bobUid).first { it.any { g -> g.id == groupId } }
      }.first { it.id == groupId }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo
        .applyDayPresetToMembers(
          bobGroup,
          bobGroup.activeCycleId,
          today,
          presetEntries.map { it.copy(dinner = 2.0) },
        )
        .getOrThrow()
    }
  }

  @Test
  fun activityLogs_mealsAndExpenses_recordActorAndChangeDetails() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("act_alice_$suffix@test.com")
    val repo = MessRepository(firestore, auth)

    val groupId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .createMessGroup(
            name = "Activity Mess $suffix",
            currencySymbol = "৳",
            cycleMode = CycleMode.MONTHLY,
            creatorMemberName = "Alice",
            initialMemberNames = listOf("Rahim"),
          )
          .getOrThrow()
      }

    val group =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserGroups(aliceUid).first { it.any { g -> g.id == groupId } }
      }.first { it.id == groupId }

    val members =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupMembers(groupId, aliceUid).first { it.size >= 2 }
      }
    val rahim = members.first { it.name == "Rahim" }
    val today = LocalDate.now().toEpochDay()

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo
        .upsertMealEntry(
          group = group,
          cycleId = group.activeCycleId,
          memberId = rahim.id,
          dateEpochDay = today,
          lunch = 1.0,
          dinner = 1.0,
          guestMeals = 0.0,
          targetMemberName = "Rahim",
          actorName = "Alice",
        )
        .getOrThrow()

      repo
        .upsertMealEntry(
          group = group,
          cycleId = group.activeCycleId,
          memberId = rahim.id,
          dateEpochDay = today,
          lunch = 2.0,
          dinner = 1.0,
          guestMeals = 1.0,
          targetMemberName = "Rahim",
          actorName = "Alice",
        )
        .getOrThrow()

      val txId =
        repo
          .addTransaction(
            group = group,
            cycleId = group.activeCycleId,
            type = TransactionType.BAZAAR,
            title = "Fish & Spices",
            notes = "",
            amount = 720.0,
            paidByMemberId = rahim.id,
            dateEpochDay = today,
            actorName = "Alice",
            targetMemberName = "Rahim",
          )
          .getOrThrow()

      repo
        .deleteTransaction(
          groupId = groupId,
          txId = txId,
          actorName = "Alice",
          currencySymbol = "৳",
        )
        .getOrThrow()
    }

    val logs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeGroupActivityLogs(groupId, aliceUid).first { it.size >= 4 }
      }

    assertTrue(logs.all { it.actorName == "Alice" && it.actorUid == aliceUid })
    assertTrue(logs.any { it.category == "MEAL" && it.action == "UPDATED" && it.changeDetail.contains("Lunch 1 → 2") })
    assertTrue(logs.any { it.category == "EXPENSE" && it.action == "ADDED" && it.title == "Fish & Spices" })
    assertTrue(logs.any { it.category == "EXPENSE" && it.action == "REMOVED" && it.title == "Fish & Spices" })
  }

  @Test
  fun permanentlyDeleteMember_removesOnlyTargetHistoryAndKeepsGroupAndOthers() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val uid = signInTestUser("purge_$suffix@test.com")
    val repo = MessRepository(firestore, auth)
    val groupId = repo.createMessGroup(
      name = "Purge Test $suffix",
      currencySymbol = "₹",
      cycleMode = CycleMode.MONTHLY,
      creatorMemberName = "Manager",
      initialMemberNames = listOf("Rahim"),
    ).getOrThrow()
    val groupRef = firestore.collection("messGroups").document(groupId)
    val group = groupRef.get(Source.SERVER).await().toObject(MessGroup::class.java)!!
    val members = groupRef.collection("members").get(Source.SERVER).await().documents
      .mapNotNull { it.toObject(MessMember::class.java) }
    val manager = members.first { it.linkedUid == uid }
    val target = members.first { it.name == "Rahim" }
    val today = LocalDate.now().toEpochDay()

    repo.upsertMealEntry(group, group.activeCycleId, target.id, today, 1.0, 0.0, 0.0,
      targetMemberName = target.name, actorName = manager.name).getOrThrow()
    repo.upsertMealEntry(group, group.activeCycleId, manager.id, today, 0.0, 1.0, 0.0,
      targetMemberName = manager.name, actorName = manager.name).getOrThrow()
    repo.addTransaction(group, group.activeCycleId, TransactionType.BAZAAR, "Rahim rice", "", 100.0,
      target.id, today, actorName = manager.name, targetMemberName = target.name).getOrThrow()
    repo.addTransaction(group, group.activeCycleId, TransactionType.BAZAAR, "Manager vegetables", "", 50.0,
      manager.id, today, actorName = manager.name, targetMemberName = manager.name).getOrThrow()

    val deletedRecords = repo.permanentlyDeleteMember(group, target).getOrThrow()

    assertTrue("Expected target meals, transaction, and attributed activity history to be deleted", deletedRecords >= 4)
    assertEquals(group.name, groupRef.get(Source.SERVER).await().getString("name"))
    assertEquals(listOf(uid), groupRef.get(Source.SERVER).await().get("memberUids"))
    assertFalse(groupRef.collection("members").document(target.id).get(Source.SERVER).await().exists())
    assertEquals(0, groupRef.collection("mealEntries").whereEqualTo("memberId", target.id).get(Source.SERVER).await().size())
    assertEquals(1, groupRef.collection("mealEntries").whereEqualTo("memberId", manager.id).get(Source.SERVER).await().size())
    assertEquals(0, groupRef.collection("transactions").whereEqualTo("paidByMemberId", target.id).get(Source.SERVER).await().size())
    assertEquals(1, groupRef.collection("transactions").whereEqualTo("paidByMemberId", manager.id).get(Source.SERVER).await().size())
    assertEquals(0, groupRef.collection("activityLogs").whereEqualTo("targetMemberName", target.name).get(Source.SERVER).await().size())
    assertTrue(groupRef.collection("activityLogs").whereEqualTo("targetMemberName", manager.name).get(Source.SERVER).await().size() > 0)
  }

  @Test
  fun observeUserGroups_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = MessRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserGroups("unauth_uid").first() }
    } catch (t: Throwable) {
      caught = t
    }
    val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
