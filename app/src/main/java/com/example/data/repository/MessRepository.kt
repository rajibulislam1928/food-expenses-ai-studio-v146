package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.export.ImportedWorkbookData
import com.example.data.handleFirestoreError
import com.example.data.model.ActivityAction
import com.example.data.model.ActivityCategory
import com.example.data.model.CycleMode
import com.example.data.model.CycleStatus
import com.example.data.model.MealEntry
import com.example.data.model.MemberRole
import com.example.data.model.MessActivityLog
import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import com.example.data.model.MessInvite
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import com.example.data.model.UserProfile
import com.example.domain.BulkBazaarItem
import com.example.domain.MessCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class MessRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private val mealWriteMutex = Mutex()

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
    val docRef = db.collection("users").document(uid)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.GET, docRef.path)
          }
          throw error
        }
    )
  }

  suspend fun ensureUserProfile(
    displayName: String,
    email: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val cleanName = displayName.ifBlank { email.substringBefore("@").ifBlank { "Mess Member" } }.take(80)
    val cleanEmail = email.take(120)
    try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val payload =
          mapOf(
            "userId" to uid,
            "displayName" to cleanName,
            "email" to cleanEmail,
            "activeGroupId" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
      } else {
        val payload =
          mapOf(
            "displayName" to cleanName,
            "email" to cleanEmail,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload, SetOptions.merge()).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun setActiveGroupId(groupId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      docRef.update(mapOf("activeGroupId" to groupId, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observeUserGroups(uid: String): Flow<List<MessGroup>> = flow {
    val path = "messGroups"
    emitAll(
      db.collection(path)
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(MessGroup::class.java, ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeGroupCycles(groupId: String, uid: String): Flow<List<MessCycle>> = flow {
    val path = "messGroups/$groupId/cycles"
    emitAll(
      db.collection("messGroups")
        .document(groupId)
        .collection("cycles")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(MessCycle::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.startDateEpochDay }
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeGroupMembers(groupId: String, uid: String): Flow<List<MessMember>> = flow {
    val path = "messGroups/$groupId/members"
    emitAll(
      db.collection("messGroups")
        .document(groupId)
        .collection("members")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(MessMember::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<MessMember> { it.role != MemberRole.MANAGER.name }.thenBy { it.name })
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeGroupMealEntries(groupId: String, uid: String): Flow<List<MealEntry>> = flow {
    val path = "messGroups/$groupId/mealEntries"
    emitAll(
      db.collection("messGroups")
        .document(groupId)
        .collection("mealEntries")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(MealEntry::class.java, ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeGroupTransactions(groupId: String, uid: String): Flow<List<MessTransaction>> = flow {
    val path = "messGroups/$groupId/transactions"
    emitAll(
      db.collection("messGroups")
        .document(groupId)
        .collection("transactions")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(MessTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.dateEpochDay }
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeGroupActivityLogs(groupId: String, uid: String): Flow<List<MessActivityLog>> = flow {
    val path = "messGroups/$groupId/activityLogs"
    emitAll(
      db.collection("messGroups")
        .document(groupId)
        .collection("activityLogs")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(MessActivityLog::class.java, ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareByDescending<MessActivityLog> { it.createdAtMillis() }
                .thenByDescending { it.dateEpochDay }
            )
            .take(150)
        }
        .catch { error ->
          if (error is CancellationException && error.cause == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun fetchRecentActivityLogsOnce(uid: String): Result<List<Pair<MessGroup, List<MessActivityLog>>>> =
    runCatching {
      if (auth.currentUser?.uid != uid) return@runCatching emptyList()
      val groupsSnap =
        try {
          db.collection("messGroups")
            .whereArrayContains("memberUids", uid)
            .get(Source.SERVER)
            .await()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          db.collection("messGroups").whereArrayContains("memberUids", uid).get().await()
        }
      val groups = groupsSnap.toObjects(MessGroup::class.java, ServerTimestampBehavior.ESTIMATE)
      coroutineScope {
        groups
          .map { group ->
            async {
              val logsCol =
                db.collection("messGroups").document(group.id).collection("activityLogs")
              val logsSnap =
                try {
                  logsCol.whereArrayContains("memberUids", uid).get(Source.SERVER).await()
                } catch (e: CancellationException) {
                  throw e
                } catch (_: Exception) {
                  logsCol.whereArrayContains("memberUids", uid).get().await()
                }
              val logs =
                logsSnap
                  .toObjects(MessActivityLog::class.java, ServerTimestampBehavior.ESTIMATE)
                  .sortedWith(
                    compareByDescending<MessActivityLog> { it.createdAtMillis() }
                      .thenByDescending { it.dateEpochDay }
                  )
                  .take(40)
              group to logs
            }
          }
          .awaitAll()
      }
    }

  suspend fun createMessGroup(
    name: String,
    currencySymbol: String,
    cycleMode: CycleMode,
    creatorMemberName: String,
    initialMemberNames: List<String> = emptyList(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val groupRef = db.collection("messGroups").document()
    val cycleRef = groupRef.collection("cycles").document()
    val managerRef = groupRef.collection("members").document()
    val inviteCode = generateInviteCode()
    val inviteRef = db.collection("messInvites").document(inviteCode)

    val (label, startDay, endDay) = defaultCycleBounds(cycleMode, LocalDate.now())
    val cleanGroupName = name.trim().take(80)
    require(cleanGroupName.isNotEmpty()) { "Mess name cannot be empty." }

    val batch = db.batch()
    batch.set(
      groupRef,
      mapOf(
        "name" to cleanGroupName,
        "ownerUid" to uid,
        "memberUids" to listOf(uid),
        "inviteCode" to inviteCode,
        "currencySymbol" to currencySymbol.ifBlank { "৳" }.take(6),
        "cycleMode" to cycleMode.name,
        "activeCycleId" to cycleRef.id,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.set(
      inviteRef,
      mapOf(
        "inviteCode" to inviteCode,
        "groupId" to groupRef.id,
        "groupName" to cleanGroupName,
        "ownerUid" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.set(
      cycleRef,
      mapOf(
        "groupId" to groupRef.id,
        "memberUids" to listOf(uid),
        "label" to label,
        "cycleType" to cycleMode.name,
        "startDateEpochDay" to startDay,
        "endDateEpochDay" to endDay,
        "status" to CycleStatus.ACTIVE.name,
        "createdByUid" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.set(
      managerRef,
      mapOf(
        "groupId" to groupRef.id,
        "memberUids" to listOf(uid),
        "name" to creatorMemberName.trim().ifBlank { "Manager" }.take(60),
        "linkedUid" to uid,
        "role" to MemberRole.MANAGER.name,
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    initialMemberNames
      .map { it.trim() }
      .filter { it.isNotBlank() && !it.equals(creatorMemberName.trim(), ignoreCase = true) }
      .distinctBy { it.lowercase() }
      .take(25)
      .forEach { roommateName ->
        val memberRef = groupRef.collection("members").document()
        batch.set(
          memberRef,
          mapOf(
            "groupId" to groupRef.id,
            "memberUids" to listOf(uid),
            "name" to roommateName.take(60),
            "linkedUid" to "",
            "role" to MemberRole.MEMBER.name,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
    val userRef = db.collection("users").document(uid)
    val userExists = userRef.get().await().exists()
    if (userExists) {
      batch.set(
        userRef,
        mapOf("activeGroupId" to groupRef.id, "updatedAt" to FieldValue.serverTimestamp()),
        SetOptions.merge(),
      )
    } else {
      val currentUser = auth.currentUser
      batch.set(
        userRef,
        mapOf(
          "userId" to uid,
          "displayName" to creatorMemberName.trim().ifBlank { currentUser?.displayName ?: "Mess Member" }.take(80),
          "email" to (currentUser?.email ?: "").take(120),
          "activeGroupId" to groupRef.id,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
    }

    try {
      batch.commit().await()
      groupRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, groupRef.path)
      throw e
    }
  }

  suspend fun joinMessGroupByInviteCode(
    rawInviteCode: String,
    memberDisplayName: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val code = rawInviteCode.trim().uppercase(Locale.US)
    require(code.matches(Regex("^[A-Z0-9]{6}$"))) {
      "Invite code must be 6 letters or digits."
    }

    val inviteRef = db.collection("messInvites").document(code)
    val inviteSnap =
      try {
        inviteRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, inviteRef.path)
        throw e
      }

    if (!inviteSnap.exists()) {
      throw IllegalArgumentException("No mess group found for invite code $code.")
    }
    val invite = inviteSnap.toObject(MessInvite::class.java)
      ?: throw IllegalStateException("Invalid invite record.")
    val groupRef = db.collection("messGroups").document(invite.groupId)

    try {
      groupRef
        .update(
          mapOf(
            "memberUids" to FieldValue.arrayUnion(uid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      val updatedGroup =
        groupRef.get().await().toObject(MessGroup::class.java, ServerTimestampBehavior.ESTIMATE)
          ?: throw IllegalStateException("Unable to load joined mess group.")

      val existingMembers =
        groupRef
          .collection("members")
          .whereArrayContains("memberUids", uid)
          .get()
          .await()
          .toObjects(MessMember::class.java)

      val alreadyLinked = existingMembers.any { it.linkedUid == uid }
      val batch = db.batch()
      if (!alreadyLinked) {
        val memberRef = groupRef.collection("members").document()
        batch.set(
          memberRef,
          mapOf(
            "groupId" to invite.groupId,
            "memberUids" to updatedGroup.memberUids,
            "name" to memberDisplayName.trim().ifBlank { "Member" }.take(60),
            "linkedUid" to uid,
            "role" to MemberRole.MEMBER.name,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      val userRef = db.collection("users").document(uid)
      val userExists = userRef.get().await().exists()
      if (userExists) {
        batch.set(
          userRef,
          mapOf("activeGroupId" to invite.groupId, "updatedAt" to FieldValue.serverTimestamp()),
          SetOptions.merge(),
        )
      } else {
        val currentUser = auth.currentUser
        batch.set(
          userRef,
          mapOf(
            "userId" to uid,
            "displayName" to memberDisplayName.trim().ifBlank { currentUser?.displayName ?: "Member" }.take(80),
            "email" to (currentUser?.email ?: "").take(120),
            "activeGroupId" to invite.groupId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      invite.groupId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
      throw e
    }
  }

  suspend fun propagateMemberUidsIfNeeded(
    group: MessGroup,
    cycles: List<MessCycle>,
    members: List<MessMember>,
    meals: List<MealEntry>,
    transactions: List<MessTransaction>,
    activityLogs: List<MessActivityLog> = emptyList(),
  ) {
    val targetUids = group.memberUids
    if (targetUids.isEmpty()) return
    val targetSet = targetUids.toSet()

    val refsWithTimestamp = mutableListOf<com.google.firebase.firestore.DocumentReference>()
    val activityLogRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
    val groupRef = db.collection("messGroups").document(group.id)

    cycles.filter { it.memberUids.toSet() != targetSet }.forEach {
      refsWithTimestamp.add(groupRef.collection("cycles").document(it.id))
    }
    members.filter { it.memberUids.toSet() != targetSet }.forEach {
      refsWithTimestamp.add(groupRef.collection("members").document(it.id))
    }
    meals.filter { it.memberUids.toSet() != targetSet }.forEach {
      refsWithTimestamp.add(groupRef.collection("mealEntries").document(it.id))
    }
    transactions.filter { it.memberUids.toSet() != targetSet }.forEach {
      refsWithTimestamp.add(groupRef.collection("transactions").document(it.id))
    }
    activityLogs.filter { it.memberUids.toSet() != targetSet }.forEach {
      activityLogRefs.add(groupRef.collection("activityLogs").document(it.id))
    }

    refsWithTimestamp.chunked(350).forEach { chunk ->
      val batch = db.batch()
      chunk.forEach { ref ->
        batch.update(
          ref,
          mapOf("memberUids" to targetUids, "updatedAt" to FieldValue.serverTimestamp()),
        )
      }
      runCatching { batch.commit().await() }.onFailure { err ->
        if (err is CancellationException) throw err
        handleFirestoreError(err, OperationType.UPDATE, "${groupRef.path}/sync")
      }
    }

    activityLogRefs.chunked(350).forEach { chunk ->
      val batch = db.batch()
      chunk.forEach { ref ->
        batch.update(ref, mapOf("memberUids" to targetUids))
      }
      runCatching { batch.commit().await() }.onFailure { err ->
        if (err is CancellationException) throw err
        handleFirestoreError(err, OperationType.UPDATE, "${groupRef.path}/activityLogsSync")
      }
    }
  }

  suspend fun addMember(
    group: MessGroup,
    name: String,
    role: MemberRole = MemberRole.MEMBER,
  ): Result<String> = runCatching {
    requireUserId()
    val cleanName = name.trim().take(60)
    require(cleanName.isNotEmpty()) { "Member name cannot be empty." }
    val memberRef = db.collection("messGroups").document(group.id).collection("members").document()
    val payload =
      mapOf(
        "groupId" to group.id,
        "memberUids" to group.memberUids,
        "name" to cleanName,
        "linkedUid" to "",
        "role" to role.name,
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      memberRef.set(payload).await()
      memberRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, memberRef.path)
      throw e
    }
  }

  suspend fun updateMyMemberPhoto(
    groupId: String,
    member: MessMember,
    avatarBase64: String?,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(member.linkedUid == uid) { "You can only update your own member profile photo." }
    require(avatarBase64 == null || avatarBase64.length <= 90_000) { "Photo is too large. Choose a smaller image." }
    val memberRef = db.collection("messGroups").document(groupId).collection("members").document(member.id)
    val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
    if (avatarBase64 == null) updates["avatarBase64"] = FieldValue.delete()
    else updates["avatarBase64"] = avatarBase64
    try {
      memberRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
      throw e
    }
  }

  suspend fun updateMemberActive(
    groupId: String,
    member: MessMember,
    isActive: Boolean,
  ): Result<Unit> = runCatching {
    requireUserId()
    val memberRef =
      db.collection("messGroups").document(groupId).collection("members").document(member.id)
    try {
      memberRef
        .update(mapOf("isActive" to isActive, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
      throw e
    }
  }

  private fun resolveSafeActorName(actorName: String): String {
    val fromParam = actorName.trim()
    val fromAuth = auth.currentUser?.displayName?.trim().orEmpty()
    return fromParam.ifBlank { fromAuth }.ifBlank { "Roommate" }.take(80)
  }

  private fun buildActivityLogPayload(
    groupId: String,
    cycleId: String,
    memberUids: List<String>,
    category: ActivityCategory,
    action: ActivityAction,
    actorUid: String,
    actorName: String,
    targetMemberName: String,
    title: String,
    changeDetail: String,
    dateEpochDay: Long,
  ): Map<String, Any> =
    mapOf(
      "groupId" to groupId,
      "cycleId" to cycleId,
      "memberUids" to memberUids,
      "category" to category.name,
      "action" to action.name,
      "actorUid" to actorUid,
      "actorName" to resolveSafeActorName(actorName),
      "targetMemberName" to targetMemberName.trim().take(80),
      "title" to title.trim().ifBlank { category.label }.take(120),
      "changeDetail" to changeDetail.trim().ifBlank { action.label }.take(240),
      "dateEpochDay" to dateEpochDay,
      "createdAt" to FieldValue.serverTimestamp(),
    )

  suspend fun upsertMealEntry(
    group: MessGroup,
    cycleId: String,
    memberId: String,
    dateEpochDay: Long,
    lunch: Double,
    dinner: Double,
    guestMeals: Double,
    targetMemberName: String = "",
    actorName: String = "",
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val entryId = "${cycleId}_${dateEpochDay}_${memberId}"
    val groupRef = db.collection("messGroups").document(group.id)
    val docRef = groupRef.collection("mealEntries").document(entryId)
    val logRef = groupRef.collection("activityLogs").document()

    val safeL = lunch.coerceIn(0.0, 20.0)
    val safeD = dinner.coerceIn(0.0, 20.0)
    val safeG = guestMeals.coerceIn(0.0, 50.0)

    try {
      mealWriteMutex.withLock {
        val existing = docRef.get().await()
        val batch = db.batch()
        val cleanTarget = targetMemberName.trim().take(80)
        val mealTitle = if (cleanTarget.isNotBlank()) "Meals for $cleanTarget" else "Meal Entry"

        if (!existing.exists()) {
          val payload =
            mapOf(
              "groupId" to group.id,
              "cycleId" to cycleId,
              "memberUids" to group.memberUids,
              "memberId" to memberId,
              "dateEpochDay" to dateEpochDay,
              "lunch" to safeL,
              "dinner" to safeD,
              "guestMeals" to safeG,
              "updatedByUid" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          batch.set(docRef, payload)
          batch.set(
            logRef,
            buildActivityLogPayload(
              groupId = group.id,
              cycleId = cycleId,
              memberUids = group.memberUids,
              category = ActivityCategory.MEAL,
              action = ActivityAction.ADDED,
              actorUid = uid,
              actorName = actorName,
              targetMemberName = cleanTarget,
              title = mealTitle,
              changeDetail = MessCalculator.formatMealValuesSummary(safeL, safeD, safeG),
              dateEpochDay = dateEpochDay,
            ),
          )
        } else {
          val prevL = existing.getDouble("lunch") ?: 0.0
          val prevD = existing.getDouble("dinner") ?: 0.0
          val prevG = existing.getDouble("guestMeals") ?: 0.0
          val changed =
            kotlin.math.abs(prevL - safeL) > 0.001 ||
              kotlin.math.abs(prevD - safeD) > 0.001 ||
              kotlin.math.abs(prevG - safeG) > 0.001

          batch.update(
            docRef,
            mapOf(
              "lunch" to safeL,
              "dinner" to safeD,
              "guestMeals" to safeG,
              "updatedByUid" to uid,
              "memberUids" to group.memberUids,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          if (changed) {
            batch.set(
              logRef,
              buildActivityLogPayload(
                groupId = group.id,
                cycleId = cycleId,
                memberUids = group.memberUids,
                category = ActivityCategory.MEAL,
                action = ActivityAction.UPDATED,
                actorUid = uid,
                actorName = actorName,
                targetMemberName = cleanTarget,
                title = mealTitle,
                changeDetail = MessCalculator.formatMealDiff(prevL, prevD, prevG, safeL, safeD, safeG),
                dateEpochDay = dateEpochDay,
              ),
            )
          }
        }
        batch.commit().await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun applyDayPresetToMembers(
    group: MessGroup,
    cycleId: String,
    targetDateEpochDay: Long,
    entriesToWrite: List<MealEntry>,
    memberNamesById: Map<String, String> = emptyMap(),
    actorName: String = "",
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    if (entriesToWrite.isEmpty()) return@runCatching
    val groupRef = db.collection("messGroups").document(group.id)
    val colRef = groupRef.collection("mealEntries")
    try {
      mealWriteMutex.withLock {
        val existingByEntryId = coroutineScope {
          entriesToWrite
            .map { entry ->
              val entryId = "${cycleId}_${targetDateEpochDay}_${entry.memberId}"
              async {
                val snap = colRef.document(entryId).get().await()
                entryId to snap
              }
            }
            .awaitAll()
            .toMap()
        }

        val firstSample = entriesToWrite.first()
        val summaryDetail =
          buildString {
            append(
              MessCalculator.formatMealValuesSummary(
                firstSample.lunch.coerceIn(0.0, 20.0),
                firstSample.dinner.coerceIn(0.0, 20.0),
                firstSample.guestMeals.coerceIn(0.0, 50.0),
              )
            )
            append(" (${entriesToWrite.size} ")
            append(if (entriesToWrite.size == 1) "member)" else "members)")
          }
        val firstMemberName = memberNamesById[firstSample.memberId].orEmpty()
        val presetTitle =
          if (entriesToWrite.size == 1 && firstMemberName.isNotBlank()) {
            "Meals for $firstMemberName"
          } else {
            "Meal preset applied (${entriesToWrite.size} members)"
          }

        entriesToWrite.chunked(350).forEachIndexed { chunkIdx, chunk ->
          val batch = db.batch()
          chunk.forEach { entry ->
            val entryId = "${cycleId}_${targetDateEpochDay}_${entry.memberId}"
            val docRef = colRef.document(entryId)
            val safeL = entry.lunch.coerceIn(0.0, 20.0)
            val safeD = entry.dinner.coerceIn(0.0, 20.0)
            val safeG = entry.guestMeals.coerceIn(0.0, 50.0)

            if (existingByEntryId[entryId]?.exists() == true) {
              batch.update(
                docRef,
                mapOf(
                  "lunch" to safeL,
                  "dinner" to safeD,
                  "guestMeals" to safeG,
                  "updatedByUid" to uid,
                  "memberUids" to group.memberUids,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            } else {
              batch.set(
                docRef,
                mapOf(
                  "groupId" to group.id,
                  "cycleId" to cycleId,
                  "memberUids" to group.memberUids,
                  "memberId" to entry.memberId,
                  "dateEpochDay" to targetDateEpochDay,
                  "lunch" to safeL,
                  "dinner" to safeD,
                  "guestMeals" to safeG,
                  "updatedByUid" to uid,
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            }
          }
          if (chunkIdx == 0) {
            val anyExisting = chunk.any { entry ->
              val entryId = "${cycleId}_${targetDateEpochDay}_${entry.memberId}"
              existingByEntryId[entryId]?.exists() == true
            }
            val logRef = groupRef.collection("activityLogs").document()
            batch.set(
              logRef,
              buildActivityLogPayload(
                groupId = group.id,
                cycleId = cycleId,
                memberUids = group.memberUids,
                category = ActivityCategory.MEAL,
                action = if (anyExisting) ActivityAction.UPDATED else ActivityAction.ADDED,
                actorUid = uid,
                actorName = actorName,
                targetMemberName = if (entriesToWrite.size == 1) firstMemberName else "",
                title = presetTitle,
                changeDetail = summaryDetail,
                dateEpochDay = targetDateEpochDay,
              ),
            )
          }
          batch.commit().await()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, colRef.path)
      throw e
    }
  }

  suspend fun addTransaction(
    group: MessGroup,
    cycleId: String,
    type: TransactionType,
    title: String,
    notes: String,
    amount: Double,
    paidByMemberId: String,
    dateEpochDay: Long,
    actorName: String = "",
    targetMemberName: String = "",
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanTitle = title.trim().take(100)
    require(cleanTitle.isNotEmpty()) { "Title cannot be empty." }
    require(amount > 0.0 && amount <= 10_000_000.0) { "Amount must be greater than zero." }
    val groupRef = db.collection("messGroups").document(group.id)
    val txRef = groupRef.collection("transactions").document()
    val logRef = groupRef.collection("activityLogs").document()
    val currency = group.currencySymbol.ifBlank { "৳" }
    val payerLabel = targetMemberName.trim().take(80)
    val changeDetail =
      buildString {
        append("$currency${MessCalculator.formatMeals(amount)} • ${type.label}")
        if (payerLabel.isNotBlank()) {
          append(" • Paid by $payerLabel")
        }
      }

    val batch = db.batch()
    batch.set(
      txRef,
      mapOf(
        "groupId" to group.id,
        "cycleId" to cycleId,
        "memberUids" to group.memberUids,
        "type" to type.name,
        "title" to cleanTitle,
        "notes" to notes.trim().take(300),
        "amount" to amount,
        "paidByMemberId" to paidByMemberId,
        "dateEpochDay" to dateEpochDay,
        "createdByUid" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.set(
      logRef,
      buildActivityLogPayload(
        groupId = group.id,
        cycleId = cycleId,
        memberUids = group.memberUids,
        category = ActivityCategory.EXPENSE,
        action = ActivityAction.ADDED,
        actorUid = uid,
        actorName = actorName,
        targetMemberName = payerLabel,
        title = cleanTitle,
        changeDetail = changeDetail,
        dateEpochDay = dateEpochDay,
      ),
    )
    try {
      batch.commit().await()
      txRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, txRef.path)
      throw e
    }
  }

  suspend fun addBulkBazaarTransactions(
    group: MessGroup,
    cycleId: String,
    items: List<BulkBazaarItem>,
    sharedNotes: String,
    paidByMemberId: String,
    dateEpochDay: Long,
    actorName: String = "",
    targetMemberName: String = "",
  ): Result<Int> = runCatching {
    val uid = requireUserId()
    val validItems =
      items
        .map { it.copy(title = it.title.trim().take(100)) }
        .filter { it.title.isNotEmpty() && it.amount > 0.0 && it.amount <= 10_000_000.0 }
    require(validItems.isNotEmpty()) { "Add at least one bazaar item with an amount." }

    val cleanNotes = sharedNotes.trim().take(300)
    val groupRef = db.collection("messGroups").document(group.id)
    val txCol = groupRef.collection("transactions")
    val currency = group.currencySymbol.ifBlank { "৳" }
    val payerLabel = targetMemberName.trim().take(80)
    val totalAmount = validItems.sumOf { it.amount }
    val bulkTitle =
      if (validItems.size == 1) {
        validItems.first().title
      } else {
        "Bulk Bazaar (${validItems.size} items)"
      }
    val bulkDetail =
      buildString {
        append("$currency${MessCalculator.formatMeals(totalAmount)} • ")
        if (validItems.size == 1) {
          append(TransactionType.BAZAAR.label)
        } else {
          append(validItems.take(3).joinToString(", ") { it.title })
          if (validItems.size > 3) append(", +${validItems.size - 3} more")
        }
        if (payerLabel.isNotBlank()) {
          append(" • Paid by $payerLabel")
        }
      }

    try {
      validItems.chunked(350).forEachIndexed { chunkIdx, chunk ->
        val batch = db.batch()
        chunk.forEach { item ->
          val txRef = txCol.document()
          batch.set(
            txRef,
            mapOf(
              "groupId" to group.id,
              "cycleId" to cycleId,
              "memberUids" to group.memberUids,
              "type" to TransactionType.BAZAAR.name,
              "title" to item.title,
              "notes" to cleanNotes,
              "amount" to item.amount,
              "paidByMemberId" to paidByMemberId,
              "dateEpochDay" to dateEpochDay,
              "createdByUid" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        if (chunkIdx == 0) {
          val logRef = groupRef.collection("activityLogs").document()
          batch.set(
            logRef,
            buildActivityLogPayload(
              groupId = group.id,
              cycleId = cycleId,
              memberUids = group.memberUids,
              category = ActivityCategory.EXPENSE,
              action = ActivityAction.ADDED,
              actorUid = uid,
              actorName = actorName,
              targetMemberName = payerLabel,
              title = bulkTitle,
              changeDetail = bulkDetail,
              dateEpochDay = dateEpochDay,
            ),
          )
        }
        batch.commit().await()
      }
      validItems.size
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, txCol.path)
      throw e
    }
  }

  suspend fun deleteTransaction(
    groupId: String,
    txId: String,
    actorName: String = "",
    currencySymbol: String = "৳",
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val groupRef = db.collection("messGroups").document(groupId)
    val txRef = groupRef.collection("transactions").document(txId)
    try {
      val existing = txRef.get().await()
      if (!existing.exists()) {
        txRef.delete().await()
        return@runCatching
      }
      val existingTx = existing.toObject(MessTransaction::class.java)
      if (existingTx != null && existingTx.cycleId.isNotBlank() && existingTx.memberUids.isNotEmpty()) {
        val typeLabel =
          TransactionType.entries.firstOrNull { it.name == existingTx.type }?.label ?: existingTx.type
        val logRef = groupRef.collection("activityLogs").document()
        val batch = db.batch()
        batch.delete(txRef)
        batch.set(
          logRef,
          buildActivityLogPayload(
            groupId = groupId,
            cycleId = existingTx.cycleId,
            memberUids = existingTx.memberUids,
            category = ActivityCategory.EXPENSE,
            action = ActivityAction.REMOVED,
            actorUid = uid,
            actorName = actorName,
            targetMemberName = "",
            title = existingTx.title.ifBlank { typeLabel },
            changeDetail = "Removed $currencySymbol${MessCalculator.formatMeals(existingTx.amount)} • $typeLabel",
            dateEpochDay = existingTx.dateEpochDay,
          ),
        )
        batch.commit().await()
      } else {
        txRef.delete().await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, txRef.path)
      throw e
    }
  }

  suspend fun unlockCycle(groupId: String, cycleId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val groupRef = db.collection("messGroups").document(groupId)
    val group = groupRef.get(Source.SERVER).await().toObject(MessGroup::class.java)
      ?: throw IllegalStateException("This group is no longer available.")
    check(group.ownerUid == uid) { "Only the group manager can unlock a closed cycle." }
    val cycleRef = groupRef.collection("cycles").document(cycleId)
    val cycle = cycleRef.get(Source.SERVER).await().toObject(MessCycle::class.java)
      ?: throw IllegalStateException("This cycle is no longer available.")
    check(cycle.status == CycleStatus.CLOSED.name) { "This cycle is already unlocked." }
    try {
      cycleRef.update(
        mapOf("status" to CycleStatus.ACTIVE.name, "updatedAt" to FieldValue.serverTimestamp())
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, cycleRef.path)
      throw e
    }
  }

  suspend fun resetGroupData(groupId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val groupRef = db.collection("messGroups").document(groupId)
    val group = groupRef.get(Source.SERVER).await().toObject(MessGroup::class.java)
      ?: throw IllegalStateException("This group is no longer available.")
    check(group.ownerUid == uid) { "Only the group manager can reset group data." }
    check(uid in group.memberUids) { "You are no longer a member of this group." }

    // Reset settings first so missing deployed permissions stop the operation before any
    // operational records are deleted. Keep group identity, membership, and member profiles.
    val mode = CycleMode.MONTHLY
    val defaultWeekStartDay = 6 // Preserve the app's legacy Saturday default.
    val defaultCustomWeekStartEpochDay = -1L
    groupRef.update(
      mapOf(
        "currencySymbol" to "৳",
        "cycleMode" to mode.name,
        "weekStartDay" to defaultWeekStartDay,
        "customWeekStartEpochDay" to defaultCustomWeekStartEpochDay,
        "breakfastWeight" to FieldValue.delete(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    ).await()

    // Delete audit logs first; deployed manager-only delete rules are required for a full reset.
    for (collectionName in listOf("activityLogs", "cycles", "mealEntries", "transactions")) {
      val collection = groupRef.collection(collectionName)
      var lastDocument: DocumentSnapshot? = null
      while (true) {
        val ordered = collection.whereArrayContains("memberUids", uid).orderBy(FieldPath.documentId())
        val pageQuery = (if (lastDocument == null) ordered else ordered.startAfter(lastDocument)).limit(400)
        val page = pageQuery.get(Source.SERVER).await()
        if (page.isEmpty) break
        val batch = db.batch()
        page.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
        lastDocument = page.documents.last()
        if (page.size() < 400) break
      }
    }

    // Start a fresh cycle from the reset default settings; retain the group's identity,
    // invite, membership, and member documents (including photos).
    val (label, startDay, endDay) = defaultCycleBounds(
      mode,
      LocalDate.now(),
      defaultWeekStartDay,
      defaultCustomWeekStartEpochDay,
    )
    val cycleRef = groupRef.collection("cycles").document()
    val batch = db.batch()
    batch.set(
      cycleRef,
      mapOf(
        "groupId" to group.id,
        "memberUids" to group.memberUids,
        "label" to label,
        "cycleType" to mode.name,
        "startDateEpochDay" to startDay,
        "endDateEpochDay" to endDay,
        "status" to CycleStatus.ACTIVE.name,
        "createdByUid" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.update(
      groupRef,
      mapOf("activeCycleId" to cycleRef.id, "updatedAt" to FieldValue.serverTimestamp()),
    )
    batch.commit().await()
    Unit
  }.onFailure { error ->
    if (error !is CancellationException) {
      handleFirestoreError(error, OperationType.DELETE, "messGroups/$groupId/reset")
    }
  }

  suspend fun switchActiveCycle(groupId: String, cycleId: String): Result<Unit> = runCatching {
    requireUserId()
    val groupRef = db.collection("messGroups").document(groupId)
    try {
      groupRef
        .update(mapOf("activeCycleId" to cycleId, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
      throw e
    }
  }

  suspend fun createCycle(
    group: MessGroup,
    cycleMode: CycleMode,
    referenceDate: LocalDate,
    closePreviousCycleId: String? = null,
    carryForwardBalances: Map<String, Double> = emptyMap(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val groupRef = db.collection("messGroups").document(group.id)
    val cycleRef = groupRef.collection("cycles").document()
    val (label, startDay, endDay) =
      defaultCycleBounds(cycleMode, referenceDate, group.weekStartDay, group.customWeekStartEpochDay)

    val batch = db.batch()
    if (!closePreviousCycleId.isNullOrBlank()) {
      batch.update(
        groupRef.collection("cycles").document(closePreviousCycleId),
        mapOf("status" to CycleStatus.CLOSED.name, "updatedAt" to FieldValue.serverTimestamp()),
      )
    }
    batch.set(
      cycleRef,
      mapOf(
        "groupId" to group.id,
        "memberUids" to group.memberUids,
        "label" to label,
        "cycleType" to cycleMode.name,
        "startDateEpochDay" to startDay,
        "endDateEpochDay" to endDay,
        "status" to CycleStatus.ACTIVE.name,
        "createdByUid" to uid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    )
    batch.update(
      groupRef,
      mapOf("activeCycleId" to cycleRef.id, "updatedAt" to FieldValue.serverTimestamp()),
    )

    carryForwardBalances.forEach { (memberId, balance) ->
      if (balance > 0.5) {
        val txRef = groupRef.collection("transactions").document()
        batch.set(
          txRef,
          mapOf(
            "groupId" to group.id,
            "cycleId" to cycleRef.id,
            "memberUids" to group.memberUids,
            "type" to TransactionType.DEPOSIT.name,
            "title" to "Carried Forward Balance",
            "notes" to "Rollover credit from previous cycle",
            "amount" to balance,
            "paidByMemberId" to memberId,
            "dateEpochDay" to startDay,
            "createdByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
    }

    try {
      batch.commit().await()
      cycleRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, cycleRef.path)
      throw e
    }
  }

  suspend fun updateGroupSettings(
    group: MessGroup,
    name: String,
    currencySymbol: String,
    cycleMode: CycleMode,
    weekStartDay: Int = group.weekStartDay,
    customWeekStartEpochDay: Long = group.customWeekStartEpochDay,
  ): Result<Unit> = runCatching {
    requireUserId()
    require(weekStartDay in 1..7) { "Choose a valid week-start day." }
    require(customWeekStartEpochDay >= -1L) { "Choose a valid custom week-start date." }
    val groupRef = db.collection("messGroups").document(group.id)
    try {
      groupRef
        .update(
          mapOf(
            "name" to name.trim().take(80),
            "currencySymbol" to currencySymbol.ifBlank { "৳" }.take(6),
            "cycleMode" to cycleMode.name,
            "weekStartDay" to weekStartDay,
            "customWeekStartEpochDay" to customWeekStartEpochDay,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
      throw e
    }
  }

  suspend fun importWorkbookIntoCycle(
    group: MessGroup,
    cycleId: String,
    existingMembers: List<MessMember>,
    imported: ImportedWorkbookData,
  ): Result<Pair<Int, Int>> = runCatching {
    val uid = requireUserId()
    val groupRef = db.collection("messGroups").document(group.id)
    val memberIdByName =
      existingMembers.associate { it.name.trim().lowercase() to it.id }.toMutableMap()

    val batchOps = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

    imported.memberNames.forEach { rawName ->
      val key = rawName.trim().lowercase()
      if (key.isNotEmpty() && !memberIdByName.containsKey(key)) {
        val newMemberRef = groupRef.collection("members").document()
        memberIdByName[key] = newMemberRef.id
        batchOps.add { batch ->
          batch.set(
            newMemberRef,
            mapOf(
              "groupId" to group.id,
              "memberUids" to group.memberUids,
              "name" to rawName.trim().take(60),
              "linkedUid" to "",
              "role" to MemberRole.MEMBER.name,
              "isActive" to true,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
    }

    val mealColRef = groupRef.collection("mealEntries")
    val existingMealIds =
      runCatching {
        mealColRef.whereArrayContains("memberUids", uid).get().await().documents.map { it.id }.toSet()
      }.getOrElse { emptySet() }

    val resolvedMeals =
      imported.meals.mapNotNull { meal ->
        val memberId = memberIdByName[meal.memberName.trim().lowercase()] ?: return@mapNotNull null
        val entryId = "${cycleId}_${meal.dateEpochDay}_${memberId}"
        Triple(meal, memberId, entryId)
      }

    val unindexedExistingIds =
      if (group.memberUids.size > 1) {
        coroutineScope {
          resolvedMeals
            .map { it.third }
            .distinct()
            .filter { it !in existingMealIds }
            .map { entryId ->
              async {
                val exists = runCatching { mealColRef.document(entryId).get().await().exists() }.getOrDefault(false)
                if (exists) entryId else null
              }
            }
            .awaitAll()
            .filterNotNull()
            .toSet()
        }
      } else {
        emptySet()
      }
    val allExistingMealIds = existingMealIds + unindexedExistingIds

    resolvedMeals.forEach { (meal, memberId, entryId) ->
      val mealRef = mealColRef.document(entryId)
      if (entryId in allExistingMealIds) {
        batchOps.add { batch ->
          batch.update(
            mealRef,
            mapOf(
              "lunch" to meal.lunch,
              "dinner" to meal.dinner,
              "guestMeals" to meal.guestMeals,
              "updatedByUid" to uid,
              "memberUids" to group.memberUids,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      } else {
        batchOps.add { batch ->
          batch.set(
            mealRef,
            mapOf(
              "groupId" to group.id,
              "cycleId" to cycleId,
              "memberUids" to group.memberUids,
              "memberId" to memberId,
              "dateEpochDay" to meal.dateEpochDay,
              "lunch" to meal.lunch,
              "dinner" to meal.dinner,
              "guestMeals" to meal.guestMeals,
              "updatedByUid" to uid,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
    }

    imported.transactions.forEach { tx ->
      val payerId =
        if (tx.payerName.isBlank() || tx.payerName.equals("Mess Pool", ignoreCase = true)) {
          MessTransaction.POOL_PAYER_ID
        } else {
          memberIdByName[tx.payerName.trim().lowercase()] ?: MessTransaction.POOL_PAYER_ID
        }
      val txRef = groupRef.collection("transactions").document()
      batchOps.add { batch ->
        batch.set(
          txRef,
          mapOf(
            "groupId" to group.id,
            "cycleId" to cycleId,
            "memberUids" to group.memberUids,
            "type" to tx.type,
            "title" to tx.title,
            "notes" to tx.notes,
            "amount" to tx.amount,
            "paidByMemberId" to payerId,
            "dateEpochDay" to tx.dateEpochDay,
            "createdByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
    }

    try {
      batchOps.chunked(350).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { op -> op(batch) }
        batch.commit().await()
      }
      imported.meals.size to imported.transactions.size
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, groupRef.path)
      throw e
    }
  }

  companion object {
    private val INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    private val RANDOM = SecureRandom()

    fun generateInviteCode(): String =
      CharArray(6) { INVITE_CHARS[RANDOM.nextInt(INVITE_CHARS.size)] }.concatToString()

    fun defaultCycleBounds(
      mode: CycleMode,
      referenceDate: LocalDate,
      weekStartDay: Int = DayOfWeek.SATURDAY.value,
      customWeekStartEpochDay: Long = -1L,
    ): Triple<String, Long, Long> {
      return when (mode) {
        CycleMode.MONTHLY -> {
          val start = referenceDate.withDayOfMonth(1)
          val end = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth())
          val label = start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
          Triple(label, start.toEpochDay(), end.toEpochDay())
        }
        CycleMode.WEEKLY -> {
          val start = if (customWeekStartEpochDay >= 0L) {
            val weeksFromAnchor = Math.floorDiv(referenceDate.toEpochDay() - customWeekStartEpochDay, 7L)
            LocalDate.ofEpochDay(customWeekStartEpochDay + weeksFromAnchor * 7L)
          } else {
            val safeDay = weekStartDay.coerceIn(1, 7)
            referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(safeDay)))
          }
          val end = start.plusDays(6)
          val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
          val label = "${start.format(fmt)} – ${end.format(fmt)}"
          Triple(label, start.toEpochDay(), end.toEpochDay())
        }
      }
    }
  }
}
