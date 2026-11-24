package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.export.DownloadPhase
import com.example.data.export.DownloadedReportResult
import com.example.data.export.ExportScope
import com.example.data.export.ReportDownloader
import com.example.data.export.ReportFormat
import com.example.data.export.XlsxCodec
import com.example.data.model.CycleMode
import com.example.data.model.CycleStatus
import com.example.data.model.MealEntry
import com.example.data.model.MemberRole
import com.example.data.model.MessActivityLog
import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import com.example.data.model.UserProfile
import com.example.data.preferences.ExpenseBannerPreferences
import com.example.data.preferences.NotificationPreferences
import com.example.data.preferences.NotificationPrefsSnapshot
import com.example.data.repository.MessRepository
import com.example.data.unwrapFirestoreException
import com.example.domain.BulkBazaarItem
import com.example.domain.CycleSummary
import com.example.domain.MemberStanding
import com.example.domain.MessCalculator
import com.example.notifications.AppVisibilityTracker
import com.example.notifications.MessBackgroundSyncWorker
import com.example.notifications.MessNotificationHelper
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MessTab {
  OVERVIEW,
  MEALS,
  EXPENSES,
  LEDGER,
}

enum class TxFilter(val label: String) {
  ALL("All"),
  BAZAAR("Bazaar"),
  FIXED_UTILITY("Fixed Bills"),
  DEPOSIT("Deposits"),
  SETTLEMENT("Settlements"),
}

data class MessDashboardData(
  val currentUserId: String,
  val userProfile: UserProfile?,
  val allGroups: List<MessGroup>,
  val activeGroup: MessGroup,
  val cycles: List<MessCycle>,
  val activeCycle: MessCycle?,
  val members: List<MessMember>,
  val cycleMeals: List<MealEntry>,
  val cycleTransactions: List<MessTransaction>,
  val allGroupMeals: List<MealEntry>,
  val allGroupTransactions: List<MessTransaction>,
  val activityLogs: List<MessActivityLog> = emptyList(),
  val summary: CycleSummary,
  val currentMemberStanding: MemberStanding?,
)

sealed interface MessUiState {
  data object Loading : MessUiState
  data class NeedsGroup(val allGroups: List<MessGroup>) : MessUiState
  data class Ready(val data: MessDashboardData) : MessUiState
  data class Error(val message: String) : MessUiState
}

sealed interface DownloadUiState {
  data object Hidden : DownloadUiState
  data class ReadyToDownload(
    val initialFormat: ReportFormat = ReportFormat.XLSX,
    val initialScope: ExportScope = ExportScope.CURRENT_MONTH,
  ) : DownloadUiState
  data class InProgress(
    val format: ReportFormat,
    val scope: ExportScope,
    val fileName: String,
    val progress: Float,
    val statusText: String,
    val bytesWritten: Long = 0L,
    val totalBytes: Long = 0L,
  ) : DownloadUiState
  data class Completed(val result: DownloadedReportResult) : DownloadUiState
  data class Failed(
    val format: ReportFormat,
    val scope: ExportScope,
    val message: String,
  ) : DownloadUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessViewModel(
  private val repository: MessRepository,
  private val currentUserId: String,
  private val userDisplayName: String,
  private val userEmail: String,
  private val expenseBannerPreferences: ExpenseBannerPreferences? = null,
  private val notificationPreferences: NotificationPreferences? = null,
  private val applicationContext: Context? = null,
  private val appVersionName: String = BuildConfig.VERSION_NAME,
) : ViewModel() {

  private val _selectedTab = MutableStateFlow(MessTab.OVERVIEW)
  val selectedTab: StateFlow<MessTab> = _selectedTab.asStateFlow()

  private val _selectedDateEpochDay = MutableStateFlow(LocalDate.now().toEpochDay())
  val selectedDateEpochDay: StateFlow<Long> = _selectedDateEpochDay.asStateFlow()

  private val _txFilter = MutableStateFlow(TxFilter.ALL)
  val txFilter: StateFlow<TxFilter> = _txFilter.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _lastSyncedMillis = MutableStateFlow(System.currentTimeMillis())
  val lastSyncedMillis: StateFlow<Long> = _lastSyncedMillis.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Hidden)
  val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

  private val _showActivitySheet = MutableStateFlow(false)
  val showActivitySheet: StateFlow<Boolean> = _showActivitySheet.asStateFlow()

  private val _fallbackBannerVisible = MutableStateFlow(true)
  val isExpenseBannerVisible: StateFlow<Boolean> =
    (expenseBannerPreferences?.isBannerVisible(appVersionName) ?: _fallbackBannerVisible)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = false,
      )

  private val _fallbackNotificationPrefs = MutableStateFlow(NotificationPrefsSnapshot())
  val notificationPrefs: StateFlow<NotificationPrefsSnapshot> =
    (notificationPreferences?.observePrefs() ?: _fallbackNotificationPrefs)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = NotificationPrefsSnapshot(),
      )

  private var activeDownloadJob: Job? = null
  private val manualRefreshTrigger = MutableStateFlow(0)
  private var observedGroupForSnackId: String = ""

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(userDisplayName, userEmail).onFailure { err ->
        Log.w(TAG, "Profile ensure warning: ${err.message}")
      }
      notificationPreferences?.ensureBaselineInitialized()
    }
  }

  val uiState: StateFlow<MessUiState> =
    combine(
        repository.observeUserProfile(currentUserId),
        repository.observeUserGroups(currentUserId),
        manualRefreshTrigger,
      ) { profile, groups, _ ->
        profile to groups
      }
      .flatMapLatest { (profile, groups) ->
        if (groups.isEmpty()) {
          flowOf<MessUiState>(MessUiState.NeedsGroup(emptyList()))
        } else {
          val activeGroup =
            groups.firstOrNull { it.id == profile?.activeGroupId } ?: groups.first()
          combine(
            repository.observeGroupCycles(activeGroup.id, currentUserId),
            repository.observeGroupMembers(activeGroup.id, currentUserId),
            repository.observeGroupMealEntries(activeGroup.id, currentUserId),
            repository.observeGroupTransactions(activeGroup.id, currentUserId),
            repository.observeGroupActivityLogs(activeGroup.id, currentUserId),
          ) { cycles, members, meals, transactions, activityLogs ->
            val activeCycle =
              cycles.firstOrNull { it.id == activeGroup.activeCycleId }
                ?: cycles.firstOrNull { it.status == CycleStatus.ACTIVE.name }
                ?: cycles.firstOrNull()

            val cycleId = activeCycle?.id.orEmpty()
            val cycleMeals = meals.filter { it.cycleId == cycleId }
            val cycleTransactions = transactions.filter { it.cycleId == cycleId }

            val summary =
              MessCalculator.computeCycleSummary(
                members = members,
                mealEntries = cycleMeals,
                transactions = cycleTransactions,
              )

            val myStanding =
              summary.standings.firstOrNull { it.member.linkedUid == currentUserId }

            MessDashboardData(
              currentUserId = currentUserId,
              userProfile = profile,
              allGroups = groups,
              activeGroup = activeGroup,
              cycles = cycles,
              activeCycle = activeCycle,
              members = members,
              cycleMeals = cycleMeals,
              cycleTransactions = cycleTransactions,
              allGroupMeals = meals,
              allGroupTransactions = transactions,
              activityLogs = activityLogs,
              summary = summary,
              currentMemberStanding = myStanding,
            )
          }
            .onEach { bundle ->
              _lastSyncedMillis.value = System.currentTimeMillis()
              // Ensure newly joined roommates are synced to existing group documents
              val needsPropagation =
                bundle.cycles.any { it.memberUids.toSet() != bundle.activeGroup.memberUids.toSet() } ||
                  bundle.members.any { it.memberUids.toSet() != bundle.activeGroup.memberUids.toSet() } ||
                  bundle.activityLogs.any { it.memberUids.toSet() != bundle.activeGroup.memberUids.toSet() }
              if (needsPropagation) {
                viewModelScope.launch {
                  repository.propagateMemberUidsIfNeeded(
                    group = bundle.activeGroup,
                    cycles = bundle.cycles,
                    members = bundle.members,
                    meals = bundle.cycleMeals,
                    transactions = bundle.cycleTransactions,
                    activityLogs = bundle.activityLogs,
                  )
                }
              }
              processActivityLogNotifications(bundle)
            }
            .map<MessDashboardData, MessUiState> { MessUiState.Ready(it) }
        }
      }
      .catch { error ->
        val firestoreError = error.unwrapFirestoreException()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing mess data", rootError)
        emit(MessUiState.Error(rootError.localizedMessage ?: "Failed to sync mess data."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MessUiState.Loading,
      )

  val unreadActivityCount: StateFlow<Int> =
    combine(uiState, notificationPrefs) { state, prefs ->
      val ready = state as? MessUiState.Ready ?: return@combine 0
      val seenAfter = prefs.lastSeenActivityMillis
      ready.data.activityLogs.count { log ->
        MessCalculator.isActivityLogUnread(
          log = log,
          currentUid = currentUserId,
          lastSeenMillis = seenAfter,
          includeSelfChanges = prefs.includeSelfChanges,
        )
      }
    }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0,
      )

  private fun processActivityLogNotifications(bundle: MessDashboardData) {
    val prefsStore = notificationPreferences ?: return
    val isFirstEmissionForGroup = observedGroupForSnackId != bundle.activeGroup.id
    observedGroupForSnackId = bundle.activeGroup.id
    viewModelScope.launch {
      val snapshot = prefsStore.getSnapshot()
      val baseline =
        snapshot.baselineMillis.takeIf { it > 0L } ?: prefsStore.ensureBaselineInitialized()
      val minTs = maxOf(baseline, snapshot.lastSeenActivityMillis)
      val pending =
        MessCalculator.filterPendingActivityNotifications(
          logs = bundle.activityLogs,
          currentUid = currentUserId,
          seenIds = snapshot.notifiedActivityIds,
          minTimestampMillis = minTs,
          notifyOnMeals = snapshot.notifyOnMeals,
          notifyOnExpenses = snapshot.notifyOnExpenses,
          includeSelfChanges = snapshot.includeSelfChanges,
        )
      if (pending.isEmpty()) return@launch

      prefsStore.markActivityIdsNotified(pending.map { it.id })

      val appCtx = applicationContext
      if (snapshot.backgroundEnabled && appCtx != null && (!AppVisibilityTracker.isForeground || snapshot.includeSelfChanges)) {
        MessNotificationHelper.postActivityNotifications(
          context = appCtx,
          groupName = bundle.activeGroup.name,
          logs = pending,
        )
      }

      if (AppVisibilityTracker.isForeground && !isFirstEmissionForGroup) {
        val remoteLatest = pending.lastOrNull { it.actorUid != currentUserId }
        if (remoteLatest != null) {
          val (title, _) = MessNotificationHelper.buildNotificationContent(remoteLatest)
          _snackbarMessage.value = "$title • ${remoteLatest.changeDetail}"
        }
      }
    }
  }

  private fun currentActorName(members: List<MessMember>): String =
    MessCalculator.resolveActorName(
      uid = currentUserId,
      members = members,
      currentUserId = currentUserId,
      currentUserDisplayName = userDisplayName,
    )

  fun openActivitySheet() {
    _showActivitySheet.value = true
  }

  fun dismissActivitySheet() {
    _showActivitySheet.value = false
  }

  fun markAllActivityRead() {
    val logs = (uiState.value as? MessUiState.Ready)?.data?.activityLogs.orEmpty()
    val latestMillis = maxOf(System.currentTimeMillis(), logs.maxOfOrNull { it.createdAtMillis() } ?: 0L)
    notificationPreferences?.let { prefs ->
      viewModelScope.launch {
        prefs.markAllActivityRead(latestMillis)
      }
    }
  }

  fun setBackgroundNotificationsEnabled(enabled: Boolean) {
    _fallbackNotificationPrefs.value = _fallbackNotificationPrefs.value.copy(backgroundEnabled = enabled)
    notificationPreferences?.let { prefs ->
      viewModelScope.launch { prefs.setBackgroundEnabled(enabled) }
    }
    applicationContext?.let { ctx ->
      if (enabled) MessBackgroundSyncWorker.enqueuePeriodic(ctx)
      else MessBackgroundSyncWorker.cancelPeriodic(ctx)
    }
  }

  fun setNotifyOnMeals(enabled: Boolean) {
    _fallbackNotificationPrefs.value = _fallbackNotificationPrefs.value.copy(notifyOnMeals = enabled)
    notificationPreferences?.let { prefs ->
      viewModelScope.launch { prefs.setNotifyOnMeals(enabled) }
    }
  }

  fun setNotifyOnExpenses(enabled: Boolean) {
    _fallbackNotificationPrefs.value = _fallbackNotificationPrefs.value.copy(notifyOnExpenses = enabled)
    notificationPreferences?.let { prefs ->
      viewModelScope.launch { prefs.setNotifyOnExpenses(enabled) }
    }
  }

  fun setIncludeSelfChanges(enabled: Boolean) {
    _fallbackNotificationPrefs.value = _fallbackNotificationPrefs.value.copy(includeSelfChanges = enabled)
    notificationPreferences?.let { prefs ->
      viewModelScope.launch { prefs.setIncludeSelfChanges(enabled) }
    }
  }

  fun runBackgroundCheckNow() {
    val ctx = applicationContext ?: return
    MessBackgroundSyncWorker.enqueueImmediateCheck(ctx)
    _snackbarMessage.value = "Checking cloud activity in background…"
  }

  fun selectTab(tab: MessTab) {
    _selectedTab.value = tab
  }

  fun selectDate(epochDay: Long) {
    _selectedDateEpochDay.value = epochDay
  }

  fun selectTxFilter(filter: TxFilter) {
    _txFilter.value = filter
  }

  fun dismissExpenseBanner() {
    _fallbackBannerVisible.value = false
    val prefs = expenseBannerPreferences ?: return
    viewModelScope.launch {
      prefs.dismissBannerForVersion(appVersionName)
    }
  }

  fun consumeSnackbar() {
    _snackbarMessage.value = null
  }

  fun showSnackbar(message: String) {
    _snackbarMessage.value = message
  }

  fun triggerManualSync() {
    manualRefreshTrigger.value += 1
    _lastSyncedMillis.value = System.currentTimeMillis()
    _snackbarMessage.value = "Synced with cloud ledger"
  }

  fun createMessGroup(
    name: String,
    currencySymbol: String,
    cycleMode: CycleMode,
    creatorMemberName: String,
    initialMembersCsv: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val extras =
        initialMembersCsv
          .split(",", "\n")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
      val result =
        repository.createMessGroup(
          name = name,
          currencySymbol = currencySymbol,
          cycleMode = cycleMode,
          creatorMemberName = creatorMemberName,
          initialMemberNames = extras,
        )
      _isBusy.value = false
      result
        .onSuccess {
          _snackbarMessage.value = "Mess group created!"
          onSuccess()
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "Failed to create mess group."
        }
    }
  }

  fun joinMessGroup(
    inviteCode: String,
    memberDisplayName: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val result = repository.joinMessGroupByInviteCode(inviteCode, memberDisplayName)
      _isBusy.value = false
      result
        .onSuccess {
          _snackbarMessage.value = "Joined mess group!"
          onSuccess()
        }
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "Invalid or expired invite code."
        }
    }
  }

  fun switchGroup(groupId: String) {
    viewModelScope.launch {
      repository.setActiveGroupId(groupId).onFailure { err ->
        _snackbarMessage.value = err.localizedMessage ?: "Could not switch mess group."
      }
    }
  }

  fun addMember(name: String) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can add members."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .addMember(data.activeGroup, name)
        .onSuccess { _snackbarMessage.value = "Added $name to mess" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Failed to add member." }
      _isBusy.value = false
    }
  }

  fun updateMyProfilePhoto(uri: Uri?) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val member = data.members.firstOrNull { it.linkedUid == currentUserId }
    if (member == null) {
      _snackbarMessage.value = "Your account isn't linked to a member row in this group yet."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val encoded = if (uri == null) null else runCatching {
        withContext(Dispatchers.IO) {
          ProfilePhotoCodec.encode(applicationContext ?: throw IllegalStateException("Photo picker unavailable."), uri)
        }
      }.getOrElse { error ->
        _snackbarMessage.value = error.localizedMessage ?: "Couldn't prepare that photo."
        _isBusy.value = false
        return@launch
      }
      repository.updateMyMemberPhoto(data.activeGroup.id, member, encoded)
        .onSuccess { _snackbarMessage.value = if (encoded == null) "Profile photo removed." else "Profile photo updated for this group." }
        .onFailure { error ->
          _snackbarMessage.value = error.localizedMessage ?: "Could not save your profile photo."
        }
      _isBusy.value = false
    }
  }

  fun toggleMemberActive(member: MessMember, isActive: Boolean) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can change member status."
      return
    }
    if (member.isRemoved) return
    viewModelScope.launch {
      repository
        .updateMemberActive(data.activeGroup.id, member, isActive)
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "Failed to update member status."
        }
    }
  }

  fun toggleMemberEditPermission(member: MessMember, canEdit: Boolean) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can change edit permissions."
      return
    }
    if (member.isRemoved) return
    if (member.role == MemberRole.MANAGER.name || member.linkedUid == data.activeGroup.ownerUid) {
      if (!canEdit) _snackbarMessage.value = "The manager's Meals/Expenses access must stay on."
      return
    }
    viewModelScope.launch {
      repository.updateMemberEditPermission(data.activeGroup, member, canEdit)
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Failed to update edit permission." }
    }
  }

  private fun canCurrentUserEdit(data: MessDashboardData): Boolean {
    val actor = data.currentUserId
    return if (data.activeGroup.editPermissionsInitialized) actor in data.activeGroup.editorUids
      else actor in data.activeGroup.memberUids
  }

  fun removeMember(member: MessMember) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can remove members."
      return
    }
    if (member.role == MemberRole.MANAGER.name || member.linkedUid == data.currentUserId) {
      _snackbarMessage.value = "The group manager cannot be removed."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository.removeMember(data.activeGroup, member)
        .onSuccess { _snackbarMessage.value = "${member.name} removed. Their history is kept." }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Could not remove member." }
      _isBusy.value = false
    }
  }

  fun permanentlyDeleteMember(member: MessMember) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can permanently delete a member."
      return
    }
    if (member.role == MemberRole.MANAGER.name || member.linkedUid == data.currentUserId) {
      _snackbarMessage.value = "The group manager cannot be deleted."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository.permanentlyDeleteMember(data.activeGroup, member)
        .onSuccess { count -> _snackbarMessage.value = "${member.name} and $count linked history records deleted." }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Could not permanently delete member." }
      _isBusy.value = false
    }
  }

  fun updateMealEntry(
    memberId: String,
    dateEpochDay: Long,
    lunch: Double,
    dinner: Double,
    guestMeals: Double,
  ) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "This cycle is closed. Switch to or start an active cycle to edit."
      return
    }
    if (data.members.firstOrNull { it.id == memberId }?.isRemoved == true) {
      _snackbarMessage.value = "Removed members' past meal entries are kept read-only."
      return
    }
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val targetMemberName = data.members.firstOrNull { it.id == memberId }?.name.orEmpty()
    val actorName = currentActorName(data.members)
    viewModelScope.launch {
      repository
        .upsertMealEntry(
          group = data.activeGroup,
          cycleId = cycle.id,
          memberId = memberId,
          dateEpochDay = dateEpochDay,
          lunch = lunch,
          dinner = dinner,
          guestMeals = guestMeals,
          targetMemberName = targetMemberName,
          actorName = actorName,
        )
        .onFailure { err ->
          _snackbarMessage.value = err.localizedMessage ?: "Failed to save meal entry."
        }
    }
  }

  fun copyYesterdayMeals(targetEpochDay: Long) {
    if (_isBusy.value) return
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Closed cycles cannot be edited."
      return
    }
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val yesterday = targetEpochDay - 1L
    val yesterdayEntries = data.cycleMeals.filter { it.dateEpochDay == yesterday }
    if (yesterdayEntries.isEmpty()) {
      _snackbarMessage.value = "No meals logged on the previous day to copy."
      return
    }
    val memberNamesById = data.members.associate { it.id to it.name }
    val actorName = currentActorName(data.members)
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .applyDayPresetToMembers(
          group = data.activeGroup,
          cycleId = cycle.id,
          targetDateEpochDay = targetEpochDay,
          entriesToWrite = yesterdayEntries,
          memberNamesById = memberNamesById,
          actorName = actorName,
        )
        .onSuccess { _snackbarMessage.value = "Copied previous day's meals" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Copy failed." }
      _isBusy.value = false
    }
  }

  fun markStandardMealsForActiveMembers(targetEpochDay: Long) {
    if (_isBusy.value) return
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Closed cycles cannot be edited."
      return
    }
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val activeMembers = data.members.filter { it.isActive }
    if (activeMembers.isEmpty()) return
    val entries =
      activeMembers.map { member ->
        MealEntry(
          groupId = data.activeGroup.id,
          cycleId = cycle.id,
          memberId = member.id,
          dateEpochDay = targetEpochDay,
          lunch = 1.0,
          dinner = 1.0,
          guestMeals = 0.0,
        )
      }
    val memberNamesById = data.members.associate { it.id to it.name }
    val actorName = currentActorName(data.members)
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .applyDayPresetToMembers(
          group = data.activeGroup,
          cycleId = cycle.id,
          targetDateEpochDay = targetEpochDay,
          entriesToWrite = entries,
          memberNamesById = memberNamesById,
          actorName = actorName,
        )
        .onSuccess { _snackbarMessage.value = "Marked L+D for active members" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Preset update failed." }
      _isBusy.value = false
    }
  }

  private fun resolvePayerLabel(members: List<MessMember>, paidByMemberId: String): String =
    if (paidByMemberId == MessTransaction.POOL_PAYER_ID) {
      "Mess Pool"
    } else {
      members.firstOrNull { it.id == paidByMemberId }?.name ?: "Member"
    }

  fun addTransaction(
    type: TransactionType,
    title: String,
    notes: String,
    amount: Double,
    paidByMemberId: String,
    dateEpochDay: Long,
  ) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Closed cycles are locked. Start a new cycle to add entries."
      return
    }
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val actorName = currentActorName(data.members)
    val payerLabel = resolvePayerLabel(data.members, paidByMemberId)
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .addTransaction(
          group = data.activeGroup,
          cycleId = cycle.id,
          type = type,
          title = title,
          notes = notes,
          amount = amount,
          paidByMemberId = paidByMemberId,
          dateEpochDay = dateEpochDay,
          actorName = actorName,
          targetMemberName = payerLabel,
        )
        .onSuccess { _snackbarMessage.value = "${type.label} recorded" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Save failed." }
      _isBusy.value = false
    }
  }

  fun addBulkBazaarTransactions(
    items: List<BulkBazaarItem>,
    sharedNotes: String,
    paidByMemberId: String,
    dateEpochDay: Long,
  ) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Closed cycles are locked. Start a new cycle to add entries."
      return
    }
    if (items.isEmpty()) return
    val total = items.sumOf { it.amount }
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val actorName = currentActorName(data.members)
    val payerLabel = resolvePayerLabel(data.members, paidByMemberId)
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .addBulkBazaarTransactions(
          group = data.activeGroup,
          cycleId = cycle.id,
          items = items,
          sharedNotes = sharedNotes,
          paidByMemberId = paidByMemberId,
          dateEpochDay = dateEpochDay,
          actorName = actorName,
          targetMemberName = payerLabel,
        )
        .onSuccess { count ->
          val formatted = MessCalculator.formatAmount(total, data.activeGroup.currencySymbol)
          _snackbarMessage.value = "Saved $count bazaar items ($formatted)"
        }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Bulk save failed." }
      _isBusy.value = false
    }
  }

  fun deleteTransaction(txId: String) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (data.activeCycle?.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Closed cycles cannot be modified."
      return
    }
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val actorName = currentActorName(data.members)
    val currency = data.activeGroup.currencySymbol.ifBlank { "৳" }
    viewModelScope.launch {
      repository
        .deleteTransaction(
          groupId = data.activeGroup.id,
          txId = txId,
          actorName = actorName,
          currencySymbol = currency,
        )
        .onSuccess { _snackbarMessage.value = "Entry deleted" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Delete failed." }
    }
  }

  fun settleMemberBalance(standing: MemberStanding) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Cycle is closed."
      return
    }
    val absAmount = kotlin.math.abs(standing.netBalance)
    if (absAmount < 0.01) return
    val isRefund = standing.netBalance > 0.0
    val marker =
      if (isRefund) MessTransaction.SETTLEMENT_REFUND_OUT else MessTransaction.SETTLEMENT_DUE_IN
    val title =
      if (isRefund) "Refund to ${standing.member.name}" else "Due Paid by ${standing.member.name}"
    if (!canCurrentUserEdit(data)) {
      _snackbarMessage.value = "You don't have permission to edit meals and expenses."
      return
    }
    val actorName = currentActorName(data.members)
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .addTransaction(
          group = data.activeGroup,
          cycleId = cycle.id,
          type = TransactionType.SETTLEMENT,
          title = title,
          notes = "$marker · Cycle settlement",
          amount = kotlin.math.round(absAmount * 100.0) / 100.0,
          paidByMemberId = standing.member.id,
          dateEpochDay = LocalDate.now().toEpochDay(),
          actorName = actorName,
          targetMemberName = standing.member.name,
        )
        .onSuccess { _snackbarMessage.value = "Settled ${standing.member.name}'s balance" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Settlement failed." }
      _isBusy.value = false
    }
  }

  private fun isCurrentUserManager(data: MessDashboardData): Boolean =
    data.activeGroup.ownerUid == data.currentUserId &&
      data.members.any { it.linkedUid == data.currentUserId && it.role == MemberRole.MANAGER.name }

  fun unlockCycle(cycleId: String) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can unlock cycles."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository.unlockCycle(data.activeGroup.id, cycleId)
        .onSuccess { _snackbarMessage.value = "Cycle unlocked. The manager can edit it now." }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Could not unlock this cycle." }
      _isBusy.value = false
    }
  }

  fun resetGroupData() {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (!isCurrentUserManager(data)) {
      _snackbarMessage.value = "Only the group manager can reset group data."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository.resetGroupData(data.activeGroup.id)
        .onSuccess { _snackbarMessage.value = "Group data reset. Members and profile photos were kept." }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Could not reset group data." }
      _isBusy.value = false
    }
  }

  fun switchCycle(cycleId: String) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    viewModelScope.launch {
      repository.switchActiveCycle(data.activeGroup.id, cycleId).onFailure { err ->
        _snackbarMessage.value = err.localizedMessage ?: "Failed to switch cycle."
      }
    }
  }

  fun startNewCycle(
    cycleMode: CycleMode,
    closeCurrentCycle: Boolean,
    rolloverPositiveBalances: Boolean,
  ) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val prevCycle = data.activeCycle
    val referenceDate =
      if (prevCycle != null) {
        LocalDate.ofEpochDay(prevCycle.endDateEpochDay).plusDays(1)
      } else {
        LocalDate.now()
      }
    val carryBalances =
      if (rolloverPositiveBalances) {
        data.summary.standings
          .filter { it.netBalance > 0.5 }
          .associate { it.member.id to (kotlin.math.round(it.netBalance * 100.0) / 100.0) }
      } else {
        emptyMap()
      }

    viewModelScope.launch {
      _isBusy.value = true
      repository
        .createCycle(
          group = data.activeGroup,
          cycleMode = cycleMode,
          referenceDate = referenceDate,
          closePreviousCycleId = if (closeCurrentCycle) prevCycle?.id else null,
          carryForwardBalances = carryBalances,
        )
        .onSuccess { _snackbarMessage.value = "New ${cycleMode.label.lowercase()} cycle started" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Could not start cycle." }
      _isBusy.value = false
    }
  }

  fun updateGroupSettings(
    name: String,
    currencySymbol: String,
    cycleMode: CycleMode,
    weekStartDay: Int,
    customWeekStartEpochDay: Long,
  ) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository
        .updateGroupSettings(data.activeGroup, name, currencySymbol, cycleMode, weekStartDay, customWeekStartEpochDay)
        .onSuccess { _snackbarMessage.value = "Mess settings updated" }
        .onFailure { err -> _snackbarMessage.value = err.localizedMessage ?: "Update failed." }
      _isBusy.value = false
    }
  }

  fun openDownloadSheet(
    initialFormat: ReportFormat = ReportFormat.XLSX,
    initialScope: ExportScope = ExportScope.CURRENT_MONTH,
  ) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    if (data.activeCycle == null && data.allGroupMeals.isEmpty() && data.allGroupTransactions.isEmpty()) {
      _snackbarMessage.value = "Start or select a billing cycle first."
      return
    }
    if (_downloadState.value !is DownloadUiState.InProgress) {
      _downloadState.value = DownloadUiState.ReadyToDownload(initialFormat, initialScope)
    }
  }

  fun buildSuggestedFileName(format: ReportFormat, scope: ExportScope): String {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return "MessExpense.${format.extension}"
    val snapshot =
      ReportDownloader.buildScopedSnapshot(
        scope = scope,
        activeCycle = data.activeCycle,
        members = data.members,
        allGroupMeals = data.allGroupMeals,
        allGroupTransactions = data.allGroupTransactions,
      )
    return ReportDownloader.buildDefaultFileName(
      groupName = data.activeGroup.name,
      periodTitle = snapshot.periodTitle,
      format = format,
      scope = scope,
    )
  }

  fun startDirectDownload(context: Context, format: ReportFormat, scope: ExportScope) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: MessCycle(groupId = data.activeGroup.id, label = "Monthly Report")
    val appContext = context.applicationContext
    val snapshot =
      ReportDownloader.buildScopedSnapshot(
        scope = scope,
        activeCycle = data.activeCycle,
        members = data.members,
        allGroupMeals = data.allGroupMeals,
        allGroupTransactions = data.allGroupTransactions,
      )
    val fileName =
      ReportDownloader.buildDefaultFileName(
        groupName = data.activeGroup.name,
        periodTitle = snapshot.periodTitle,
        format = format,
        scope = scope,
      )

    activeDownloadJob?.cancel()
    _downloadState.value =
      DownloadUiState.InProgress(
        format = format,
        scope = scope,
        fileName = fileName,
        progress = 0.04f,
        statusText = "Starting download…",
      )

    activeDownloadJob =
      viewModelScope.launch {
        try {
          val result =
            ReportDownloader.downloadToPublicDownloads(
              context = appContext,
              fileName = fileName,
              format = format,
              group = data.activeGroup,
              cycle = cycle,
              snapshot = snapshot,
            ) { update ->
              val streaming = update.phase as? DownloadPhase.Streaming
              val currentInProgress = _downloadState.value as? DownloadUiState.InProgress
              _downloadState.value =
                DownloadUiState.InProgress(
                  format = format,
                  scope = scope,
                  fileName = fileName,
                  progress = update.progress,
                  statusText = update.statusText,
                  bytesWritten = streaming?.bytesWritten ?: currentInProgress?.bytesWritten ?: 0L,
                  totalBytes = streaming?.totalBytes ?: currentInProgress?.totalBytes ?: 0L,
                )
            }
          _downloadState.value = DownloadUiState.Completed(result)
          _snackbarMessage.value = "Saved ${result.fileName} to ${result.locationLabel}"
        } catch (cancel: CancellationException) {
          _downloadState.value = DownloadUiState.Hidden
          _snackbarMessage.value = "Download cancelled"
          throw cancel
        } catch (err: Throwable) {
          Log.w(TAG, "Direct download failed", err)
          _downloadState.value =
            DownloadUiState.Failed(
              format = format,
              scope = scope,
              message = err.localizedMessage ?: "Failed to save report to Downloads.",
            )
        } finally {
          activeDownloadJob = null
        }
      }
  }

  fun startSafDownload(context: Context, targetUri: Uri, format: ReportFormat, scope: ExportScope) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: MessCycle(groupId = data.activeGroup.id, label = "Monthly Report")
    val appContext = context.applicationContext
    val snapshot =
      ReportDownloader.buildScopedSnapshot(
        scope = scope,
        activeCycle = data.activeCycle,
        members = data.members,
        allGroupMeals = data.allGroupMeals,
        allGroupTransactions = data.allGroupTransactions,
      )
    val fileName =
      ReportDownloader.buildDefaultFileName(
        groupName = data.activeGroup.name,
        periodTitle = snapshot.periodTitle,
        format = format,
        scope = scope,
      )

    activeDownloadJob?.cancel()
    _downloadState.value =
      DownloadUiState.InProgress(
        format = format,
        scope = scope,
        fileName = fileName,
        progress = 0.04f,
        statusText = "Starting download…",
      )

    activeDownloadJob =
      viewModelScope.launch {
        try {
          val result =
            ReportDownloader.downloadToSafUri(
              context = appContext,
              targetUri = targetUri,
              fileName = fileName,
              format = format,
              group = data.activeGroup,
              cycle = cycle,
              snapshot = snapshot,
            ) { update ->
              val streaming = update.phase as? DownloadPhase.Streaming
              val currentInProgress = _downloadState.value as? DownloadUiState.InProgress
              _downloadState.value =
                DownloadUiState.InProgress(
                  format = format,
                  scope = scope,
                  fileName = fileName,
                  progress = update.progress,
                  statusText = update.statusText,
                  bytesWritten = streaming?.bytesWritten ?: currentInProgress?.bytesWritten ?: 0L,
                  totalBytes = streaming?.totalBytes ?: currentInProgress?.totalBytes ?: 0L,
                )
            }
          _downloadState.value = DownloadUiState.Completed(result)
          _snackbarMessage.value = "Exported ${result.fileName}"
        } catch (cancel: CancellationException) {
          _downloadState.value = DownloadUiState.Hidden
          _snackbarMessage.value = "Download cancelled"
          throw cancel
        } catch (err: Throwable) {
          Log.w(TAG, "SAF download failed", err)
          _downloadState.value =
            DownloadUiState.Failed(
              format = format,
              scope = scope,
              message = err.localizedMessage ?: "Failed to save report.",
            )
        } finally {
          activeDownloadJob = null
        }
      }
  }

  fun cancelDownload() {
    activeDownloadJob?.cancel()
    activeDownloadJob = null
    _downloadState.value = DownloadUiState.Hidden
    _snackbarMessage.value = "Download cancelled"
  }

  fun dismissDownloadSheet() {
    if (_downloadState.value !is DownloadUiState.InProgress) {
      _downloadState.value = DownloadUiState.Hidden
    }
  }

  fun importXlsxFromStream(openStream: () -> InputStream?) {
    val data = (uiState.value as? MessUiState.Ready)?.data ?: return
    val cycle = data.activeCycle ?: return
    if (cycle.status == CycleStatus.CLOSED.name) {
      _snackbarMessage.value = "Cannot import into a closed cycle."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val parsed =
        withContext(Dispatchers.IO) {
          runCatching {
            val stream = openStream() ?: error("Could not open selected file.")
            stream.use { XlsxCodec.readWorkbook(it) }
          }
        }
      parsed
        .onSuccess { workbook ->
          if (workbook.meals.isEmpty() && workbook.transactions.isEmpty()) {
            _isBusy.value = false
            _snackbarMessage.value = "No meal or transaction rows found in .xlsx file."
            return@onSuccess
          }
          repository
            .importWorkbookIntoCycle(data.activeGroup, cycle.id, data.members, workbook)
            .onSuccess { (mealsCount, txCount) ->
              _snackbarMessage.value = "Imported $mealsCount meal rows & $txCount transactions"
            }
            .onFailure { err ->
              _snackbarMessage.value = err.localizedMessage ?: "Import failed."
            }
          _isBusy.value = false
        }
        .onFailure { err ->
          _isBusy.value = false
          _snackbarMessage.value = err.localizedMessage ?: "Invalid .xlsx workbook."
        }
    }
  }

  private companion object {
    const val TAG = "MessViewModel"
  }
}
