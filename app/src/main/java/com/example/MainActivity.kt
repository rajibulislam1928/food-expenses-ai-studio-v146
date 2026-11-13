package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.preferences.ExpenseBannerPreferences
import com.example.data.preferences.NotificationPreferences
import com.example.data.repository.MessRepository
import com.example.notifications.AppVisibilityTracker
import com.example.notifications.MessBackgroundSyncWorker
import com.example.notifications.MessNotificationHelper
import com.example.ui.MessTab
import com.example.ui.MessViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MessAppScaffold
import com.example.ui.theme.MessExpenseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private data class NotificationDeepLink(
  val targetTab: MessTab?,
  val openActivitySheet: Boolean,
)

class MainActivity : ComponentActivity() {
  private val deepLinkFlow = MutableStateFlow<NotificationDeepLink?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    MessNotificationHelper.ensureChannel(applicationContext)
    handleNotificationIntent(intent)
    setContent {
      val deepLink by deepLinkFlow.asStateFlow().collectAsStateWithLifecycle()
      MessExpenseTheme {
        AppNavigation(
          pendingDeepLink = deepLink,
          onConsumeDeepLink = { deepLinkFlow.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNotificationIntent(intent)
  }

  override fun onStart() {
    super.onStart()
    AppVisibilityTracker.onAppStarted()
  }

  override fun onStop() {
    super.onStop()
    AppVisibilityTracker.onAppStopped()
  }

  private fun handleNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val rawTab = intent.getStringExtra(MessNotificationHelper.EXTRA_OPEN_TAB)
    val openSheet =
      intent.getBooleanExtra(MessNotificationHelper.EXTRA_OPEN_ACTIVITY_SHEET, false)
    val targetTab = MessTab.entries.firstOrNull { it.name == rawTab }
    if (targetTab != null || openSheet) {
      deepLinkFlow.value = NotificationDeepLink(targetTab = targetTab, openActivitySheet = openSheet)
      intent.removeExtra(MessNotificationHelper.EXTRA_OPEN_TAB)
      intent.removeExtra(MessNotificationHelper.EXTRA_OPEN_ACTIVITY_SHEET)
    }
  }
}

@Composable
private fun AppNavigation(
  pendingDeepLink: NotificationDeepLink?,
  onConsumeDeepLink: () -> Unit,
) {
  val currentUser by
    Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      MainFeatureScreen(
        user = user,
        pendingDeepLink = pendingDeepLink,
        onConsumeDeepLink = onConsumeDeepLink,
      )
    }
  }
}

@Composable
private fun MainFeatureScreen(
  user: FirebaseUser,
  pendingDeepLink: NotificationDeepLink?,
  onConsumeDeepLink: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: MessViewModel =
    viewModel(
      key = user.uid,
      factory =
        viewModelFactory {
          initializer {
            val appCtx = context.applicationContext
            val dbId = appCtx.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            val repository = MessRepository(db = db, auth = Firebase.auth)
            val bannerPrefs = ExpenseBannerPreferences(appCtx)
            val notificationPrefs = NotificationPreferences(appCtx)
            MessViewModel(
              repository = repository,
              currentUserId = user.uid,
              userDisplayName = user.displayName ?: "",
              userEmail = user.email ?: "",
              expenseBannerPreferences = bannerPrefs,
              notificationPreferences = notificationPrefs,
              applicationContext = appCtx,
            )
          }
        },
    )

  val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()

  LaunchedEffect(user.uid, notificationPrefs.backgroundEnabled) {
    if (notificationPrefs.backgroundEnabled) {
      MessBackgroundSyncWorker.enqueuePeriodic(context.applicationContext)
    } else {
      MessBackgroundSyncWorker.cancelPeriodic(context.applicationContext)
    }
  }

  LaunchedEffect(pendingDeepLink) {
    val link = pendingDeepLink ?: return@LaunchedEffect
    link.targetTab?.let { viewModel.selectTab(it) }
    if (link.openActivitySheet) {
      viewModel.openActivitySheet()
    }
    onConsumeDeepLink()
  }

  MessAppScaffold(
    viewModel = viewModel,
    userDisplayName = user.displayName ?: "",
    userEmail = user.email ?: "",
    onSignOut = {
      MessBackgroundSyncWorker.cancelPeriodic(context.applicationContext)
      signOutUser(
        credentialManager = credentialManager,
        scope = scope,
      )
    },
  )
}
