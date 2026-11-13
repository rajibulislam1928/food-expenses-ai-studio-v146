package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActivityAction
import com.example.data.model.ActivityCategory
import com.example.data.model.MessActivityLog
import com.example.ui.MessTab

object MessNotificationHelper {
  const val CHANNEL_ID = "mess_activity_alerts"
  const val EXTRA_OPEN_TAB = "extra_open_tab"
  const val EXTRA_OPEN_ACTIVITY_SHEET = "extra_open_activity_sheet"
  private const val GROUP_KEY = "com.example.FOOD_EXPENSES_UPDATES"
  private const val SUMMARY_NOTIFICATION_ID = 41000

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
          ).apply {
            description = context.getString(R.string.notification_channel_desc)
          }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    val osGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    return osGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun buildNotificationContent(
    log: MessActivityLog,
    groupName: String = "",
  ): Pair<String, String> {
    val actor = log.actorName.trim().ifBlank { "Roommate" }
    val verb =
      when (log.action) {
        ActivityAction.ADDED.name -> "added"
        ActivityAction.UPDATED.name -> "updated"
        ActivityAction.REMOVED.name -> "removed"
        else -> "updated"
      }
    val noun =
      when (log.category) {
        ActivityCategory.MEAL.name -> "meals"
        ActivityCategory.EXPENSE.name -> "expense"
        else -> "entry"
      }
    val title = "$actor $verb $noun"
    val bodyPrefix = if (groupName.isNotBlank()) "[$groupName] " else ""
    val body = "$bodyPrefix${log.title}: ${log.changeDetail}"
    return title to body
  }

  @SuppressLint("MissingPermission")
  fun postActivityNotifications(
    context: Context,
    groupName: String,
    logs: List<MessActivityLog>,
  ): Int {
    if (logs.isEmpty() || !hasNotificationPermission(context)) return 0
    ensureChannel(context)

    val notificationManager = NotificationManagerCompat.from(context)
    val itemsToPost = logs.takeLast(4)

    itemsToPost.forEachIndexed { index, log ->
      val (title, body) = buildNotificationContent(log, groupName)
      val targetTab =
        if (log.category == ActivityCategory.MEAL.name) MessTab.MEALS.name else MessTab.EXPENSES.name
      val contentIntent = createLaunchIntent(context, targetTab = targetTab, openSheet = true)
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          (log.id.hashCode() and 0x0FFF_FFFF) + index,
          contentIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_notification_mess)
          .setContentTitle(title)
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body))
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setCategory(NotificationCompat.CATEGORY_SOCIAL)
          .setGroup(GROUP_KEY)
          .setAutoCancel(true)
          .setContentIntent(pendingIntent)

      notificationManager.notify(41100 + ((log.id.hashCode() and 0x7FFF) % 5000), builder.build())
    }

    if (logs.size > 1) {
      val inbox =
        NotificationCompat.InboxStyle()
          .setBigContentTitle("${logs.size} Mess Updates")
          .setSummaryText(groupName.ifBlank { context.getString(R.string.app_name) })
      logs.takeLast(5).forEach { log ->
        val actor = log.actorName.trim().ifBlank { "Roommate" }
        inbox.addLine("$actor: ${log.title} (${log.changeDetail})")
      }
      val summaryPendingIntent =
        PendingIntent.getActivity(
          context,
          SUMMARY_NOTIFICATION_ID,
          createLaunchIntent(context, targetTab = MessTab.OVERVIEW.name, openSheet = true),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      val summaryNotification =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_notification_mess)
          .setContentTitle("Food Expenses • ${logs.size} updates")
          .setContentText("Tap to see who changed meal or expense values")
          .setStyle(inbox)
          .setGroup(GROUP_KEY)
          .setGroupSummary(true)
          .setAutoCancel(true)
          .setContentIntent(summaryPendingIntent)
          .build()

      notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }

    return itemsToPost.size
  }

  private fun createLaunchIntent(
    context: Context,
    targetTab: String,
    openSheet: Boolean,
  ): Intent =
    Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_OPEN_TAB, targetTab)
      putExtra(EXTRA_OPEN_ACTIVITY_SHEET, openSheet)
    }
}
