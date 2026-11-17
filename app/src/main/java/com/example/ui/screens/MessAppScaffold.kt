package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.export.ExportScope
import com.example.data.export.ReportFormat
import com.example.data.model.CycleMode
import com.example.data.model.CycleStatus
import com.example.data.model.MessGroup
import com.example.data.model.TransactionType
import com.example.domain.MemberStanding
import com.example.notifications.MessBackgroundSyncWorker
import com.example.ui.DownloadUiState
import com.example.ui.MessDashboardData
import com.example.ui.MessTab
import com.example.ui.MessUiState
import com.example.ui.MessViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@Composable
fun MessAppScaffold(
  viewModel: MessViewModel,
  userDisplayName: String,
  userEmail: String,
  onSignOut: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
  val selectedDateEpochDay by viewModel.selectedDateEpochDay.collectAsStateWithLifecycle()
  val txFilter by viewModel.txFilter.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val lastSyncedMillis by viewModel.lastSyncedMillis.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarMessage) {
    val msg = snackbarMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeSnackbar()
    }
  }

  when (val state = uiState) {
    MessUiState.Loading -> {
      Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator()
            Text(
              text = "Syncing mess ledger...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    is MessUiState.Error -> {
      Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "Could Not Sync Mess Data",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                  onClick = onSignOut,
                  modifier = Modifier.height(48.dp).testTag("error_sign_out_button"),
                ) {
                  Text(stringResource(R.string.sign_out))
                }
                Button(
                  onClick = { viewModel.triggerManualSync() },
                  modifier = Modifier.height(48.dp).testTag("error_retry_button"),
                ) {
                  Text("Retry Sync")
                }
              }
            }
          }
        }
      }
    }

    is MessUiState.NeedsGroup -> {
      Box(modifier = Modifier.fillMaxSize()) {
        GroupOnboardingScreen(
          defaultUserName = userDisplayName,
          isBusy = isBusy,
          onCreateGroup = { name, currency, cycleMode, creatorName, roommatesCsv ->
            viewModel.createMessGroup(
              name = name,
              currencySymbol = currency,
              cycleMode = cycleMode,
              creatorMemberName = creatorName,
              initialMembersCsv = roommatesCsv,
            )
          },
          onJoinGroup = { code, memberName ->
            viewModel.joinMessGroup(inviteCode = code, memberDisplayName = memberName)
          },
          onSignOut = onSignOut,
        )
        SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
      }
    }

    is MessUiState.Ready -> {
      MessDashboardContent(
        data = state.data,
        selectedTab = selectedTab,
        selectedDateEpochDay = selectedDateEpochDay,
        txFilter = txFilter,
        isBusy = isBusy,
        lastSyncedMillis = lastSyncedMillis,
        userDisplayName = userDisplayName,
        userEmail = userEmail,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        onSignOut = onSignOut,
      )
    }
  }
}

private data class NavDestinationItem(
  val tab: MessTab,
  val labelRes: Int,
  val icon: ImageVector,
  val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessDashboardContent(
  data: MessDashboardData,
  selectedTab: MessTab,
  selectedDateEpochDay: Long,
  txFilter: com.example.ui.TxFilter,
  isBusy: Boolean,
  lastSyncedMillis: Long,
  userDisplayName: String,
  userEmail: String,
  snackbarHostState: SnackbarHostState,
  viewModel: MessViewModel,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val isCycleClosed = data.activeCycle?.status == CycleStatus.CLOSED.name
  val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
  val showExpenseBanner by viewModel.isExpenseBannerVisible.collectAsStateWithLifecycle()
  val unreadActivityCount by viewModel.unreadActivityCount.collectAsStateWithLifecycle()
  val showActivitySheet by viewModel.showActivitySheet.collectAsStateWithLifecycle()
  val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()

  var addTxSheetType by remember { mutableStateOf<TransactionType?>(null) }
  var showGroupMenu by remember { mutableStateOf(false) }
  var showAccountMenu by remember { mutableStateOf(false) }
  var showGroupSettings by remember { mutableStateOf(false) }
  var showCreateOrJoinDialog by remember { mutableStateOf(false) }
  var pendingExportScope by remember { mutableStateOf(ExportScope.CURRENT_MONTH) }
  var pendingReminderStanding by remember { mutableStateOf<MemberStanding?>(null) }
  var showBulkBazaarDialog by remember { mutableStateOf(false) }

  LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    if (notificationPrefs.backgroundEnabled) {
      MessBackgroundSyncWorker.enqueuePeriodic(context.applicationContext)
    }
  }

  BackHandler(enabled = selectedTab != MessTab.OVERVIEW) {
    viewModel.selectTab(MessTab.OVERVIEW)
  }

  val exportXlsxLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(ReportFormat.XLSX.mimeType)
    ) { uri ->
      if (uri != null) {
        viewModel.startSafDownload(context, uri, ReportFormat.XLSX, pendingExportScope)
      }
    }

  val exportCsvLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument(ReportFormat.CSV.mimeType)
    ) { uri ->
      if (uri != null) {
        viewModel.startSafDownload(context, uri, ReportFormat.CSV, pendingExportScope)
      }
    }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importXlsxFromStream { context.contentResolver.openInputStream(uri) }
      }
    }

  val profilePhotoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) viewModel.updateMyProfilePhoto(uri)
    }

  val navItems = remember {
    listOf(
      NavDestinationItem(MessTab.OVERVIEW, R.string.nav_overview, Icons.Filled.Dashboard, "nav_overview"),
      NavDestinationItem(MessTab.MEALS, R.string.nav_meals, Icons.Filled.Restaurant, "nav_meals"),
      NavDestinationItem(MessTab.EXPENSES, R.string.nav_expenses, Icons.Filled.ReceiptLong, "nav_expenses"),
      NavDestinationItem(
        MessTab.LEDGER,
        R.string.nav_ledger,
        Icons.Filled.AccountBalanceWallet,
        "nav_ledger",
      ),
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideLayout = maxWidth >= 600.dp

    Scaffold(
      topBar = {
        Column {
          TopAppBar(
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
              ),
            title = {
              Box {
                Column(
                  modifier =
                    Modifier.clickable { showGroupMenu = true }
                      .padding(vertical = 4.dp)
                      .testTag("group_switcher_button")
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = data.activeGroup.name,
                      style = MaterialTheme.typography.titleLarge,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                      imageVector = Icons.Filled.ArrowDropDown,
                      contentDescription = "Switch mess group",
                    )
                  }
                  Text(
                    text =
                      "${data.activeCycle?.label ?: "No Cycle"} · Code ${data.activeGroup.inviteCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }

                DropdownMenu(
                  expanded = showGroupMenu,
                  onDismissRequest = { showGroupMenu = false },
                ) {
                  data.allGroups.forEach { group ->
                    DropdownMenuItem(
                      text = { Text(group.name) },
                      leadingIcon = {
                        if (group.id == data.activeGroup.id) {
                          Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Active group",
                            tint = MaterialTheme.colorScheme.primary,
                          )
                        }
                      },
                      onClick = {
                        showGroupMenu = false
                        viewModel.switchGroup(group.id)
                      },
                    )
                  }
                  HorizontalDivider()
                  DropdownMenuItem(
                    text = { Text("Create or Join Another Mess") },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Filled.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    },
                    onClick = {
                      showGroupMenu = false
                      showCreateOrJoinDialog = true
                    },
                  )
                }
              }
            },
            actions = {
              IconButton(
                onClick = { viewModel.openActivitySheet() },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("activity_bell_button"),
              ) {
                BadgedBox(
                  badge = {
                    if (unreadActivityCount > 0) {
                      Badge {
                        Text(if (unreadActivityCount > 99) "99+" else unreadActivityCount.toString())
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.activity_bell_cd),
                  )
                }
              }
              IconButton(
                onClick = { viewModel.triggerManualSync() },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_now_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Sync,
                  contentDescription = stringResource(R.string.sync_now),
                )
              }
              IconButton(
                onClick = { showGroupSettings = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("group_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Tune,
                  contentDescription = "Mess settings",
                )
              }
              Box {
                IconButton(
                  onClick = { showAccountMenu = true },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("account_menu_button"),
                ) {
                  MemberAvatar(
                    name = data.members.firstOrNull { it.linkedUid == data.currentUserId }?.name ?: userDisplayName,
                    photoBase64 = data.members.firstOrNull { it.linkedUid == data.currentUserId }?.avatarBase64.orEmpty(),
                    size = 30.dp,
                    modifier = Modifier.testTag("my_profile_avatar"),
                  )
                }
                DropdownMenu(
                  expanded = showAccountMenu,
                  onDismissRequest = { showAccountMenu = false },
                ) {
                  val formattedSync =
                    remember(lastSyncedMillis) {
                      SimpleDateFormat("h:mm a", Locale.US).format(Date(lastSyncedMillis))
                    }
                  Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                      text = userDisplayName.ifBlank { "Google Account" },
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                    )
                    if (userEmail.isNotBlank()) {
                      Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Text(
                      text = "Synced at $formattedSync",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  HorizontalDivider()
                  DropdownMenuItem(
                    text = {
                      Text(
                        if (data.members.firstOrNull { it.linkedUid == data.currentUserId }?.avatarBase64.isNullOrBlank())
                          "Add profile photo" else "Change profile photo"
                      )
                    },
                    leadingIcon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                    modifier = Modifier.testTag("profile_photo_menu_item"),
                    onClick = {
                      showAccountMenu = false
                      profilePhotoLauncher.launch("image/*")
                    },
                  )
                  if (!data.members.firstOrNull { it.linkedUid == data.currentUserId }?.avatarBase64.isNullOrBlank()) {
                    DropdownMenuItem(
                      text = { Text("Remove profile photo") },
                      leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                      onClick = {
                        showAccountMenu = false
                        viewModel.updateMyProfilePhoto(null)
                      },
                    )
                  }
                  HorizontalDivider()
                  DropdownMenuItem(
                    text = { Text(stringResource(R.string.sign_out)) },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                      )
                    },
                    onClick = {
                      showAccountMenu = false
                      onSignOut()
                    },
                  )
                }
              }
            },
          )
          if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
        }
      },
      bottomBar = {
        if (!isWideLayout) {
          NavigationBar(
            windowInsets = WindowInsets.navigationBars,
          ) {
            navItems.forEach { item ->
              val label = stringResource(item.labelRes)
              NavigationBarItem(
                selected = selectedTab == item.tab,
                onClick = { viewModel.selectTab(item.tab) },
                icon = { Icon(item.icon, contentDescription = label) },
                label = { Text(label) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }
      },
      floatingActionButton = {
        if (selectedTab == MessTab.EXPENSES && !isCycleClosed) {
          ExtendedFloatingActionButton(
            onClick = { addTxSheetType = TransactionType.BAZAAR },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Log Entry") },
            modifier = Modifier.testTag("fab_log_entry"),
          )
        }
      },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isWideLayout) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            Spacer(modifier = Modifier.height(12.dp))
            navItems.forEach { item ->
              val label = stringResource(item.labelRes)
              NavigationRailItem(
                selected = selectedTab == item.tab,
                onClick = { viewModel.selectTab(item.tab) },
                icon = { Icon(item.icon, contentDescription = label) },
                label = { Text(label) },
                modifier = Modifier.testTag("${item.testTag}_rail"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          when (selectedTab) {
            MessTab.OVERVIEW -> {
              OverviewScreen(
                data = data,
                isWideLayout = isWideLayout,
                onNavigateTab = viewModel::selectTab,
                onQuickAddBazaar = { addTxSheetType = TransactionType.BAZAAR },
                onQuickBulkBazaar = { showBulkBazaarDialog = true },
                onQuickAddDeposit = { addTxSheetType = TransactionType.DEPOSIT },
                onShareInvite = {
                  val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(
                        Intent.EXTRA_TEXT,
                        "Join our mess \"${data.activeGroup.name}\" on Food Expenses! Invite code: ${data.activeGroup.inviteCode}",
                      )
                    }
                  context.startActivity(Intent.createChooser(sendIntent, "Share Mess Invite"))
                },
                onDownloadReport = { viewModel.openDownloadSheet(ReportFormat.XLSX) },
                onSendReminder = { standing -> pendingReminderStanding = standing },
              )
            }

            MessTab.MEALS -> {
              MealsScreen(
                data = data,
                selectedDateEpochDay = selectedDateEpochDay,
                onSelectDate = viewModel::selectDate,
                onUpdateMeal = viewModel::updateMealEntry,
                onCopyYesterday = viewModel::copyYesterdayMeals,
                onMarkStandardDay = viewModel::markStandardMealsForActiveMembers,
              )
            }

            MessTab.EXPENSES -> {
              ExpensesScreen(
                data = data,
                selectedFilter = txFilter,
                showExpenseBanner = showExpenseBanner,
                onSelectFilter = viewModel::selectTxFilter,
                onDismissExpenseBanner = viewModel::dismissExpenseBanner,
                onOpenBulkBazaar = { showBulkBazaarDialog = true },
                onDeleteTransaction = viewModel::deleteTransaction,
              )
            }

            MessTab.LEDGER -> {
              LedgerScreen(
                data = data,
                isManager = data.activeGroup.ownerUid == data.currentUserId &&
                  data.members.any { it.linkedUid == data.currentUserId && it.role == "MANAGER" },
                onUnlockCycle = viewModel::unlockCycle,
                onResetGroupData = viewModel::resetGroupData,
                onSettleMember = viewModel::settleMemberBalance,
                onAddMember = viewModel::addMember,
                onToggleMemberActive = viewModel::toggleMemberActive,
                onSwitchCycle = viewModel::switchCycle,
                onStartNewCycle = viewModel::startNewCycle,
                onExportXlsx = { viewModel.openDownloadSheet(ReportFormat.XLSX) },
                onImportXlsx = {
                  importLauncher.launch(
                    arrayOf(
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                      "application/octet-stream",
                    )
                  )
                },
                onSendReminder = { standing -> pendingReminderStanding = standing },
                onCopiedCode = { msg -> viewModel.showSnackbar(msg) },
              )
            }
          }
        }
      }
    }
  }

  addTxSheetType?.let { initialType ->
    AddTransactionSheet(
      initialType = initialType,
      members = data.members,
      activeCycle = data.activeCycle,
      currencySymbol = data.activeGroup.currencySymbol,
      onDismiss = { addTxSheetType = null },
      onSwitchToBulkBazaar = { showBulkBazaarDialog = true },
      onSubmit = { type, title, notes, amount, paidByMemberId, dateEpochDay ->
        viewModel.addTransaction(
          type = type,
          title = title,
          notes = notes,
          amount = amount,
          paidByMemberId = paidByMemberId,
          dateEpochDay = dateEpochDay,
        )
      },
    )
  }

  if (showBulkBazaarDialog) {
    BulkBazaarDialog(
      members = data.members,
      activeCycle = data.activeCycle,
      currencySymbol = data.activeGroup.currencySymbol,
      onDismiss = { showBulkBazaarDialog = false },
      onSubmitBulk = { items, sharedNotes, paidByMemberId, dateEpochDay ->
        viewModel.addBulkBazaarTransactions(
          items = items,
          sharedNotes = sharedNotes,
          paidByMemberId = paidByMemberId,
          dateEpochDay = dateEpochDay,
        )
      },
    )
  }

  if (downloadState !is DownloadUiState.Hidden) {
    DownloadFlowDialog(
      state = downloadState,
      data = data,
      onStartDirectDownload = { format, scope ->
        viewModel.startDirectDownload(context, format, scope)
      },
      onChooseFolderDownload = { format, scope ->
        pendingExportScope = scope
        val fileName = viewModel.buildSuggestedFileName(format, scope)
        when (format) {
          ReportFormat.XLSX -> exportXlsxLauncher.launch(fileName)
          ReportFormat.CSV -> exportCsvLauncher.launch(fileName)
        }
      },
      onCancelDownload = viewModel::cancelDownload,
      onOpenFile = { res ->
        val uri = res.contentUri
        if (uri == null) {
          viewModel.showSnackbar("File saved to ${res.locationLabel}")
        } else {
          runCatching {
            val viewIntent =
              Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, res.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
            context.startActivity(viewIntent)
          }.onFailure {
            viewModel.showSnackbar("No app found to open ${res.fileName}. Use Share instead.")
          }
        }
      },
      onShareFile = { res ->
        val uri = res.contentUri
        if (uri != null) {
          val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
              type = res.mimeType
              putExtra(Intent.EXTRA_STREAM, uri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
          context.startActivity(Intent.createChooser(shareIntent, "Share ${res.fileName}"))
        }
      },
      onDismiss = viewModel::dismissDownloadSheet,
    )
  }

  pendingReminderStanding?.let { reminderTarget ->
    SendReminderDialog(
      standing = reminderTarget,
      group = data.activeGroup,
      cycle = data.activeCycle,
      settlementTransfers = data.summary.settlementTransfers,
      onCopied = { msg -> viewModel.showSnackbar(msg) },
      onDismiss = { pendingReminderStanding = null },
    )
  }

  if (showGroupSettings) {
    GroupSettingsDialog(
      group = data.activeGroup,
      onDismiss = { showGroupSettings = false },
      onSave = { name, currency, mode, weekStartDay, customWeekStartEpochDay ->
        viewModel.updateGroupSettings(name, currency, mode, weekStartDay, customWeekStartEpochDay)
        showGroupSettings = false
      },
    )
  }

  if (showCreateOrJoinDialog) {
    Dialog(
      onDismissRequest = { showCreateOrJoinDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
          TopAppBar(
            title = { Text("Add or Join Mess") },
            navigationIcon = {
              IconButton(
                onClick = { showCreateOrJoinDialog = false },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
              }
            },
          )
          Box(modifier = Modifier.weight(1f)) {
            GroupOnboardingScreen(
              defaultUserName = userDisplayName,
              isBusy = isBusy,
              showSignOutInTopBar = false,
              onCreateGroup = { name, currency, cycleMode, creatorName, roommatesCsv ->
                viewModel.createMessGroup(
                  name = name,
                  currencySymbol = currency,
                  cycleMode = cycleMode,
                  creatorMemberName = creatorName,
                  initialMembersCsv = roommatesCsv,
                  onSuccess = { showCreateOrJoinDialog = false },
                )
              },
              onJoinGroup = { code, memberName ->
                viewModel.joinMessGroup(
                  inviteCode = code,
                  memberDisplayName = memberName,
                  onSuccess = { showCreateOrJoinDialog = false },
                )
              },
              onSignOut = {},
            )
          }
        }
      }
    }
  }

  if (showActivitySheet) {
    MessActivitySheet(
      logs = data.activityLogs,
      members = data.members,
      currentUserId = data.currentUserId,
      prefs = notificationPrefs,
      onToggleBackgroundAlerts = viewModel::setBackgroundNotificationsEnabled,
      onToggleMealAlerts = viewModel::setNotifyOnMeals,
      onToggleExpenseAlerts = viewModel::setNotifyOnExpenses,
      onToggleIncludeSelf = viewModel::setIncludeSelfChanges,
      onRunBackgroundCheckNow = viewModel::runBackgroundCheckNow,
      onMarkAllRead = viewModel::markAllActivityRead,
      onDismiss = viewModel::dismissActivitySheet,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSettingsDialog(
  group: MessGroup,
  onDismiss: () -> Unit,
  onSave: (String, String, CycleMode, Int, Long) -> Unit,
) {
  var name by remember(group.id) { mutableStateOf(group.name) }
  var currency by remember(group.id) { mutableStateOf(group.currencySymbol) }
  var mode by remember(group.id) {
    mutableStateOf(CycleMode.entries.firstOrNull { it.name == group.cycleMode } ?: CycleMode.MONTHLY)
  }
  var weekStartDay by remember(group.id) { mutableStateOf(group.weekStartDay.coerceIn(1, 7)) }
  var customWeekStartEpochDay by remember(group.id) { mutableStateOf(group.customWeekStartEpochDay) }
  var showWeekStartDatePicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Mess Settings") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Mess Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("settings_mess_name_input"),
        )
        Text("Default Currency", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("৳", "₹", "$", "£").forEach { sym ->
            FilterChip(
              selected = currency == sym,
              onClick = { currency = sym },
              label = { Text(sym) },
            )
          }
        }
        Text("Default Cycle Mode", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CycleMode.entries.forEach { m ->
            FilterChip(
              selected = mode == m,
              onClick = { mode = m },
              label = { Text(m.label) },
            )
          }
        }
        if (mode == CycleMode.WEEKLY) {
          Text("Week starts", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = customWeekStartEpochDay < 0L && weekStartDay == java.time.DayOfWeek.SATURDAY.value,
              onClick = { weekStartDay = java.time.DayOfWeek.SATURDAY.value; customWeekStartEpochDay = -1L },
              label = { Text("Saturday") },
            )
            FilterChip(
              selected = customWeekStartEpochDay < 0L && weekStartDay == java.time.DayOfWeek.SUNDAY.value,
              onClick = { weekStartDay = java.time.DayOfWeek.SUNDAY.value; customWeekStartEpochDay = -1L },
              label = { Text("Sunday") },
            )
            FilterChip(
              selected = customWeekStartEpochDay >= 0L,
              onClick = { showWeekStartDatePicker = true },
              label = {
                Text(if (customWeekStartEpochDay >= 0L) "Custom: ${LocalDate.ofEpochDay(customWeekStartEpochDay)}" else "Custom date")
              },
            )
          }
          Text(
            "Applies to new weekly cycles; existing cycle dates stay unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(name, currency, mode, weekStartDay, customWeekStartEpochDay) },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("save_group_settings_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )

  if (showWeekStartDatePicker) {
    val pickerState = rememberDatePickerState(
      initialSelectedDateMillis = (if (customWeekStartEpochDay >= 0L) customWeekStartEpochDay else LocalDate.now().toEpochDay()) * 86_400_000L,
    )
    DatePickerDialog(
      onDismissRequest = { showWeekStartDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          pickerState.selectedDateMillis?.let { customWeekStartEpochDay = Math.floorDiv(it, 86_400_000L) }
          showWeekStartDatePicker = false
        }) { Text("Set start date") }
      },
      dismissButton = { TextButton(onClick = { showWeekStartDatePicker = false }) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
  }
}
