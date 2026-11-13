package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.preferences.NotificationPreferences
import com.example.data.repository.MessRepository
import com.example.domain.MessCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class MessBackgroundSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
    val prefs = NotificationPreferences(applicationContext)
    val snapshot = prefs.getSnapshot()

    if (!snapshot.backgroundEnabled) return Result.success()
    if (!snapshot.notifyOnMeals && !snapshot.notifyOnExpenses) return Result.success()
    if (!MessNotificationHelper.hasNotificationPermission(applicationContext)) {
      return Result.success()
    }

    val minTimestamp =
      maxOf(snapshot.baselineMillis, snapshot.lastSeenActivityMillis).takeIf { it > 0L }
        ?: prefs.ensureBaselineInitialized()

    return try {
      val repository = MessRepository(applicationContext)
      val groupLogsList = repository.fetchRecentActivityLogsOnce(uid).getOrThrow()
      val notifiedIds = mutableListOf<String>()

      groupLogsList.forEach { (group, logs) ->
        val pending =
          MessCalculator.filterPendingActivityNotifications(
            logs = logs,
            currentUid = uid,
            seenIds = snapshot.notifiedActivityIds + notifiedIds,
            minTimestampMillis = minTimestamp,
            notifyOnMeals = snapshot.notifyOnMeals,
            notifyOnExpenses = snapshot.notifyOnExpenses,
            includeSelfChanges = snapshot.includeSelfChanges,
          )
        if (pending.isNotEmpty()) {
          val posted =
            MessNotificationHelper.postActivityNotifications(
              context = applicationContext,
              groupName = group.name,
              logs = pending,
            )
          if (posted > 0) {
            notifiedIds.addAll(pending.map { it.id })
          }
        }
      }

      if (notifiedIds.isNotEmpty()) {
        prefs.markActivityIdsNotified(notifiedIds)
      }
      Result.success()
    } catch (cancel: CancellationException) {
      throw cancel
    } catch (err: Exception) {
      Log.w(TAG, "Background activity sync failed: ${err.message}")
      if (runAttemptCount < 2) Result.retry() else Result.success()
    }
  }

  companion object {
    private const val TAG = "MessBgSyncWorker"
    const val PERIODIC_WORK_NAME = "mess_background_sync_periodic"
    const val IMMEDIATE_WORK_NAME = "mess_background_sync_immediate"

    fun enqueuePeriodic(context: Context) {
      val constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request =
        PeriodicWorkRequestBuilder<MessBackgroundSyncWorker>(15, TimeUnit.MINUTES)
          .setConstraints(constraints)
          .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniquePeriodicWork(
          PERIODIC_WORK_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          request,
        )
    }

    fun cancelPeriodic(context: Context) {
      WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun enqueueImmediateCheck(context: Context) {
      val constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request =
        OneTimeWorkRequestBuilder<MessBackgroundSyncWorker>()
          .setConstraints(constraints)
          .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(
          IMMEDIATE_WORK_NAME,
          ExistingWorkPolicy.REPLACE,
          request,
        )
    }
  }
}
