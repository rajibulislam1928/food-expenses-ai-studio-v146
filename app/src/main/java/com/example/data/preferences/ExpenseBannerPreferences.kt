package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.expenseBannerDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "mess_expense_banner_prefs")

class ExpenseBannerPreferences(private val context: Context) {

  fun isBannerVisible(currentVersion: String): Flow<Boolean> =
    context.expenseBannerDataStore.data
      .catch { exception ->
        if (exception is IOException) {
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { prefs -> prefs[KEY_DISMISSED_VERSION] != currentVersion }

  suspend fun dismissBannerForVersion(version: String) {
    context.expenseBannerDataStore.edit { prefs -> prefs[KEY_DISMISSED_VERSION] = version }
  }

  companion object {
    val KEY_DISMISSED_VERSION = stringPreferencesKey("dismissed_expense_banner_version")
  }
}
