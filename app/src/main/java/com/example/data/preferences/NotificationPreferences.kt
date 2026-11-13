package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "mess_notification_prefs")

data class NotificationPrefsSnapshot(
  val backgroundEnabled: Boolean = true,
  val notifyOnMeals: Boolean = true,
  val notifyOnExpenses: Boolean = true,
  val includeSelfChanges: Boolean = false,
  val lastSeenActivityMillis: Long = 0L,
  val baselineMillis: Long = 0L,
  val notifiedActivityIds: Set<String> = emptySet(),
)

class NotificationPreferences(private val context: Context) {

  fun observePrefs(): Flow<NotificationPrefsSnapshot> =
    context.notificationDataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs -> prefs.toSnapshot() }

  suspend fun getSnapshot(): NotificationPrefsSnapshot = observePrefs().first()

  suspend fun ensureBaselineInitialized(nowMillis: Long = System.currentTimeMillis()): Long {
    var resultBaseline = 0L
    context.notificationDataStore.edit { prefs ->
      val existing = prefs[KEY_BASELINE_MILLIS]
      if (existing != null && existing > 0L) {
        resultBaseline = existing
      } else {
        // Seed slightly in the past so immediately created items still trigger, while old backlog doesn't spam
        resultBaseline = (nowMillis - 5_000L).coerceAtLeast(0L)
        prefs[KEY_BASELINE_MILLIS] = resultBaseline
      }
    }
    return resultBaseline
  }

  suspend fun setBackgroundEnabled(enabled: Boolean) {
    context.notificationDataStore.edit { prefs ->
      prefs[KEY_BG_ENABLED] = enabled
    }
  }

  suspend fun setNotifyOnMeals(enabled: Boolean) {
    context.notificationDataStore.edit { prefs ->
      prefs[KEY_NOTIFY_MEALS] = enabled
    }
  }

  suspend fun setNotifyOnExpenses(enabled: Boolean) {
    context.notificationDataStore.edit { prefs ->
      prefs[KEY_NOTIFY_EXPENSES] = enabled
    }
  }

  suspend fun setIncludeSelfChanges(enabled: Boolean) {
    context.notificationDataStore.edit { prefs ->
      prefs[KEY_INCLUDE_SELF] = enabled
    }
  }

  suspend fun markAllActivityRead(latestMillis: Long = System.currentTimeMillis()) {
    context.notificationDataStore.edit { prefs ->
      val prev = prefs[KEY_LAST_SEEN_MILLIS] ?: 0L
      prefs[KEY_LAST_SEEN_MILLIS] = maxOf(prev, latestMillis)
    }
  }

  suspend fun markActivityIdsNotified(ids: Collection<String>) {
    val cleanIds = ids.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanIds.isEmpty()) return
    context.notificationDataStore.edit { prefs ->
      val existing = prefs[KEY_NOTIFIED_IDS]?.toList().orEmpty()
      val merged = (existing + cleanIds).distinct().takeLast(MAX_TRACKED_IDS).toSet()
      prefs[KEY_NOTIFIED_IDS] = merged
    }
  }

  private fun Preferences.toSnapshot(): NotificationPrefsSnapshot =
    NotificationPrefsSnapshot(
      backgroundEnabled = this[KEY_BG_ENABLED] ?: true,
      notifyOnMeals = this[KEY_NOTIFY_MEALS] ?: true,
      notifyOnExpenses = this[KEY_NOTIFY_EXPENSES] ?: true,
      includeSelfChanges = this[KEY_INCLUDE_SELF] ?: false,
      lastSeenActivityMillis = this[KEY_LAST_SEEN_MILLIS] ?: 0L,
      baselineMillis = this[KEY_BASELINE_MILLIS] ?: 0L,
      notifiedActivityIds = this[KEY_NOTIFIED_IDS] ?: emptySet(),
    )

  companion object {
    const val MAX_TRACKED_IDS = 200
    val KEY_BG_ENABLED = booleanPreferencesKey("background_notifications_enabled")
    val KEY_NOTIFY_MEALS = booleanPreferencesKey("notify_on_meals")
    val KEY_NOTIFY_EXPENSES = booleanPreferencesKey("notify_on_expenses")
    val KEY_INCLUDE_SELF = booleanPreferencesKey("include_self_changes")
    val KEY_LAST_SEEN_MILLIS = longPreferencesKey("last_seen_activity_epoch_millis")
    val KEY_BASELINE_MILLIS = longPreferencesKey("last_notification_baseline_millis")
    val KEY_NOTIFIED_IDS = stringSetPreferencesKey("notified_activity_ids")
  }
}
