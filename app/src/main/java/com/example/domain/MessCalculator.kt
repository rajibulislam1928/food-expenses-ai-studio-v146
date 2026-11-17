package com.example.domain

import com.example.data.model.ActivityCategory
import com.example.data.model.MealEntry
import com.example.data.model.MessActivityLog
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class MemberStanding(
  val member: MessMember,
  val totalMeals: Double,
  val mealCost: Double,
  val fixedShare: Double,
  val totalPayable: Double,
  val totalDeposited: Double,
  val outOfPocketPaid: Double,
  val settlementDuePaid: Double,
  val settlementRefundReceived: Double,
  val netBalance: Double,
) {
  val totalCredits: Double
    get() = totalDeposited + outOfPocketPaid + settlementDuePaid - settlementRefundReceived
}

data class SettlementTransfer(
  val fromMemberName: String,
  val toRecipientName: String,
  val amount: Double,
  val category: String,
)

data class CycleSummary(
  val totalBazaar: Double = 0.0,
  val totalFixed: Double = 0.0,
  val totalDeposits: Double = 0.0,
  val totalSettlementsIn: Double = 0.0,
  val totalSettlementsOut: Double = 0.0,
  val totalMeals: Double = 0.0,
  val mealRate: Double = 0.0,
  val fixedSharePerMember: Double = 0.0,
  val messPoolCashBalance: Double = 0.0,
  val standings: List<MemberStanding> = emptyList(),
  val settlementTransfers: List<SettlementTransfer> = emptyList(),
)

object MessCalculator {

  fun computeCycleSummary(
    members: List<MessMember>,
    mealEntries: List<MealEntry>,
    transactions: List<MessTransaction>,
  ): CycleSummary {
    val bazaarTx = transactions.filter { it.type == TransactionType.BAZAAR.name }
    val fixedTx = transactions.filter { it.type == TransactionType.FIXED_UTILITY.name }
    val depositTx = transactions.filter { it.type == TransactionType.DEPOSIT.name }
    val settlementTx = transactions.filter { it.type == TransactionType.SETTLEMENT.name }

    val totalBazaar = bazaarTx.sumOf { it.amount }
    val totalFixed = fixedTx.sumOf { it.amount }
    val totalDeposits = depositTx.sumOf { it.amount }

    val totalMeals = mealEntries.sumOf { it.totalMeals() }
    val mealRate = if (totalMeals > 0.0) totalBazaar / totalMeals else 0.0

    val activeMembers = members.filter { it.isActive }
    val fixedDivisor =
      when {
        activeMembers.isNotEmpty() -> activeMembers.size
        members.isNotEmpty() -> members.size
        else -> 1
      }
    val fixedSharePerMember = if (members.isEmpty()) 0.0 else totalFixed / fixedDivisor

    val poolBazaar = bazaarTx.filter { it.paidByMemberId == MessTransaction.POOL_PAYER_ID }.sumOf { it.amount }
    val poolFixed = fixedTx.filter { it.paidByMemberId == MessTransaction.POOL_PAYER_ID }.sumOf { it.amount }

    val settlementsIn =
      settlementTx.filter { !it.notes.startsWith(MessTransaction.SETTLEMENT_REFUND_OUT) }.sumOf { it.amount }
    val settlementsOut =
      settlementTx.filter { it.notes.startsWith(MessTransaction.SETTLEMENT_REFUND_OUT) }.sumOf { it.amount }

    val messPoolCashBalance = totalDeposits + settlementsIn - poolBazaar - poolFixed - settlementsOut

    val mealsByMember = mealEntries.groupBy { it.memberId }
    val standings =
      members.map { member ->
        val memberMeals =
          mealsByMember[member.id]?.sumOf { it.totalMeals() } ?: 0.0
        val mealCost = memberMeals * mealRate
        val fixedShare = if (member.isActive || activeMembers.isEmpty()) fixedSharePerMember else 0.0
        val totalPayable = mealCost + fixedShare

        val deposited = depositTx.filter { it.paidByMemberId == member.id }.sumOf { it.amount }
        val outOfPocket =
          (bazaarTx + fixedTx).filter { it.paidByMemberId == member.id }.sumOf { it.amount }
        val duePaid =
          settlementTx
            .filter {
              it.paidByMemberId == member.id &&
                !it.notes.startsWith(MessTransaction.SETTLEMENT_REFUND_OUT)
            }
            .sumOf { it.amount }
        val refundReceived =
          settlementTx
            .filter {
              it.paidByMemberId == member.id &&
                it.notes.startsWith(MessTransaction.SETTLEMENT_REFUND_OUT)
            }
            .sumOf { it.amount }

        val credits = deposited + outOfPocket + duePaid - refundReceived
        val netBalance = credits - totalPayable

        MemberStanding(
          member = member,
          totalMeals = memberMeals,
          mealCost = mealCost,
          fixedShare = fixedShare,
          totalPayable = totalPayable,
          totalDeposited = deposited,
          outOfPocketPaid = outOfPocket,
          settlementDuePaid = duePaid,
          settlementRefundReceived = refundReceived,
          netBalance = netBalance,
        )
      }

    val settlementTransfers = computeSettlementPlan(standings)

    return CycleSummary(
      totalBazaar = totalBazaar,
      totalFixed = totalFixed,
      totalDeposits = totalDeposits,
      totalSettlementsIn = settlementsIn,
      totalSettlementsOut = settlementsOut,
      totalMeals = totalMeals,
      mealRate = mealRate,
      fixedSharePerMember = fixedSharePerMember,
      messPoolCashBalance = messPoolCashBalance,
      standings = standings,
      settlementTransfers = settlementTransfers,
    )
  }

  fun computeSettlementPlan(standings: List<MemberStanding>): List<SettlementTransfer> {
    data class MutableBalance(val name: String, var amount: Double)

    val debtors =
      standings
        .filter { it.netBalance < -0.01 }
        .map { MutableBalance(it.member.name, -it.netBalance) }
        .sortedByDescending { it.amount }
        .toMutableList()
    val creditors =
      standings
        .filter { it.netBalance > 0.01 }
        .map { MutableBalance(it.member.name, it.netBalance) }
        .sortedByDescending { it.amount }
        .toMutableList()

    val transfers = mutableListOf<SettlementTransfer>()
    var dIdx = 0
    var cIdx = 0
    while (dIdx < debtors.size && cIdx < creditors.size) {
      val debtor = debtors[dIdx]
      val creditor = creditors[cIdx]
      val transferAmount = minOf(debtor.amount, creditor.amount)
      val rounded = kotlin.math.round(transferAmount * 100.0) / 100.0
      if (rounded >= 0.01) {
        transfers.add(
          SettlementTransfer(
            fromMemberName = debtor.name,
            toRecipientName = creditor.name,
            amount = rounded,
            category = "Member-to-Member",
          )
        )
      }
      debtor.amount -= transferAmount
      creditor.amount -= transferAmount
      if (debtor.amount <= 0.01) dIdx++
      if (creditor.amount <= 0.01) cIdx++
    }

    while (cIdx < creditors.size) {
      val creditor = creditors[cIdx]
      val rounded = kotlin.math.round(creditor.amount * 100.0) / 100.0
      if (rounded >= 0.01) {
        transfers.add(
          SettlementTransfer(
            fromMemberName = "Mess Pool",
            toRecipientName = creditor.name,
            amount = rounded,
            category = "Pool-to-Member Refund",
          )
        )
      }
      cIdx++
    }

    while (dIdx < debtors.size) {
      val debtor = debtors[dIdx]
      val rounded = kotlin.math.round(debtor.amount * 100.0) / 100.0
      if (rounded >= 0.01) {
        transfers.add(
          SettlementTransfer(
            fromMemberName = debtor.name,
            toRecipientName = "Mess Pool",
            amount = rounded,
            category = "Member-to-Pool Due",
          )
        )
      }
      dIdx++
    }

    return transfers
  }

  fun formatAmount(amount: Double, currencySymbol: String): String {
    val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    val absFormatted = formatter.format(kotlin.math.abs(amount))
    return if (amount < -0.005) "-$currencySymbol$absFormatted" else "$currencySymbol$absFormatted"
  }

  fun formatMeals(meals: Double): String {
    val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    return formatter.format(meals)
  }

  fun computeMemberContributions(
    memberId: String,
    transactions: List<MessTransaction>,
  ): MemberContributionSummary {
    val memberTx =
      transactions
        .filter { it.paidByMemberId == memberId }
        .sortedByDescending { it.dateEpochDay }
    val bazaarPaid =
      memberTx.filter { it.type == TransactionType.BAZAAR.name }.sumOf { it.amount }
    val fixedPaid =
      memberTx.filter { it.type == TransactionType.FIXED_UTILITY.name }.sumOf { it.amount }
    val deposited =
      memberTx.filter { it.type == TransactionType.DEPOSIT.name }.sumOf { it.amount }
    val settlementPaid =
      memberTx
        .filter {
          it.type == TransactionType.SETTLEMENT.name &&
            !it.notes.startsWith(MessTransaction.SETTLEMENT_REFUND_OUT)
        }
        .sumOf { it.amount }
    val outOfPocketTotal = bazaarPaid + fixedPaid
    val totalContributed = outOfPocketTotal + deposited + settlementPaid
    return MemberContributionSummary(
      bazaarPaid = bazaarPaid,
      fixedPaid = fixedPaid,
      deposited = deposited,
      settlementPaid = settlementPaid,
      outOfPocketTotal = outOfPocketTotal,
      totalContributed = totalContributed,
      transactions = memberTx,
    )
  }

  fun filterStandings(
    standings: List<MemberStanding>,
    query: String,
  ): List<MemberStanding> {
    val clean = query.trim()
    if (clean.isEmpty()) return standings
    return standings.filter { it.member.name.contains(clean, ignoreCase = true) }
  }

  fun filterMembers(
    members: List<MessMember>,
    query: String,
  ): List<MessMember> {
    val clean = query.trim()
    if (clean.isEmpty()) return members
    return members.filter { it.name.contains(clean, ignoreCase = true) }
  }

  fun resolveActorName(
    uid: String,
    members: List<MessMember>,
    currentUserId: String = "",
    currentUserDisplayName: String = "",
    explicitActorName: String = "",
  ): String {
    if (uid.isBlank()) {
      return explicitActorName.trim().ifBlank { "Roommate" }
    }
    val linked = members.firstOrNull { it.linkedUid == uid }?.name?.trim().orEmpty()
    if (linked.isNotBlank()) return linked
    if (uid == currentUserId && currentUserDisplayName.isNotBlank()) {
      return currentUserDisplayName.trim()
    }
    if (explicitActorName.isNotBlank()) return explicitActorName.trim()
    return "Roommate"
  }

  fun formatMealDiff(
    prevLunch: Double,
    prevDinner: Double,
    prevGuest: Double,
    newLunch: Double,
    newDinner: Double,
    newGuest: Double,
  ): String {
    val parts = mutableListOf<String>()
    if (kotlin.math.abs(prevLunch - newLunch) > 0.001) {
      parts.add("Lunch ${formatMeals(prevLunch)} → ${formatMeals(newLunch)}")
    }
    if (kotlin.math.abs(prevDinner - newDinner) > 0.001) {
      parts.add("Dinner ${formatMeals(prevDinner)} → ${formatMeals(newDinner)}")
    }
    if (kotlin.math.abs(prevGuest - newGuest) > 0.001) {
      parts.add("Guest ${formatMeals(prevGuest)} → ${formatMeals(newGuest)}")
    }
    if (parts.isEmpty()) {
      return formatMealValuesSummary(newLunch, newDinner, newGuest)
    }
    return parts.joinToString(", ").take(240)
  }

  fun formatMealValuesSummary(
    lunch: Double,
    dinner: Double,
    guest: Double,
  ): String {
    val parts = mutableListOf<String>()
    if (lunch > 0.0) parts.add("Lunch ${formatMeals(lunch)}")
    if (dinner > 0.0) parts.add("Dinner ${formatMeals(dinner)}")
    if (guest > 0.0) parts.add("Guest ${formatMeals(guest)}")
    return if (parts.isEmpty()) "0 meals" else parts.joinToString(", ")
  }

  fun filterPendingActivityNotifications(
    logs: List<MessActivityLog>,
    currentUid: String,
    seenIds: Set<String>,
    minTimestampMillis: Long,
    notifyOnMeals: Boolean,
    notifyOnExpenses: Boolean,
    includeSelfChanges: Boolean,
  ): List<MessActivityLog> {
    if (currentUid.isBlank()) return emptyList()
    return logs
      .filter { log ->
        val createdMillis = log.createdAtMillis()
        val idValid = log.id.isNotBlank() && log.id !in seenIds
        val afterBaseline = createdMillis == 0L || createdMillis >= minTimestampMillis
        val categoryAllowed =
          when (log.category) {
            ActivityCategory.MEAL.name -> notifyOnMeals
            ActivityCategory.EXPENSE.name -> notifyOnExpenses
            else -> true
          }
        val actorAllowed = includeSelfChanges || log.actorUid != currentUid
        idValid && afterBaseline && categoryAllowed && actorAllowed
      }
      .sortedBy { it.createdAtMillis() }
  }

  fun isActivityLogUnread(
    log: MessActivityLog,
    currentUid: String,
    lastSeenMillis: Long,
    includeSelfChanges: Boolean,
  ): Boolean =
    log.createdAtMillis() > lastSeenMillis &&
      (includeSelfChanges || log.actorUid != currentUid)

  fun filterActivityLogs(
    logs: List<MessActivityLog>,
    categoryFilter: ActivityCategory?,
  ): List<MessActivityLog> {
    return if (categoryFilter == null) {
      logs
    } else {
      logs.filter { it.category == categoryFilter.name }
    }
  }
}

data class MemberContributionSummary(
  val bazaarPaid: Double,
  val fixedPaid: Double,
  val deposited: Double,
  val settlementPaid: Double,
  val outOfPocketTotal: Double,
  val totalContributed: Double,
  val transactions: List<MessTransaction>,
)
