package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CycleMode(val label: String) {
  MONTHLY("Monthly"),
  WEEKLY("Weekly"),
}

enum class CycleStatus {
  ACTIVE,
  CLOSED,
}

enum class MemberRole {
  MANAGER,
  MEMBER,
}

enum class TransactionType(val label: String) {
  BAZAAR("Bazaar"),
  FIXED_UTILITY("Fixed / Utility"),
  DEPOSIT("Deposit"),
  SETTLEMENT("Settlement"),
}

enum class ActivityCategory(val label: String) {
  MEAL("Meals"),
  EXPENSE("Expenses"),
}

enum class ActivityAction(val label: String) {
  ADDED("Added"),
  UPDATED("Updated"),
  REMOVED("Removed"),
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String = "",
  val activeGroupId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MessInvite(
  @DocumentId val id: String = "",
  val inviteCode: String = "",
  val groupId: String = "",
  val groupName: String = "",
  val ownerUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MessGroup(
  @DocumentId val id: String = "",
  val name: String = "",
  val ownerUid: String = "",
  val memberUids: List<String> = emptyList(),
  val editorUids: List<String> = emptyList(),
  val editPermissionsInitialized: Boolean = false,
  val inviteCode: String = "",
  val currencySymbol: String = "৳",
  val cycleMode: String = CycleMode.MONTHLY.name,
  val weekStartDay: Int = 6,
  val customWeekStartEpochDay: Long = -1L,
  val activeCycleId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MessCycle(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val memberUids: List<String> = emptyList(),
  val label: String = "",
  val cycleType: String = CycleMode.MONTHLY.name,
  val startDateEpochDay: Long = 0L,
  val endDateEpochDay: Long = 0L,
  val status: String = CycleStatus.ACTIVE.name,
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MessMember(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val memberUids: List<String> = emptyList(),
  val name: String = "",
  val linkedUid: String = "",
  val avatarBase64: String = "",
  val role: String = MemberRole.MEMBER.name,
  val isActive: Boolean = true,
  val canEditMealsAndExpenses: Boolean = true,
  val isRemoved: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MealEntry(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val cycleId: String = "",
  val memberUids: List<String> = emptyList(),
  val memberId: String = "",
  val dateEpochDay: Long = 0L,
  val lunch: Double = 0.0,
  val dinner: Double = 0.0,
  val guestMeals: Double = 0.0,
  val updatedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun totalMeals(): Double = lunch + dinner + guestMeals
}

@IgnoreExtraProperties
data class MessTransaction(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val cycleId: String = "",
  val memberUids: List<String> = emptyList(),
  val type: String = TransactionType.BAZAAR.name,
  val title: String = "",
  val notes: String = "",
  val amount: Double = 0.0,
  val paidByMemberId: String = POOL_PAYER_ID,
  val dateEpochDay: Long = 0L,
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val POOL_PAYER_ID = "MESS_POOL"
    const val SETTLEMENT_DUE_IN = "DUE_IN"
    const val SETTLEMENT_REFUND_OUT = "REFUND_OUT"
  }
}

@IgnoreExtraProperties
data class MessActivityLog(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val cycleId: String = "",
  val memberUids: List<String> = emptyList(),
  val category: String = ActivityCategory.MEAL.name,
  val action: String = ActivityAction.ADDED.name,
  val actorUid: String = "",
  val actorName: String = "",
  val targetMemberName: String = "",
  val title: String = "",
  val changeDetail: String = "",
  val dateEpochDay: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  fun createdAtMillis(): Long = createdAt?.toDate()?.time ?: 0L
}
