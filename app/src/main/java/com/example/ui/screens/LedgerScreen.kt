package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CycleMode
import com.example.data.model.CycleStatus
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import com.example.domain.MemberContributionSummary
import com.example.domain.MemberStanding
import com.example.domain.MessCalculator
import com.example.domain.ReminderMessageBuilder
import com.example.ui.MessDashboardData
import com.example.ui.theme.NegativeCrimson
import com.example.ui.theme.NegativeCrimsonDark
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PositiveEmeraldDark
import com.example.ui.theme.TabularNumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LedgerScreen(
  data: MessDashboardData,
  isManager: Boolean,
  onUnlockCycle: (String) -> Unit,
  onResetGroupData: () -> Unit,
  onSettleMember: (MemberStanding) -> Unit,
  onAddMember: (String) -> Unit,
  onToggleMemberActive: (MessMember, Boolean) -> Unit,
  onToggleMemberEditPermission: (MessMember, Boolean) -> Unit,
  onRemoveMember: (MessMember) -> Unit,
  onPermanentlyDeleteMember: (MessMember) -> Unit,
  onSwitchCycle: (String) -> Unit,
  onStartNewCycle: (CycleMode, closeCurrent: Boolean, rollover: Boolean) -> Unit,
  onExportXlsx: () -> Unit,
  onImportXlsx: () -> Unit,
  onSendReminder: (MemberStanding) -> Unit,
  onCopiedCode: (String) -> Unit,
) {
  val context = LocalContext.current
  val currency = data.activeGroup.currencySymbol
  val isClosed = data.activeCycle?.status == CycleStatus.CLOSED.name
  val isDark = isSystemInDarkTheme()
  val positiveColor = if (isDark) PositiveEmeraldDark else PositiveEmerald
  val negativeColor = if (isDark) NegativeCrimsonDark else NegativeCrimson

  var showAddMemberDialog by remember { mutableStateOf(false) }
  var pendingRemoveMember by remember { mutableStateOf<MessMember?>(null) }
  var pendingDeleteMember by remember { mutableStateOf<MessMember?>(null) }
  var showNewCycleDialog by remember { mutableStateOf(false) }
  var showResetConfirmation by remember { mutableStateOf(false) }
  var pendingSettle by remember { mutableStateOf<MemberStanding?>(null) }
  var memberSearchQuery by remember { mutableStateOf("") }
  var expandedContributionIds by remember { mutableStateOf<Set<String>>(emptySet()) }

  val filteredStandings =
    remember(data.summary.standings, memberSearchQuery) {
      MessCalculator.filterStandings(data.summary.standings, memberSearchQuery)
    }
  val filteredDirectoryMembers =
    remember(data.members, memberSearchQuery) {
      MessCalculator.filterMembers(data.members, memberSearchQuery)
    }
  val contributionMap =
    remember(data.members, data.cycleTransactions) {
      data.members.associate { member ->
        member.id to MessCalculator.computeMemberContributions(member.id, data.cycleTransactions)
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Cycle Management & History
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("Billing Cycles", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Active: ${data.activeCycle?.label ?: "None"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = { showNewCycleDialog = true },
              modifier = Modifier.height(48.dp).testTag("start_new_cycle_button"),
            ) {
              Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("New Cycle")
            }
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.cycles, key = { it.id }) { cycle ->
              val isSelected = cycle.id == data.activeCycle?.id
              val badge = if (cycle.status == CycleStatus.CLOSED.name) " (Closed)" else ""
              FilterChip(
                selected = isSelected,
                onClick = { onSwitchCycle(cycle.id) },
                label = { Text("${cycle.label}$badge") },
              )
            }
          }
          if (isManager && isClosed) {
            OutlinedButton(
              onClick = { onUnlockCycle(data.activeCycle.id) },
              modifier = Modifier.testTag("unlock_closed_cycle_button"),
            ) {
              Text("Unlock closed cycle")
            }
          }
          if (isManager) {
            OutlinedButton(
              onClick = { showResetConfirmation = true },
              modifier = Modifier.testTag("reset_group_data_button"),
            ) {
              Text("Reset group data")
            }
          }
        }
      }
    }

    // 2. Balance Sheet Header & Roommate Search Bar
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Balance Sheet & Settlements",
            style = MaterialTheme.typography.titleLarge,
          )
          if (memberSearchQuery.isNotBlank()) {
            Text(
              text = "${filteredStandings.size} of ${data.summary.standings.size}",
              style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

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
                    .testTag("ledger_clear_member_search_button"),
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
          modifier = Modifier.fillMaxWidth().testTag("ledger_member_search_input"),
        )
      }
    }

    if (filteredStandings.isEmpty() && memberSearchQuery.isNotBlank()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
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
              text = "Try another name or clear the search filter to see everyone's balance and contributions.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
              onClick = { memberSearchQuery = "" },
              modifier = Modifier.height(48.dp).testTag("ledger_reset_member_search"),
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
        val isExpanded = standing.member.id in expandedContributionIds
        BalanceSheetCard(
          standing = standing,
          contributions = contributions,
          isContributionsExpanded = isExpanded,
          onToggleContributions = {
            expandedContributionIds =
              if (isExpanded) expandedContributionIds - standing.member.id
              else expandedContributionIds + standing.member.id
          },
          currency = currency,
          isClosed = isClosed,
          positiveColor = positiveColor,
          negativeColor = negativeColor,
          onSettleClick = { pendingSettle = standing },
          onSendReminderClick = { onSendReminder(standing) },
        )
      }
    }

    // 3. Members Directory
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val directoryLabel =
              if (memberSearchQuery.isBlank()) "Mess Members (${data.members.size})"
              else "Mess Members (${filteredDirectoryMembers.size} of ${data.members.size})"
            Text(directoryLabel, style = MaterialTheme.typography.titleMedium)
            if (isManager) {
              OutlinedButton(
                onClick = { showAddMemberDialog = true },
                modifier = Modifier.height(48.dp).testTag("add_member_button"),
              ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add")
              }
            }
          }

          if (filteredDirectoryMembers.isEmpty() && memberSearchQuery.isNotBlank()) {
            Text(
              text = "No roommates match \"${memberSearchQuery.trim()}\".",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            filteredDirectoryMembers.forEachIndexed { index, member ->
              val memberContrib = contributionMap[member.id]
              if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                MemberAvatar(
                  name = member.name,
                  photoBase64 = member.avatarBase64,
                  size = 38.dp,
                  modifier = Modifier.testTag("member_avatar_${member.id}"),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(member.name, style = MaterialTheme.typography.titleSmall)
                  Text(
                    text =
                      buildString {
                        append(if (member.role == "MANAGER") "Manager" else "Member")
                        append(" · ")
                        append(if (member.linkedUid.isNotBlank()) "Account linked" else "Managed offline")
                        append(" · ")
                        append(when {
                          member.isRemoved -> "Removed"
                          member.isActive -> "Active"
                          else -> "Paused"
                        })
                        if (memberContrib != null && memberContrib.totalContributed > 0.0) {
                          append(" · Contributed ")
                          append(MessCalculator.formatAmount(memberContrib.totalContributed, currency))
                        }
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (isManager) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!member.isRemoved) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Active", style = MaterialTheme.typography.labelSmall)
                        Switch(
                          checked = member.isActive,
                          onCheckedChange = { onToggleMemberActive(member, it) },
                          modifier = Modifier.testTag("member_active_${member.id}"),
                        )
                      }
                      Spacer(Modifier.width(4.dp))
                      val managerRow = member.role == "MANAGER" || member.linkedUid == data.activeGroup.ownerUid
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Meals/expenses", style = MaterialTheme.typography.labelSmall)
                        Switch(
                          checked = managerRow || member.canEditMealsAndExpenses,
                          onCheckedChange = { if (!managerRow) onToggleMemberEditPermission(member, it) },
                          enabled = !managerRow,
                          modifier = Modifier.testTag("member_edit_permission_${member.id}"),
                        )
                      }
                    }
                    if (member.role != "MANAGER" && member.linkedUid != data.currentUserId) {
                      IconButton(
                        onClick = { pendingRemoveMember = member },
                        modifier = Modifier.testTag("remove_member_${member.id}"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = "Manage ${member.name}",
                          tint = MaterialTheme.colorScheme.error,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Invite Code & Spreadsheet (.xlsx) Backup
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("Roommate Invite & Excel Backup", style = MaterialTheme.typography.titleMedium)

          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Invite Code", style = MaterialTheme.typography.labelSmall)
                Text(
                  text = data.activeGroup.inviteCode,
                  style =
                    MaterialTheme.typography.headlineSmall.merge(TabularNumberStyle).copy(
                      fontWeight = FontWeight.Bold
                    ),
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              Row {
                IconButton(
                  onClick = {
                    val clipboard =
                      context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                      ClipData.newPlainText("Mess Invite Code", data.activeGroup.inviteCode)
                    )
                    onCopiedCode("Copied invite code ${data.activeGroup.inviteCode}")
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_invite_button"),
                ) {
                  Icon(Icons.Filled.ContentCopy, contentDescription = "Copy invite code")
                }
                IconButton(
                  onClick = {
                    val sendIntent =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                          Intent.EXTRA_TEXT,
                          "Join our mess \"${data.activeGroup.name}\" on Food Expenses! Invite code: ${data.activeGroup.inviteCode}",
                        )
                      }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Mess Invite"))
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("share_invite_button"),
                ) {
                  Icon(Icons.Filled.Share, contentDescription = "Share invite code")
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = onExportXlsx,
              modifier = Modifier.weight(1f).height(50.dp).testTag("export_xlsx_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.export_xlsx))
            }
            OutlinedButton(
              onClick = onImportXlsx,
              enabled = !isClosed,
              modifier = Modifier.weight(1f).height(50.dp).testTag("import_xlsx_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.import_xlsx))
            }
          }
        }
      }
    }
  }

  if (showAddMemberDialog) {
    AddMemberDialog(
      onDismiss = { showAddMemberDialog = false },
      onAdd = { name ->
        onAddMember(name)
        showAddMemberDialog = false
      },
    )
  }

  pendingRemoveMember?.let { member ->
    AlertDialog(
      onDismissRequest = { pendingRemoveMember = null },
      title = { Text("Manage ${member.name}") },
      text = {
        Text(
          if (member.isRemoved) {
            "${member.name} has already lost access. Their history is still saved. You can permanently delete their linked history and member profile."
          } else {
            "Choose whether to remove access but keep the history, or permanently delete this member and their linked history."
          }
        )
      },
      confirmButton = {
        Button(
          onClick = {
            pendingDeleteMember = member
            pendingRemoveMember = null
          },
          modifier = Modifier.testTag("choose_delete_member_button"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete member & history")
        }
      },
      dismissButton = {
        Row {
          if (!member.isRemoved) {
            TextButton(
              onClick = {
                onRemoveMember(member)
                pendingRemoveMember = null
              },
              modifier = Modifier.testTag("confirm_remove_member_button"),
            ) { Text("Remove access") }
          }
          TextButton(onClick = { pendingRemoveMember = null }) { Text("Cancel") }
        }
      },
    )
  }

  pendingDeleteMember?.let { member ->
    AlertDialog(
      onDismissRequest = { pendingDeleteMember = null },
      title = { Text("Permanently delete ${member.name}?") },
      text = {
        Text(
          "This permanently deletes ${member.name}'s member profile, meal entries, expenses they paid, and activity history uniquely linked to them. Other members' records and the mess stay. This can't be undone."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onPermanentlyDeleteMember(member)
            pendingDeleteMember = null
          },
          modifier = Modifier.testTag("confirm_permanently_delete_member_button"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Delete permanently")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteMember = null }) { Text("Cancel") }
      },
    )
  }

  if (showNewCycleDialog) {
    StartNewCycleDialog(
      defaultMode =
        CycleMode.entries.firstOrNull { it.name == data.activeGroup.cycleMode }
          ?: CycleMode.MONTHLY,
      hasActiveCycle = data.activeCycle != null,
      onDismiss = { showNewCycleDialog = false },
      onConfirm = { mode, closePrev, rollover ->
        onStartNewCycle(mode, closePrev, rollover)
        showNewCycleDialog = false
      },
    )
  }

  if (showResetConfirmation) {
    AlertDialog(
      onDismissRequest = { showResetConfirmation = false },
      title = { Text("Reset group data?") },
      text = {
        Text("This permanently clears meal, expense, cycle, and activity records and resets group settings to defaults. The group name, membership, and member profiles/photos will stay.")
      },
      confirmButton = {
        Button(
          onClick = {
            onResetGroupData()
            showResetConfirmation = false
          },
          modifier = Modifier.testTag("confirm_reset_group_data_button"),
        ) {
          Text("Reset group data")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") }
      },
    )
  }

  pendingSettle?.let { standing ->
    val isRefund = standing.netBalance > 0.0
    AlertDialog(
      onDismissRequest = { pendingSettle = null },
      title = { Text("Settle ${standing.member.name}") },
      text = {
        val formatted = MessCalculator.formatAmount(kotlin.math.abs(standing.netBalance), currency)
        Text(
          if (isRefund) {
            "Record a refund of $formatted paid out from the mess pool to ${standing.member.name}?"
          } else {
            "Record a settlement payment of $formatted received from ${standing.member.name} into the mess pool?"
          }
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onSettleMember(standing)
            pendingSettle = null
          },
          modifier = Modifier.testTag("confirm_settle_button"),
        ) {
          Text("Confirm Settlement")
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingSettle = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun BalanceSheetCard(
  standing: MemberStanding,
  contributions: MemberContributionSummary,
  isContributionsExpanded: Boolean,
  onToggleContributions: () -> Unit,
  currency: String,
  isClosed: Boolean,
  positiveColor: Color,
  negativeColor: Color,
  onSettleClick: () -> Unit,
  onSendReminderClick: () -> Unit,
) {
  val isSettled = kotlin.math.abs(standing.netBalance) < 0.01
  val isRefund = standing.netBalance > 0.0
  val hasNegativeBalance = ReminderMessageBuilder.hasNegativeBalance(standing)
  val statusColor =
    when {
      isSettled -> MaterialTheme.colorScheme.onSurfaceVariant
      isRefund -> positiveColor
      else -> negativeColor
    }
  val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(standing.member.name, style = MaterialTheme.typography.titleMedium)
        Text(
          text =
            when {
              isSettled -> "Settled"
              isRefund -> "Refund ${MessCalculator.formatAmount(standing.netBalance, currency)}"
              else -> "Due ${MessCalculator.formatAmount(kotlin.math.abs(standing.netBalance), currency)}"
            },
          style =
            MaterialTheme.typography.titleMedium.merge(TabularNumberStyle).copy(
              fontWeight = FontWeight.Bold
            ),
          color = statusColor,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        LedgerMiniCell("Meals", MessCalculator.formatMeals(standing.totalMeals))
        LedgerMiniCell("Meal Cost", MessCalculator.formatAmount(standing.mealCost, currency))
        LedgerMiniCell("Fixed Share", MessCalculator.formatAmount(standing.fixedShare, currency))
        LedgerMiniCell("Paid / Dep", MessCalculator.formatAmount(standing.totalCredits, currency))
      }

      // Individual Expense Contributions Summary
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Expense Contributions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = MessCalculator.formatAmount(contributions.totalContributed, currency),
              style =
                MaterialTheme.typography.labelLarge.merge(TabularNumberStyle).copy(
                  fontWeight = FontWeight.Bold
                ),
              color = MaterialTheme.colorScheme.primary,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            LedgerMiniCell("Bazaar Paid", MessCalculator.formatAmount(contributions.bazaarPaid, currency))
            LedgerMiniCell("Fixed Paid", MessCalculator.formatAmount(contributions.fixedPaid, currency))
            LedgerMiniCell("Deposited", MessCalculator.formatAmount(contributions.deposited, currency))
          }

          TextButton(
            onClick = onToggleContributions,
            modifier =
              Modifier.fillMaxWidth()
                .height(48.dp)
                .testTag("toggle_contributions_${standing.member.id}"),
          ) {
            Text(
              text =
                if (isContributionsExpanded) {
                  stringResource(R.string.hide_contributions)
                } else {
                  "${stringResource(R.string.view_contributions)} (${contributions.transactions.size})"
                }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector =
                if (isContributionsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }

          AnimatedVisibility(visible = isContributionsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              if (contributions.transactions.isEmpty()) {
                Text(
                  text = "No individual bazaar, fixed, or deposit entries paid by ${standing.member.name} in this cycle.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                contributions.transactions.forEach { tx ->
                  val typeLabel =
                    when (tx.type) {
                      TransactionType.BAZAAR.name -> "Bazaar"
                      TransactionType.FIXED_UTILITY.name -> "Fixed Utility"
                      TransactionType.DEPOSIT.name -> "Pool Deposit"
                      TransactionType.SETTLEMENT.name ->
                        if (tx.notes.startsWith(MessTransaction.SETTLEMENT_REFUND_OUT)) "Refund"
                        else "Settlement"
                      else -> tx.type
                    }
                  val txDate = LocalDate.ofEpochDay(tx.dateEpochDay).format(dateFormatter)
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = tx.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "$typeLabel · $txDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = MessCalculator.formatAmount(tx.amount, currency),
                      style =
                        MaterialTheme.typography.bodyMedium.merge(TabularNumberStyle).copy(
                          fontWeight = FontWeight.SemiBold
                        ),
                      color = positiveColor,
                    )
                  }
                }
              }
            }
          }
        }
      }

      if (!isSettled && !isClosed && hasNegativeBalance) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onSettleClick,
            modifier = Modifier.weight(1f).height(48.dp).testTag("settle_${standing.member.id}"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Mark Due Paid", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          FilledTonalButton(
            onClick = onSendReminderClick,
            modifier =
              Modifier.weight(1f).height(48.dp).testTag("send_reminder_${standing.member.id}"),
            shape = RoundedCornerShape(12.dp),
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
          ) {
            Icon(
              Icons.Filled.NotificationsActive,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.send_reminder),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      } else if (!isSettled && !isClosed) {
        OutlinedButton(
          onClick = onSettleClick,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settle_${standing.member.id}"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Issue Refund")
        }
      } else if (hasNegativeBalance) {
        FilledTonalButton(
          onClick = onSendReminderClick,
          modifier =
            Modifier.fillMaxWidth().height(48.dp).testTag("send_reminder_${standing.member.id}"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
          Icon(
            Icons.Filled.NotificationsActive,
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
private fun LedgerMiniCell(label: String, value: String) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
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
private fun AddMemberDialog(
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Mess Member") },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Member Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("new_member_name_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onAdd(name) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("confirm_add_member_button"),
      ) {
        Text("Add Member")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun StartNewCycleDialog(
  defaultMode: CycleMode,
  hasActiveCycle: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (CycleMode, closeCurrent: Boolean, rollover: Boolean) -> Unit,
) {
  var mode by remember { mutableStateOf(defaultMode) }
  var closeCurrent by remember { mutableStateOf(hasActiveCycle) }
  var rollover by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Start New Billing Cycle") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CycleMode.entries.forEach { m ->
            FilterChip(
              selected = mode == m,
              onClick = { mode = m },
              label = { Text(m.label) },
            )
          }
        }
        if (hasActiveCycle) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = closeCurrent, onCheckedChange = { closeCurrent = it })
            Text("Lock & close current cycle", style = MaterialTheme.typography.bodyMedium)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rollover, onCheckedChange = { rollover = it })
            Text("Carry positive refund balances forward", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(mode, closeCurrent, rollover) },
        modifier = Modifier.testTag("confirm_new_cycle_button"),
      ) {
        Text("Start Cycle")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
