package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.MessCycle
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.domain.BulkBazaarItem
import com.example.domain.BulkBazaarRowDraft
import com.example.domain.BulkBazaarValidator
import com.example.domain.MessCalculator
import com.example.ui.theme.TabularNumberStyle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkBazaarDialog(
  members: List<MessMember>,
  activeCycle: MessCycle?,
  currencySymbol: String,
  onDismiss: () -> Unit,
  onSubmitBulk: (
    items: List<BulkBazaarItem>,
    sharedNotes: String,
    paidByMemberId: String,
    dateEpochDay: Long,
  ) -> Unit,
) {
  val initialDate = remember(activeCycle?.id) {
    clampEpochDayToCycle(LocalDate.now().toEpochDay(), activeCycle)
  }
  var dateEpochDay by remember(activeCycle?.id) { mutableLongStateOf(initialDate) }
  var paidById by remember { mutableStateOf(MessTransaction.POOL_PAYER_ID) }
  var sharedNotes by remember { mutableStateOf("") }
  var nextRowId by remember { mutableIntStateOf(4) }
  var rows by remember {
    mutableStateOf(
      listOf(
        BulkBazaarRowDraft(id = 1),
        BulkBazaarRowDraft(id = 2),
        BulkBazaarRowDraft(id = 3),
      )
    )
  }

  val validation = remember(rows) { BulkBazaarValidator.validate(rows) }
  val canSave = validation.validItems.isNotEmpty() && validation.incompleteRowIds.isEmpty()

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      Scaffold(
        topBar = {
          TopAppBar(
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
              ),
            navigationIcon = {
              IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("close_bulk_bazaar_dialog"),
              ) {
                Icon(Icons.Filled.Close, contentDescription = "Close bulk entry")
              }
            },
            title = {
              Column {
                Text(
                  text = stringResource(R.string.bulk_bazaar_title),
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  text = stringResource(R.string.bulk_bazaar_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
          )
        },
        bottomBar = {
          Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text =
                    when {
                      validation.incompleteRowIds.isNotEmpty() ->
                        "Complete or clear ${validation.incompleteRowIds.size} highlighted row(s)"
                      validation.validItems.isEmpty() -> "Enter item names and prices above"
                      else -> "${validation.validItems.size} bazaar item(s) ready"
                    },
                  style = MaterialTheme.typography.labelLarge,
                  color =
                    if (validation.incompleteRowIds.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = MessCalculator.formatAmount(validation.totalAmount, currencySymbol),
                  style =
                    MaterialTheme.typography.titleLarge.merge(TabularNumberStyle).copy(
                      fontWeight = FontWeight.Bold
                    ),
                  color = MaterialTheme.colorScheme.primary,
                )
              }

              Button(
                onClick = {
                  onSubmitBulk(validation.validItems, sharedNotes, paidById, dateEpochDay)
                  onDismiss()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("bulk_bazaar_submit_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val formattedTotal = MessCalculator.formatAmount(validation.totalAmount, currencySymbol)
                Text(
                  text =
                    if (validation.validItems.isEmpty()) "Save Bazaar Items"
                    else "Save ${validation.validItems.size} Items ($formattedTotal)"
                )
              }
            }
          }
        },
        containerColor = MaterialTheme.colorScheme.background,
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // 1. Shared Date, Payer & Trip Note Card
            item {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Text("Trip Details", style = MaterialTheme.typography.titleSmall)

                  CycleDatePickerSection(
                    selectedEpochDay = dateEpochDay,
                    activeCycle = activeCycle,
                    onDateSelected = { dateEpochDay = it },
                    tagPrefix = "bulk",
                  )

                  Text("Paid From / By", style = MaterialTheme.typography.labelLarge)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                      FilterChip(
                        selected = paidById == MessTransaction.POOL_PAYER_ID,
                        onClick = { paidById = MessTransaction.POOL_PAYER_ID },
                        label = { Text("Mess Pool") },
                        modifier = Modifier.testTag("bulk_payer_pool"),
                      )
                    }
                    items(members, key = { it.id }) { member ->
                      FilterChip(
                        selected = paidById == member.id,
                        onClick = { paidById = member.id },
                        label = { Text(member.name) },
                      )
                    }
                  }

                  OutlinedTextField(
                    value = sharedNotes,
                    onValueChange = { sharedNotes = it },
                    label = { Text("Market / Trip Note (Optional, applied to all items)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bulk_shared_notes_input"),
                  )
                }
              }
            }

            // 2. Quick-Tap Grocery Staples
            item {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "Quick-Add Daily Staples",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(BulkBazaarValidator.DEFAULT_GROCERY_PRESETS) { preset ->
                    AssistChip(
                      onClick = {
                        val blankIdx = rows.indexOfFirst { it.title.isBlank() }
                        if (blankIdx >= 0) {
                          rows =
                            rows.toMutableList().also { list ->
                              list[blankIdx] = list[blankIdx].copy(title = preset)
                            }
                        } else {
                          rows = rows + BulkBazaarRowDraft(id = nextRowId, title = preset)
                          nextRowId += 1
                        }
                      },
                      label = { Text("+ $preset", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                      modifier = Modifier.height(48.dp),
                    )
                  }
                }
              }
            }

            item {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 3. Grocery Item Rows
            items(rows.size, key = { idx -> rows[idx].id }) { index ->
              val row = rows[index]
              val isIncomplete = row.id in validation.incompleteRowIds

              BulkBazaarItemRow(
                index = index + 1,
                row = row,
                currencySymbol = currencySymbol,
                isIncomplete = isIncomplete,
                onTitleChange = { newTitle ->
                  rows = rows.map { if (it.id == row.id) it.copy(title = newTitle) else it }
                },
                onAmountChange = { newAmount ->
                  if (newAmount.isEmpty() || newAmount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    rows = rows.map { if (it.id == row.id) it.copy(amountText = newAmount) else it }
                  }
                },
                onRemove = {
                  rows =
                    if (rows.size <= 1) {
                      listOf(BulkBazaarRowDraft(id = row.id))
                    } else {
                      rows.filterNot { it.id == row.id }
                    }
                },
              )
            }

            // 4. Add Row Actions
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = {
                    rows = rows + BulkBazaarRowDraft(id = nextRowId)
                    nextRowId += 1
                  },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("bulk_add_row_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Add Item Row")
                }
                OutlinedButton(
                  onClick = {
                    val startId = nextRowId
                    rows =
                      rows +
                        listOf(
                          BulkBazaarRowDraft(id = startId),
                          BulkBazaarRowDraft(id = startId + 1),
                          BulkBazaarRowDraft(id = startId + 2),
                        )
                    nextRowId = startId + 3
                  },
                  modifier = Modifier.height(48.dp).testTag("bulk_add_3_rows_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Text("+3 Rows")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BulkBazaarItemRow(
  index: Int,
  row: BulkBazaarRowDraft,
  currencySymbol: String,
  isIncomplete: Boolean,
  onTitleChange: (String) -> Unit,
  onAmountChange: (String) -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isIncomplete) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
          else MaterialTheme.colorScheme.surface
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(28.dp)
            .clip(CircleShape)
            .background(
              if (isIncomplete) MaterialTheme.colorScheme.errorContainer
              else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = index.toString(),
          style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle),
          fontWeight = FontWeight.Bold,
          color =
            if (isIncomplete) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      OutlinedTextField(
        value = row.title,
        onValueChange = onTitleChange,
        label = { Text("Item") },
        placeholder = { Text("e.g. Rice") },
        isError = isIncomplete && row.title.isBlank(),
        singleLine = true,
        keyboardOptions =
          KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
          ),
        modifier = Modifier.weight(1.45f).testTag("bulk_item_title_$index"),
      )

      OutlinedTextField(
        value = row.amountText,
        onValueChange = onAmountChange,
        label = { Text(currencySymbol) },
        placeholder = { Text("0") },
        isError = isIncomplete && (row.amountText.toDoubleOrNull() ?: 0.0) <= 0.0,
        singleLine = true,
        textStyle = TabularNumberStyle,
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
          ),
        modifier = Modifier.weight(1f).testTag("bulk_item_amount_$index"),
      )

      IconButton(
        onClick = onRemove,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("bulk_item_remove_$index"),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = "Remove item $index",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
