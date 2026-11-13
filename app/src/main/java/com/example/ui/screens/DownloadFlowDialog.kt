package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.export.DownloadedReportResult
import com.example.data.export.ExportScope
import com.example.data.export.ReportDownloader
import com.example.data.export.ReportFormat
import com.example.ui.DownloadUiState
import com.example.ui.MessDashboardData
import com.example.ui.theme.TabularNumberStyle
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DownloadFlowDialog(
  state: DownloadUiState,
  data: MessDashboardData,
  onStartDirectDownload: (ReportFormat, ExportScope) -> Unit,
  onChooseFolderDownload: (ReportFormat, ExportScope) -> Unit,
  onCancelDownload: () -> Unit,
  onOpenFile: (DownloadedReportResult) -> Unit,
  onShareFile: (DownloadedReportResult) -> Unit,
  onDismiss: () -> Unit,
) {
  if (state is DownloadUiState.Hidden) return

  var selectedFormat by remember(state is DownloadUiState.ReadyToDownload) {
    mutableStateOf(
      when (state) {
        is DownloadUiState.ReadyToDownload -> state.initialFormat
        is DownloadUiState.InProgress -> state.format
        is DownloadUiState.Failed -> state.format
        else -> ReportFormat.XLSX
      }
    )
  }

  var selectedScope by remember(state is DownloadUiState.ReadyToDownload) {
    mutableStateOf(
      when (state) {
        is DownloadUiState.ReadyToDownload -> state.initialScope
        is DownloadUiState.InProgress -> state.scope
        is DownloadUiState.Failed -> state.scope
        else -> ExportScope.CURRENT_MONTH
      }
    )
  }

  val snapshot =
    remember(selectedScope, data.activeCycle?.id, data.allGroupMeals, data.allGroupTransactions, data.members) {
      ReportDownloader.buildScopedSnapshot(
        scope = selectedScope,
        activeCycle = data.activeCycle,
        members = data.members,
        allGroupMeals = data.allGroupMeals,
        allGroupTransactions = data.allGroupTransactions,
      )
    }

  val currentMonthLabel =
    remember { YearMonth.now().format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)) }

  val isActiveDownload = state is DownloadUiState.InProgress
  val supportsDirectDownloads = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  Dialog(
    onDismissRequest = { if (!isActiveDownload) onDismiss() },
    properties =
      DialogProperties(
        dismissOnBackPress = !isActiveDownload,
        dismissOnClickOutside = !isActiveDownload,
        usePlatformDefaultWidth = false,
      ),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).widthIn(max = 460.dp),
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 6.dp,
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier =
                Modifier.size(44.dp)
                  .clip(CircleShape)
                  .background(
                    when (state) {
                      is DownloadUiState.Completed -> MaterialTheme.colorScheme.primaryContainer
                      is DownloadUiState.Failed -> MaterialTheme.colorScheme.errorContainer
                      else -> MaterialTheme.colorScheme.primaryContainer
                    }
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector =
                  when (state) {
                    is DownloadUiState.Completed -> Icons.Filled.CheckCircle
                    is DownloadUiState.Failed -> Icons.Filled.ErrorOutline
                    else -> Icons.Filled.Download
                  },
                contentDescription = null,
                tint =
                  when (state) {
                    is DownloadUiState.Failed -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                  },
                modifier = Modifier.size(22.dp),
              )
            }
            Column {
              Text(
                text =
                  when (state) {
                    is DownloadUiState.ReadyToDownload -> stringResource(R.string.download_report)
                    is DownloadUiState.InProgress -> stringResource(R.string.download_in_progress)
                    is DownloadUiState.Completed -> stringResource(R.string.download_complete)
                    is DownloadUiState.Failed -> "Download Interrupted"
                    DownloadUiState.Hidden -> ""
                  },
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "${snapshot.periodTitle} · ${data.activeGroup.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          if (!isActiveDownload) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("close_download_dialog"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Close")
            }
          }
        }

        when (state) {
          is DownloadUiState.ReadyToDownload -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Time Period",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                  selected = selectedScope == ExportScope.CURRENT_MONTH,
                  onClick = { selectedScope = ExportScope.CURRENT_MONTH },
                  label = { Text("Month ($currentMonthLabel)") },
                  modifier = Modifier.testTag("download_scope_current_month"),
                )
                if (data.activeCycle != null) {
                  FilterChip(
                    selected = selectedScope == ExportScope.ACTIVE_CYCLE,
                    onClick = { selectedScope = ExportScope.ACTIVE_CYCLE },
                    label = { Text("Cycle (${data.activeCycle.label})") },
                    modifier = Modifier.testTag("download_scope_active_cycle"),
                  )
                }
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Report Format",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportFormat.entries.forEach { format ->
                  FilterChip(
                    selected = selectedFormat == format,
                    onClick = { selectedFormat = format },
                    label = { Text(format.label) },
                    modifier =
                      Modifier.testTag(
                        if (format == ReportFormat.XLSX) "download_format_xlsx"
                        else "download_format_csv"
                      ),
                  )
                }
              }
            }

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Export Contents & File Preview",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    ReportDownloader.buildDefaultFileName(
                      groupName = data.activeGroup.name,
                      periodTitle = snapshot.periodTitle,
                      format = selectedFormat,
                      scope = selectedScope,
                    ),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val transfersCount = snapshot.summary.settlementTransfers.size
                val transfersLabel =
                  if (transfersCount == 0) "all settled" else "$transfersCount settlement(s)"
                Text(
                  text =
                    "${snapshot.meals.size} meal logs · ${snapshot.transactions.size} transactions · $transfersLabel",
                  style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle),
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text =
                    if (supportsDirectDownloads) "Saves into Downloads/MessExpense with live progress"
                    else "Pick a folder to save your report with live progress",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              if (supportsDirectDownloads) {
                Button(
                  onClick = { onStartDirectDownload(selectedFormat, selectedScope) },
                  modifier =
                    Modifier.fillMaxWidth().height(50.dp).testTag("start_in_app_download_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Download to Downloads")
                }
                OutlinedButton(
                  onClick = { onChooseFolderDownload(selectedFormat, selectedScope) },
                  modifier =
                    Modifier.fillMaxWidth().height(48.dp).testTag("choose_folder_download_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Choose Custom Folder…")
                }
              } else {
                Button(
                  onClick = { onChooseFolderDownload(selectedFormat, selectedScope) },
                  modifier =
                    Modifier.fillMaxWidth().height(50.dp).testTag("choose_folder_download_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Choose Folder & Download")
                }
              }
            }
          }

          is DownloadUiState.InProgress -> {
            val animatedProgress by
              animateFloatAsState(
                targetValue = state.progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 120),
                label = "download_progress_animation",
              )
            val percent = (animatedProgress * 100f).toInt().coerceIn(0, 100)

            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "$percent%",
                    style =
                      MaterialTheme.typography.titleMedium.merge(TabularNumberStyle).copy(
                        fontWeight = FontWeight.Bold
                      ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("download_progress_percent"),
                  )
                }

                LinearProgressIndicator(
                  progress = { animatedProgress },
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(10.dp)
                      .clip(RoundedCornerShape(6.dp))
                      .testTag("download_progress_bar"),
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant,
                  strokeCap = StrokeCap.Round,
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val bytesText =
                    if (state.totalBytes > 0L) {
                      "${ReportDownloader.formatBytes(state.bytesWritten)} / ${ReportDownloader.formatBytes(state.totalBytes)}"
                    } else {
                      "Preparing…"
                    }
                  Text(
                    text = bytesText,
                    style = MaterialTheme.typography.labelMedium.merge(TabularNumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                  )
                }
              }
            }

            OutlinedButton(
              onClick = onCancelDownload,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cancel_download_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Cancel Download")
            }
          }

          is DownloadUiState.Completed -> {
            val result = state.result
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = result.fileName,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                  text = "${ReportDownloader.formatBytes(result.totalBytes)} · Saved to ${result.locationLabel}",
                  style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle),
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
                LinearProgressIndicator(
                  progress = { 1f },
                  modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (result.contentUri != null) {
                OutlinedButton(
                  onClick = { onOpenFile(result) },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("open_downloaded_file_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Open")
                }
                OutlinedButton(
                  onClick = { onShareFile(result) },
                  modifier = Modifier.weight(1f).height(48.dp).testTag("share_downloaded_file_button"),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Share")
                }
              }
            }

            Button(
              onClick = onDismiss,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("download_done_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Done")
            }
          }

          is DownloadUiState.Failed -> {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(14.dp),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Text("Dismiss")
              }
              Button(
                onClick = {
                  if (supportsDirectDownloads) {
                    onStartDirectDownload(state.format, state.scope)
                  } else {
                    onChooseFolderDownload(state.format, state.scope)
                  }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("retry_download_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
              }
            }
          }

          DownloadUiState.Hidden -> Unit
        }
      }
    }
  }
}
