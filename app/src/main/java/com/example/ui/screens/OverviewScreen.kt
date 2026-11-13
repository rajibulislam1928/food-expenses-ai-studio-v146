package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.MemberContributionSummary
import com.example.domain.MemberStanding
import com.example.domain.MessCalculator
import com.example.domain.ReminderMessageBuilder
import com.example.ui.MessDashboardData
import com.example.ui.MessTab
import com.example.ui.theme.NegativeCrimson
import com.example.ui.theme.NegativeCrimsonDark
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PositiveEmeraldDark
import com.example.ui.theme.TabularNumberStyle

@Composable
fun OverviewScreen(
  data: MessDashboardData,
  isWideLayout: Boolean,
  onNavigateTab: (MessTab) -> Unit,
  onQuickAddBazaar: () -> Unit,
  onQuickBulkBazaar: () -> Unit,
  onQuickAddDeposit: () -> Unit,
  onShareInvite: () -> Unit,
  onDownloadReport: () -> Unit,
  onSendReminder: (MemberStanding) -> Unit,
) {
  val currency = data.activeGroup.currencySymbol
  val summary = data.summary
  val isDark = isSystemInDarkTheme()
  val positiveColor = if (isDark) PositiveEmeraldDark else PositiveEmerald
  val negativeColor = if (isDark) NegativeCrimsonDark else NegativeCrimson
  var memberSearchQuery by remember { mutableStateOf("") }
  val filteredStandings =
    remember(summary.standings, memberSearchQuery) {
      MessCalculator.filterStandings(summary.standings, memberSearchQuery)
    }
  val contributionMap =
    remember(summary.standings, data.cycleTransactions) {
      summary.standings.associate { standing ->
        standing.member.id to
          MessCalculator.computeMemberContributions(standing.member.id, data.cycleTransactions)
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Hero Live Meal Rate Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(
                Brush.linearGradient(
                  colors =
                    listOf(
                      Color(0xFF0B6E4F),
                      Color(0xFF064833),
                    )
                )
              )
              .padding(22.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "LIVE MEAL RATE",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color(0xFFA7F3D0),
                )
                Text(
                  text = "${MessCalculator.formatAmount(summary.mealRate, currency)} / meal",
                  style =
                    MaterialTheme.typography.displaySmall.merge(TabularNumberStyle).copy(
                      fontWeight = FontWeight.Bold
                    ),
                  color = Color.White,
                )
              }
              Surface(
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text(
                  text = data.activeCycle?.label ?: "Active Cycle",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              HeroStatPill(
                label = "Total Meals",
                value = MessCalculator.formatMeals(summary.totalMeals),
                modifier = Modifier.weight(1f),
              )
              HeroStatPill(
                label = "Bazaar Cost",
                value = MessCalculator.formatAmount(summary.totalBazaar, currency),
                modifier = Modifier.weight(1f),
              )
              HeroStatPill(
                label = "Pool Cash",
                value = MessCalculator.formatAmount(summary.messPoolCashBalance, currency),
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    // 2. Quick Actions Strip
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
          QuickActionChip(
            icon = Icons.Filled.Fastfood,
            label = "Log Meals",
            tag = "quick_log_meals",
            onClick = { onNavigateTab(MessTab.MEALS) },
          )
        }
        item {
          QuickActionChip(
            icon = Icons.Filled.AddShoppingCart,
            label = "Add Bazaar",
            tag = "quick_add_bazaar",
            onClick = onQuickAddBazaar,
          )
        }
        item {
          QuickActionChip(
            icon = Icons.Filled.PlaylistAddCheck,
            label = stringResource(R.string.bulk_bazaar),
            tag = "quick_bulk_bazaar",
            onClick = onQuickBulkBazaar,
          )
        }
        item {
          QuickActionChip(
            icon = Icons.Filled.Payments,
            label = "Add Deposit",
            tag = "quick_add_deposit",
            onClick = onQuickAddDeposit,
          )
        }
        item {
          QuickActionChip(
            icon = Icons.Filled.Share,
            label = "Invite (${data.activeGroup.inviteCode})",
            tag = "quick_share_invite",
            onClick = onShareInvite,
          )
        }
        item {
          QuickActionChip(
            icon = Icons.Filled.Download,
            label = "Download Report",
            tag = "quick_download_report",
            onClick = onDownloadReport,
          )
        }
      }
    }

    // 3. Personal Standing + Cycle Totals (side-by-side on wide screens)
    item {
      if (isWideLayout) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          PersonalStandingCard(
            standing = data.currentMemberStanding,
            currency = currency,
            positiveColor = positiveColor,
            negativeColor = negativeColor,
            modifier = Modifier.weight(1f),
          )
          CycleCostBreakdownCard(
            totalBazaar = summary.totalBazaar,
            totalFixed = summary.totalFixed,
            totalDeposits = summary.totalDeposits,
            fixedPerMember = summary.fixedSharePerMember,
            currency = currency,
            modifier = Modifier.weight(1f),
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          PersonalStandingCard(
            standing = data.currentMemberStanding,
            currency = currency,
            positiveColor = positiveColor,
            negativeColor = negativeColor,
            modifier = Modifier.fillMaxWidth(),
          )
          CycleCostBreakdownCard(
            totalBazaar = summary.totalBazaar,
            totalFixed = summary.totalFixed,
            totalDeposits = summary.totalDeposits,
            fixedPerMember = summary.fixedSharePerMember,
            currency = currency,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    // 4. Member Standings Preview Header & Roommate Search
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val countText =
            if (memberSearchQuery.isBlank()) "${summary.standings.size}"
            else "${filteredStandings.size}/${summary.standings.size}"
          Text(
            text = "Member Standings ($countText)",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "Full Ledger →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateTab(MessTab.LEDGER) }
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("overview_open_ledger"),
          )
        }

        if (summary.standings.isNotEmpty()) {
          OutlinedTextField(
            value = memberSearchQuery,
            onValueChange = { memberSearchQuery = it },
            placeholder = { Text(stringResource(R.string.search_roommates_hint)) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
              )
            },
            trailingIcon = {
              if (memberSearchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { memberSearchQuery = "" },
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .testTag("overview_clear_member_search_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.clear_search),
                  )
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("overview_member_search_input"),
          )
        }
      }
    }

    if (summary.standings.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Filled.Group,
          title = "No members yet",
          message = "Add roommates in the Ledger tab or share your invite code to start tracking.",
        )
      }
    } else if (filteredStandings.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "No roommates match \"${memberSearchQuery.trim()}\"",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "Clear the search filter to view all roommates and their expense contributions.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
              onClick = { memberSearchQuery = "" },
              modifier = Modifier.height(48.dp).testTag("overview_reset_member_search"),
            ) {
              Text(stringResource(R.string.clear_search))
            }
          }
        }
      }
    } else {
      items(filteredStandings, key = { it.member.id }) { standing ->
        val contributions =
          contributionMap[standing.member.id]
            ?: MessCalculator.computeMemberContributions(standing.member.id, data.cycleTransactions)
        MemberStandingPreviewRow(
          standing = standing,
          contributions = contributions,
          totalCycleMeals = summary.totalMeals,
          currency = currency,
          positiveColor = positiveColor,
          negativeColor = negativeColor,
          onClick = { onNavigateTab(MessTab.LEDGER) },
          onSendReminderClick = { onSendReminder(standing) },
        )
      }
    }
  }
}

@Composable
private fun HeroStatPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = Color.White.copy(alpha = 0.12f),
    shape = RoundedCornerShape(14.dp),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFFD1FAE5),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium.merge(TabularNumberStyle),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun QuickActionChip(
  icon: ImageVector,
  label: String,
  tag: String,
  onClick: () -> Unit,
) {
  AssistChip(
    onClick = onClick,
    modifier = Modifier.height(48.dp).testTag(tag),
    leadingIcon = {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
    },
    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
  )
}

@Composable
private fun PersonalStandingCard(
  standing: MemberStanding?,
  currency: String,
  positiveColor: Color,
  negativeColor: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Your Position", style = MaterialTheme.typography.titleMedium)
        Icon(
          imageVector = Icons.Filled.AccountBalanceWallet,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
      }

      if (standing == null) {
        Text(
          text = "Your account is viewing this mess. Add or link a member record in Ledger to track personal meals.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val isRefund = standing.netBalance >= -0.005
        val statusColor = if (isRefund) positiveColor else negativeColor
        val statusLabel =
          if (kotlin.math.abs(standing.netBalance) < 0.01) {
            "Settled"
          } else if (isRefund) {
            "Refundable"
          } else {
            "Due Payable"
          }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom,
        ) {
          Column {
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelMedium,
              color = statusColor,
            )
            Text(
              text = MessCalculator.formatAmount(standing.netBalance, currency),
              style =
                MaterialTheme.typography.headlineMedium.merge(TabularNumberStyle).copy(
                  fontWeight = FontWeight.Bold
                ),
              color = statusColor,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "${MessCalculator.formatMeals(standing.totalMeals)} meals",
              style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
            )
            Text(
              text = "Cost ${MessCalculator.formatAmount(standing.totalPayable, currency)}",
              style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CycleCostBreakdownCard(
  totalBazaar: Double,
  totalFixed: Double,
  totalDeposits: Double,
  fixedPerMember: Double,
  currency: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Cycle Summary", style = MaterialTheme.typography.titleMedium)
        Icon(
          imageVector = Icons.Filled.ReceiptLong,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
      }
      MetricLine("Total Bazaar (Meal Pool)", MessCalculator.formatAmount(totalBazaar, currency))
      MetricLine("Fixed Utilities (Split)", MessCalculator.formatAmount(totalFixed, currency))
      MetricLine("Fixed Share / Active Member", MessCalculator.formatAmount(fixedPerMember, currency))
      MetricLine("Total Deposits Received", MessCalculator.formatAmount(totalDeposits, currency))
    }
  }
}

@Composable
private fun MetricLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.merge(TabularNumberStyle),
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun MemberStandingPreviewRow(
  standing: MemberStanding,
  contributions: MemberContributionSummary,
  totalCycleMeals: Double,
  currency: String,
  positiveColor: Color,
  negativeColor: Color,
  onClick: () -> Unit,
  onSendReminderClick: () -> Unit,
) {
  val mealRatio =
    if (totalCycleMeals > 0.0) (standing.totalMeals / totalCycleMeals).toFloat().coerceIn(0f, 1f)
    else 0f
  val hasNegativeBalance = ReminderMessageBuilder.hasNegativeBalance(standing)
  val balanceColor = if (!hasNegativeBalance) positiveColor else negativeColor

  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier =
              Modifier.size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = standing.member.name.take(1).uppercase(),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          Column {
            Text(
              text = standing.member.name,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text =
                "${MessCalculator.formatMeals(standing.totalMeals)} meals · Contributed ${MessCalculator.formatAmount(contributions.totalContributed, currency)}",
              style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Text(
          text = MessCalculator.formatAmount(standing.netBalance, currency),
          style =
            MaterialTheme.typography.titleMedium.merge(TabularNumberStyle).copy(
              fontWeight = FontWeight.Bold
            ),
          color = balanceColor,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Bazaar: ${MessCalculator.formatAmount(contributions.bazaarPaid, currency)}",
          style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Fixed: ${MessCalculator.formatAmount(contributions.fixedPaid, currency)}",
          style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "Deposits: ${MessCalculator.formatAmount(contributions.deposited, currency)}",
          style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      LinearProgressIndicator(
        progress = { mealRatio },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )

      if (hasNegativeBalance) {
        FilledTonalButton(
          onClick = onSendReminderClick,
          modifier =
            Modifier.fillMaxWidth()
              .height(48.dp)
              .testTag("overview_send_reminder_${standing.member.id}"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
          Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.send_reminder))
        }
      }
    }
  }
}

@Composable
fun EmptyStateCard(
  icon: ImageVector,
  title: String,
  message: String,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
