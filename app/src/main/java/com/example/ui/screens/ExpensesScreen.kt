package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CycleStatus
import com.example.data.model.MessCycle
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import com.example.domain.MessCalculator
import com.example.ui.MessDashboardData
import com.example.ui.TxFilter
import com.example.ui.theme.NegativeCrimson
import com.example.ui.theme.NegativeCrimsonDark
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PositiveEmeraldDark
import com.example.ui.theme.TabularNumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExpensesScreen(
  data: MessDashboardData,
  selectedFilter: TxFilter,
  showExpenseBanner: Boolean,
  onSelectFilter: (TxFilter) -> Unit,
  onDismissExpenseBanner: () -> Unit,
  onOpenBulkBazaar: () -> Unit,
  onDeleteTransaction: (String) -> Unit,
) {
  val currency = data.activeGroup.currencySymbol
  val isClosed = data.activeCycle?.status == CycleStatus.CLOSED.name
  val memberById = data.members.associateBy { it.id }

  val filteredTx =
    remember(data.cycleTransactions, selectedFilter) {
      when (selectedFilter) {
        TxFilter.ALL -> data.cycleTransactions
        TxFilter.BAZAAR -> data.cycleTransactions.filter { it.type == TransactionType.BAZAAR.name }
        TxFilter.FIXED_UTILITY ->
          data.cycleTransactions.filter { it.type == TransactionType.FIXED_UTILITY.name }
        TxFilter.DEPOSIT -> data.cycleTransactions.filter { it.type == TransactionType.DEPOSIT.name }
        TxFilter.SETTLEMENT ->
          data.cycleTransactions.filter { it.type == TransactionType.SETTLEMENT.name }
      }
    }

  val filteredTotal = filteredTx.sumOf { it.amount }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 92.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      AnimatedVisibility(
        visible = showExpenseBanner,
        enter = fadeIn(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        ExpenseCyclePoolBanner(
          cycleLabel = data.activeCycle?.label ?: "Active Cycle",
          isClosed = isClosed,
          poolCashBalance = data.summary.messPoolCashBalance,
          currency = currency,
          onDismiss = onDismissExpenseBanner,
        )
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TxFilter.entries) { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { onSelectFilter(filter) },
            label = { Text(filter.label) },
            modifier = Modifier.height(48.dp).testTag("tx_filter_${filter.name.lowercase()}"),
          )
        }
      }
    }

    item {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${filteredTx.size} entries (${selectedFilter.label})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = MessCalculator.formatAmount(filteredTotal, currency),
            style = MaterialTheme.typography.titleMedium.merge(TabularNumberStyle),
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    if (!isClosed) {
      item {
        FilledTonalButton(
          onClick = onOpenBulkBazaar,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("expenses_bulk_bazaar_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.size(8.dp))
          Text("Bulk Bazaar Entry (Add Grocery List)")
        }
      }
    }

    if (filteredTx.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Filled.ReceiptLong,
          title = "No ${selectedFilter.label.lowercase()} entries yet",
          message = "Tap + Log Entry below to add a bazaar trip, fixed utility bill, or member deposit.",
        )
      }
    } else {
      items(filteredTx, key = { it.id }) { tx ->
        val addedByName =
          tx.createdByUid.takeIf { it.isNotBlank() }?.let { uid ->
            MessCalculator.resolveActorName(
              uid = uid,
              members = data.members,
              currentUserId = data.currentUserId,
              currentUserDisplayName = data.userProfile?.displayName.orEmpty(),
            )
          }
        TransactionRowCard(
          tx = tx,
          payerName =
            if (tx.paidByMemberId == MessTransaction.POOL_PAYER_ID) {
              "Mess Pool"
            } else {
              memberById[tx.paidByMemberId]?.name ?: "Member"
            },
          addedByName = addedByName,
          currency = currency,
          canDelete = !isClosed,
          onDelete = { onDeleteTransaction(tx.id) },
        )
      }
    }
  }
}

@Composable
private fun ExpenseCyclePoolBanner(
  cycleLabel: String,
  isClosed: Boolean,
  poolCashBalance: Double,
  currency: String,
  onDismiss: () -> Unit,
) {
  val isOverdrawn = poolCashBalance < -0.01
  val formattedPool = MessCalculator.formatAmount(poolCashBalance, currency)
  val containerColor =
    if (isOverdrawn || isClosed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
  val contentColor =
    if (isOverdrawn || isClosed) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

  val bodyText =
    when {
      isClosed -> stringResource(R.string.expense_banner_body_closed)
      isOverdrawn -> stringResource(R.string.expense_banner_body_overdrawn, formattedPool)
      else -> stringResource(R.string.expense_banner_body_healthy)
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("expense_cycle_pool_banner"),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(R.string.expense_banner_badge),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("dismiss_expense_banner_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.expense_banner_dismiss_cd),
            tint = contentColor,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Text(
        text = "$cycleLabel · Mess Pool Cash: $formattedPool",
        style = MaterialTheme.typography.titleSmall.merge(TabularNumberStyle),
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
      Text(
        text = bodyText,
        style = MaterialTheme.typography.bodySmall,
        color = contentColor.copy(alpha = 0.9f),
        modifier = Modifier.padding(end = 8.dp),
      )
    }
  }
}

@Composable
private fun TransactionRowCard(
  tx: MessTransaction,
  payerName: String,
  addedByName: String?,
  currency: String,
  canDelete: Boolean,
  onDelete: () -> Unit,
) {
  val isDark = isSystemInDarkTheme()
  val positiveColor = if (isDark) PositiveEmeraldDark else PositiveEmerald
  val negativeColor = if (isDark) NegativeCrimsonDark else NegativeCrimson

  val (icon, tint, amountColor) =
    when (tx.type) {
      TransactionType.BAZAAR.name ->
        Triple(Icons.Filled.ShoppingBasket, MaterialTheme.colorScheme.secondary, negativeColor)
      TransactionType.FIXED_UTILITY.name ->
        Triple(Icons.Filled.Bolt, MaterialTheme.colorScheme.tertiary, negativeColor)
      TransactionType.DEPOSIT.name ->
        Triple(Icons.Filled.Payments, MaterialTheme.colorScheme.primary, positiveColor)
      else -> Triple(Icons.Filled.Handshake, MaterialTheme.colorScheme.primary, positiveColor)
    }

  val dateText =
    LocalDate.ofEpochDay(tx.dateEpochDay)
      .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = tx.type,
          tint = tint,
          modifier = Modifier.size(20.dp),
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = tx.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "$dateText · $payerName",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!addedByName.isNullOrBlank()) {
          Text(
            text = "Added by $addedByName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("tx_added_by_${tx.id}"),
          )
        }
        if (tx.notes.isNotBlank()) {
          Text(
            text = tx.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = MessCalculator.formatAmount(tx.amount, currency),
          style =
            MaterialTheme.typography.titleMedium.merge(TabularNumberStyle).copy(
              fontWeight = FontWeight.Bold
            ),
          color = amountColor,
        )
        if (canDelete) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_tx_${tx.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Delete entry",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
  initialType: TransactionType,
  members: List<MessMember>,
  activeCycle: MessCycle?,
  currencySymbol: String,
  onDismiss: () -> Unit,
  onSwitchToBulkBazaar: () -> Unit,
  onSubmit: (
    type: TransactionType,
    title: String,
    notes: String,
    amount: Double,
    paidByMemberId: String,
    dateEpochDay: Long,
  ) -> Unit,
) {
  var selectedType by remember { mutableStateOf(initialType) }
  var title by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var amountText by remember { mutableStateOf("") }
  val initialDate = remember(activeCycle?.id) {
    clampEpochDayToCycle(LocalDate.now().toEpochDay(), activeCycle)
  }
  var dateEpochDay by remember(activeCycle?.id) { mutableLongStateOf(initialDate) }

  var paidById by remember(selectedType, members) {
    mutableStateOf(
      if (selectedType == TransactionType.DEPOSIT) {
        members.firstOrNull()?.id ?: MessTransaction.POOL_PAYER_ID
      } else {
        MessTransaction.POOL_PAYER_ID
      }
    )
  }

  val presets =
    when (selectedType) {
      TransactionType.BAZAAR -> listOf("Daily Bazaar", "Rice & Oil", "Fish & Meat", "Vegetables")
      TransactionType.FIXED_UTILITY -> listOf("House Rent", "Gas & Electricity", "Maid Bill", "Wi-Fi")
      TransactionType.DEPOSIT -> listOf("Monthly Deposit", "Mid-Month Top-up", "Weekly Advance")
      TransactionType.SETTLEMENT -> listOf("Cycle Settlement")
    }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Log Mess Entry", style = MaterialTheme.typography.headlineSmall)

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
          listOf(
            TransactionType.BAZAAR,
            TransactionType.FIXED_UTILITY,
            TransactionType.DEPOSIT,
          )
        ) { type ->
          FilterChip(
            selected = selectedType == type,
            onClick = { selectedType = type },
            label = { Text(type.label) },
            modifier = Modifier.testTag("sheet_type_${type.name.lowercase()}"),
          )
        }
      }

      if (selectedType == TransactionType.BAZAAR) {
        OutlinedButton(
          onClick = {
            onDismiss()
            onSwitchToBulkBazaar()
          },
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("switch_to_bulk_bazaar_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(8.dp))
          Text("Switch to Bulk Grocery List →")
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(presets) { preset ->
          FilterChip(
            selected = title == preset,
            onClick = { title = preset },
            label = { Text(preset) },
          )
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Description") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_title_input"),
      )

      OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
          if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            amountText = input
          }
        },
        label = { Text("Amount ($currencySymbol)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_amount_input"),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Items / Notes (Optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_notes_input"),
      )

      CycleDatePickerSection(
        selectedEpochDay = dateEpochDay,
        activeCycle = activeCycle,
        onDateSelected = { dateEpochDay = it },
        tagPrefix = "tx",
      )

      Text(
        text = if (selectedType == TransactionType.DEPOSIT) "Deposited By" else "Paid From / By",
        style = MaterialTheme.typography.labelLarge,
      )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedType != TransactionType.DEPOSIT) {
          item {
            FilterChip(
              selected = paidById == MessTransaction.POOL_PAYER_ID,
              onClick = { paidById = MessTransaction.POOL_PAYER_ID },
              label = { Text("Mess Pool") },
            )
          }
        }
        items(members, key = { it.id }) { member ->
          FilterChip(
            selected = paidById == member.id,
            onClick = { paidById = member.id },
            label = { Text(member.name) },
          )
        }
      }

      val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
      Button(
        onClick = {
          onSubmit(selectedType, title, notes, parsedAmount, paidById, dateEpochDay)
          onDismiss()
        },
        enabled = title.isNotBlank() && parsedAmount > 0.0,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tx_submit_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text("Save ${selectedType.label}")
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
