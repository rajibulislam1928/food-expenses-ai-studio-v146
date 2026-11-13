package com.example.domain

import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import kotlin.math.abs

enum class ReminderStyle(val label: String) {
  DETAILED("Detailed Breakdown"),
  SHORT("Quick Ping"),
}

object ReminderMessageBuilder {

  fun hasNegativeBalance(standing: MemberStanding): Boolean = standing.netBalance < -0.01

  fun buildMessage(
    standing: MemberStanding,
    group: MessGroup,
    cycle: MessCycle?,
    settlementTransfers: List<SettlementTransfer> = emptyList(),
    style: ReminderStyle = ReminderStyle.DETAILED,
  ): String {
    val currency = group.currencySymbol
    val memberName = standing.member.name
    val dueAmount = abs(standing.netBalance).coerceAtLeast(0.0)
    val formattedDue = MessCalculator.formatAmount(dueAmount, currency)
    val cycleLabel = cycle?.label ?: "Current Cycle"

    val memberTransfers =
      settlementTransfers.filter { it.fromMemberName.equals(memberName, ignoreCase = true) }

    val paymentLine =
      if (memberTransfers.isNotEmpty()) {
        val parts =
          memberTransfers.joinToString(", ") { tr ->
            "${MessCalculator.formatAmount(tr.amount, currency)} to ${tr.toRecipientName}"
          }
        "Suggested payment: $parts."
      } else {
        "Please settle $formattedDue with the Mess Pool when convenient."
      }

    return when (style) {
      ReminderStyle.SHORT -> {
        buildString {
          append("Hi $memberName, friendly reminder from ${group.name} ($cycleLabel): ")
          append("your mess due balance is $formattedDue. ")
          append(paymentLine)
          append(" Thank you!")
        }
      }

      ReminderStyle.DETAILED -> {
        buildString {
          appendLine("Hi $memberName,")
          appendLine("Here is your mess balance reminder for ${group.name} ($cycleLabel):")
          appendLine()
          appendLine("- Total Meals: ${MessCalculator.formatMeals(standing.totalMeals)}")
          appendLine("- Meal Cost: ${MessCalculator.formatAmount(standing.mealCost, currency)}")
          if (standing.fixedShare > 0.005) {
            appendLine("- Fixed / Utility Share: ${MessCalculator.formatAmount(standing.fixedShare, currency)}")
          }
          appendLine("- Total Payable: ${MessCalculator.formatAmount(standing.totalPayable, currency)}")
          appendLine("- Paid / Deposited: ${MessCalculator.formatAmount(standing.totalCredits, currency)}")
          appendLine("- Balance Due: $formattedDue")
          appendLine()
          appendLine(paymentLine)
          append("Thanks!")
        }
      }
    }
  }
}
