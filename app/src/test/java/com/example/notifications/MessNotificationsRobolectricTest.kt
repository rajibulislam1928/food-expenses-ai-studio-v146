package com.example.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.model.ActivityAction
import com.example.data.model.ActivityCategory
import com.example.data.model.MessActivityLog
import com.example.data.model.MessMember
import com.example.data.preferences.NotificationPreferences
import com.example.domain.MessCalculator
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessNotificationsRobolectricTest {

  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(app).isEmpty()) {
      FirebaseApp.initializeApp(app)
    }
  }

  @Test
  fun resolveActorName_andFormatMealDiff_produceClearAttribution() {
    val members =
      listOf(
        MessMember(id = "m1", name = "Karim", linkedUid = "uid_karim"),
        MessMember(id = "m2", name = "Nadia", linkedUid = "uid_nadia"),
      )

    assertEquals("Nadia", MessCalculator.resolveActorName("uid_nadia", members))
    assertEquals(
      "Tariq",
      MessCalculator.resolveActorName(
        uid = "uid_tariq",
        members = members,
        currentUserId = "uid_tariq",
        currentUserDisplayName = "Tariq",
      ),
    )

    val diff =
      MessCalculator.formatMealDiff(
        prevLunch = 1.0,
        prevDinner = 1.0,
        prevGuest = 0.0,
        newLunch = 2.0,
        newDinner = 1.0,
        newGuest = 1.0,
      )
    assertEquals("Lunch 1 → 2, Guest 0 → 1", diff)
  }

  @Test
  fun filterPendingActivityNotifications_respectsCategorySelfAndSeenIds() {
    val now = System.currentTimeMillis()
    val tsRecent = Timestamp(Date(now + 2_000L))
    val tsOld = Timestamp(Date(now - 60_000L))

    val logs =
      listOf(
        MessActivityLog(
          id = "log_meal_other",
          category = ActivityCategory.MEAL.name,
          action = ActivityAction.UPDATED.name,
          actorUid = "uid_other",
          actorName = "Nadia",
          title = "Meals for Karim",
          changeDetail = "Lunch 1 → 2",
          createdAt = tsRecent,
        ),
        MessActivityLog(
          id = "log_expense_self",
          category = ActivityCategory.EXPENSE.name,
          action = ActivityAction.ADDED.name,
          actorUid = "uid_me",
          actorName = "Me",
          title = "Chicken",
          changeDetail = "৳450 • Bazaar",
          createdAt = tsRecent,
        ),
        MessActivityLog(
          id = "log_old",
          category = ActivityCategory.EXPENSE.name,
          action = ActivityAction.ADDED.name,
          actorUid = "uid_other",
          actorName = "Nadia",
          title = "Old Milk",
          changeDetail = "৳90 • Bazaar",
          createdAt = tsOld,
        ),
        MessActivityLog(
          id = "log_seen",
          category = ActivityCategory.EXPENSE.name,
          action = ActivityAction.REMOVED.name,
          actorUid = "uid_other",
          actorName = "Nadia",
          title = "Dup Sugar",
          changeDetail = "Removed ৳120",
          createdAt = tsRecent,
        ),
      )

    val pendingDefault =
      MessCalculator.filterPendingActivityNotifications(
        logs = logs,
        currentUid = "uid_me",
        seenIds = setOf("log_seen"),
        minTimestampMillis = now,
        notifyOnMeals = true,
        notifyOnExpenses = true,
        includeSelfChanges = false,
      )
    assertEquals(listOf("log_meal_other"), pendingDefault.map { it.id })

    val pendingIncludeSelfNoMeals =
      MessCalculator.filterPendingActivityNotifications(
        logs = logs,
        currentUid = "uid_me",
        seenIds = setOf("log_seen"),
        minTimestampMillis = now,
        notifyOnMeals = false,
        notifyOnExpenses = true,
        includeSelfChanges = true,
      )
    assertEquals(listOf("log_expense_self"), pendingIncludeSelfNoMeals.map { it.id })
  }

  @Test
  fun activityLogUnreadStateTracksExplicitReadCutoffAndSelfPreference() {
    val log =
      MessActivityLog(
        id = "unread_log",
        actorUid = "uid_other",
        createdAt = Timestamp(Date(2_000L)),
      )

    assertTrue(MessCalculator.isActivityLogUnread(log, "uid_me", 1_999L, false))
    assertFalse(MessCalculator.isActivityLogUnread(log, "uid_me", 2_000L, false))
    assertFalse(MessCalculator.isActivityLogUnread(log.copy(actorUid = "uid_me"), "uid_me", 1_999L, false))
    assertTrue(MessCalculator.isActivityLogUnread(log.copy(actorUid = "uid_me"), "uid_me", 1_999L, true))
  }

  @Test
  fun notificationPreferences_deduplicatesSeenIdsAndSavesToggles() = runBlocking {
    val prefs = NotificationPreferences(app)
    prefs.setBackgroundEnabled(true)
    prefs.setNotifyOnMeals(false)
    prefs.setNotifyOnExpenses(true)
    prefs.setIncludeSelfChanges(true)
    prefs.markActivityIdsNotified(listOf("id_1", "id_2"))
    prefs.markActivityIdsNotified(listOf("id_2", "id_3"))
    prefs.markAllActivityRead(2_000L)
    prefs.markAllActivityRead(1_000L)

    val snap = prefs.getSnapshot()
    assertTrue(snap.backgroundEnabled)
    assertFalse(snap.notifyOnMeals)
    assertTrue(snap.notifyOnExpenses)
    assertTrue(snap.includeSelfChanges)
    assertEquals(2_000L, snap.lastSeenActivityMillis)
    assertTrue(setOf("id_1", "id_2", "id_3").all { it in snap.notifiedActivityIds })
  }

  @Test
  fun clearActivityNotifications_removesSummaryAndIndividualAlerts() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val manager = app.getSystemService(NotificationManager::class.java)
    shadowOf(manager).setNotificationsEnabled(true)
    val logs = listOf(
      MessActivityLog(id = "clear_1", actorUid = "other", actorName = "A", title = "Meal"),
      MessActivityLog(id = "clear_2", actorUid = "other", actorName = "B", title = "Expense"),
    )

    assertEquals(2, MessNotificationHelper.postActivityNotifications(app, "Mess", logs))
    assertTrue(manager.activeNotifications.any { it.id == 41000 })
    assertTrue(manager.activeNotifications.any { it.id in 41100..46099 })

    MessNotificationHelper.clearActivityNotifications(app)

    assertTrue(manager.activeNotifications.none { it.id == 41000 || it.id in 41100..46099 })
  }

  @Test
  fun postActivityNotifications_postsActorAttributedNotifications() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    shadowOf(nm).setNotificationsEnabled(true)

    val logs =
      listOf(
        MessActivityLog(
          id = "n1",
          category = ActivityCategory.MEAL.name,
          action = ActivityAction.UPDATED.name,
          actorUid = "uid_nadia",
          actorName = "Nadia",
          title = "Meals for Karim",
          changeDetail = "Lunch 1 → 2, Dinner 1",
        ),
        MessActivityLog(
          id = "n2",
          category = ActivityCategory.EXPENSE.name,
          action = ActivityAction.ADDED.name,
          actorUid = "uid_rahim",
          actorName = "Rahim",
          title = "Rice 10kg",
          changeDetail = "৳750 • Bazaar",
        ),
      )

    val postedCount = MessNotificationHelper.postActivityNotifications(app, "Emerald Mess", logs)
    assertEquals(2, postedCount)

    val allPosted = shadowOf(nm).allNotifications
    assertTrue(allPosted.size >= 2)
    val texts =
      allPosted.map { notif ->
        val extras = notif.extras
        "${extras.getString(Notification.EXTRA_TITLE)} | ${extras.getCharSequence(Notification.EXTRA_TEXT)}"
      }
    assertTrue(texts.any { it.contains("Nadia") && it.contains("Lunch 1 → 2") })
    assertTrue(texts.any { it.contains("Rahim") && it.contains("Rice 10kg") })
  }

  @Test
  fun backgroundSyncWorker_whenSignedOut_succeedsWithoutFailure() = runBlocking {
    FirebaseAuth.getInstance().signOut()
    val worker = TestListenableWorkerBuilder<MessBackgroundSyncWorker>(app).build()
    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.success(), result)
  }
}
