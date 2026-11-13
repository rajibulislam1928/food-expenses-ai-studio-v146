package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActivityAction
import com.example.data.model.ActivityCategory
import com.example.data.model.MessActivityLog
import com.example.data.model.MessMember
import com.example.data.preferences.NotificationPrefsSnapshot
import com.example.domain.MessCalculator
import com.example.notifications.MessNotificationHelper
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessActivitySheet(
  logs: List<MessActivityLog>,
  members: List<MessMember>,
  currentUserId: String,
  prefs: NotificationPrefsSnapshot,
  onToggleBackgroundAlerts: (Boolean) -> Unit,
  onToggleMealAlerts: (Boolean) -> Unit,
  onToggleExpenseAlerts: (Boolean) -> Unit,
  onToggleIncludeSelf: (Boolean) -> Unit,
  onRunBackgroundCheckNow: () -> Unit,
  onMarkAllRead: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedCategory by remember { mutableStateOf<ActivityCategory?>(null) }
  var hasPermission by remember {
    mutableStateOf(MessNotificationHelper.hasNotificationPermission(context))
  }
  var showAlertSettings by remember { mutableStateOf(true) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted || MessNotificationHelper.hasNotificationPermission(context)
    }

  val filteredLogs =
    remember(logs, selectedCategory) {
      MessCalculator.filterActivityLogs(logs, selectedCategory)
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(max = 680.dp)
          .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.activity_alerts_title),
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text = "See who added or changed meals & expenses",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (logs.isNotEmpty()) {
          TextButton(
            onClick = onMarkAllRead,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("activity_mark_all_read"),
          ) {
            Icon(
              imageVector = Icons.Filled.DoneAll,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.mark_all_read))
          }
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("close_activity_sheet"),
        ) {
          Icon(Icons.Filled.Close, contentDescription = "Close")
        }
      }

      if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Background alerts need notification permission",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Text(
                text = "Allow notifications so updates appear even when the app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
              )
            }
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
              modifier = Modifier.height(48.dp).testTag("request_notification_permission_button"),
            ) {
              Text("Allow")
            }
          }
        }
      }

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
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
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = "Background Notifications",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    if (prefs.backgroundEnabled) "Active every 15 min & on sync"
                    else "Paused",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = prefs.backgroundEnabled,
              onCheckedChange = { enabled ->
                onToggleBackgroundAlerts(enabled)
                if (enabled && !hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
              },
              modifier = Modifier.testTag("toggle_background_notifications"),
            )
          }

          if (showAlertSettings && prefs.backgroundEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              item {
                FilterChip(
                  selected = prefs.notifyOnMeals,
                  onClick = { onToggleMealAlerts(!prefs.notifyOnMeals) },
                  label = { Text("Meal Updates") },
                  modifier = Modifier.testTag("toggle_meal_alerts_chip"),
                )
              }
              item {
                FilterChip(
                  selected = prefs.notifyOnExpenses,
                  onClick = { onToggleExpenseAlerts(!prefs.notifyOnExpenses) },
                  label = { Text("Expense Updates") },
                  modifier = Modifier.testTag("toggle_expense_alerts_chip"),
                )
              }
              item {
                FilterChip(
                  selected = prefs.includeSelfChanges,
                  onClick = { onToggleIncludeSelf(!prefs.includeSelfChanges) },
                  label = { Text("My Own Edits") },
                  modifier = Modifier.testTag("toggle_self_alerts_chip"),
                )
              }
            }
            FilledTonalButton(
              onClick = {
                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onRunBackgroundCheckNow()
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_bg_check_now_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.check_updates_now))
            }
          }
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(
            selected = selectedCategory == null,
            onClick = { selectedCategory = null },
            label = { Text("All (${logs.size})") },
            modifier = Modifier.testTag("activity_filter_all"),
          )
        }
        items(ActivityCategory.entries) { category ->
          val count = logs.count { it.category == category.name }
          FilterChip(
            selected = selectedCategory == category,
            onClick = { selectedCategory = category },
            label = { Text("${category.label} ($count)") },
            modifier = Modifier.testTag("activity_filter_${category.name.lowercase()}"),
          )
        }
      }

      if (filteredLogs.isEmpty()) {
        EmptyStateCard(
          icon = Icons.Filled.History,
          title = "No recorded updates yet",
          message = "Meal edits, bazaar entries, and deletions will appear here with who changed the value.",
        )
        Spacer(modifier = Modifier.height(16.dp))
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f, fill = false),
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(filteredLogs, key = { it.id.ifBlank { "${it.title}_${it.dateEpochDay}_${it.actorUid}" } }) { log ->
            ActivityLogRowCard(
              log = log,
              members = members,
              currentUserId = currentUserId,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActivityLogRowCard(
  log: MessActivityLog,
  members: List<MessMember>,
  currentUserId: String,
) {
  val isMeal = log.category == ActivityCategory.MEAL.name
  val icon = if (isMeal) Icons.Filled.Restaurant else Icons.Filled.ReceiptLong
  val accentColor =
    when (log.action) {
      ActivityAction.REMOVED.name -> MaterialTheme.colorScheme.error
      ActivityAction.UPDATED.name -> MaterialTheme.colorScheme.tertiary
      else -> if (isMeal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    }

  val resolvedActor =
    remember(log.actorUid, log.actorName, members) {
      MessCalculator.resolveActorName(
        uid = log.actorUid,
        members = members,
        currentUserId = currentUserId,
        explicitActorName = log.actorName,
      )
    }

  val actionLabel =
    ActivityAction.entries.firstOrNull { it.name == log.action }?.label ?: log.action

  val dateText =
    remember(log.dateEpochDay) {
      if (log.dateEpochDay > 0L) {
        LocalDate.ofEpochDay(log.dateEpochDay)
          .format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
      } else {
        ""
      }
    }

  val timeText =
    remember(log.createdAt) {
      val millis = log.createdAtMillis()
      if (millis > 0L) {
        SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
      } else {
        dateText
      }
    }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth().testTag("activity_log_item_${log.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(
        modifier =
          Modifier.size(38.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = log.category,
          tint = accentColor,
          modifier = Modifier.size(18.dp),
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = log.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            color = accentColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = actionLabel,
              style = MaterialTheme.typography.labelSmall,
              color = accentColor,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }

        Text(
          text = log.changeDetail,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text =
              if (log.actorUid == currentUserId) "$resolvedActor (You)" else resolvedActor,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
          if (timeText.isNotBlank()) {
            Text(
              text = "· $timeText",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
