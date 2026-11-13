package com.example.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.MealEntry
import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.domain.CycleSummary
import com.example.domain.MessCalculator
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class ExportScope(val label: String) {
  CURRENT_MONTH("Current Month"),
  ACTIVE_CYCLE("Active Cycle"),
}

enum class ReportFormat(
  val extension: String,
  val mimeType: String,
  val label: String,
) {
  XLSX(
    extension = "xlsx",
    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    label = "Excel (.xlsx)",
  ),
  CSV(
    extension = "csv",
    mimeType = "text/csv",
    label = "CSV Statement (.csv)",
  ),
}

data class ScopedExportSnapshot(
  val scope: ExportScope,
  val periodTitle: String,
  val periodType: String,
  val periodStatus: String,
  val members: List<MessMember>,
  val meals: List<MealEntry>,
  val transactions: List<MessTransaction>,
  val summary: CycleSummary,
)

sealed interface DownloadPhase {
  data object Preparing : DownloadPhase
  data object Building : DownloadPhase
  data class Streaming(val bytesWritten: Long, val totalBytes: Long) : DownloadPhase
  data object Finalizing : DownloadPhase
}

data class DownloadProgressUpdate(
  val phase: DownloadPhase,
  val progress: Float,
  val statusText: String,
)

data class DownloadedReportResult(
  val fileName: String,
  val mimeType: String,
  val totalBytes: Long,
  val locationLabel: String,
  val contentUri: Uri?,
)

object ReportDownloader {

  fun buildScopedSnapshot(
    scope: ExportScope,
    activeCycle: MessCycle?,
    members: List<MessMember>,
    allGroupMeals: List<MealEntry>,
    allGroupTransactions: List<MessTransaction>,
    currentMonth: YearMonth = YearMonth.now(),
  ): ScopedExportSnapshot {
    return when (scope) {
      ExportScope.CURRENT_MONTH -> {
        val monthStart = currentMonth.atDay(1).toEpochDay()
        val monthEnd = currentMonth.atEndOfMonth().toEpochDay()
        val monthMeals = allGroupMeals.filter { it.dateEpochDay in monthStart..monthEnd }
        val monthTx = allGroupTransactions.filter { it.dateEpochDay in monthStart..monthEnd }
        val summary = MessCalculator.computeCycleSummary(members, monthMeals, monthTx)
        val monthLabel = currentMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
        ScopedExportSnapshot(
          scope = ExportScope.CURRENT_MONTH,
          periodTitle = monthLabel,
          periodType = "MONTHLY_REPORT",
          periodStatus = "MONTH_${currentMonth}",
          members = members,
          meals = monthMeals,
          transactions = monthTx,
          summary = summary,
        )
      }

      ExportScope.ACTIVE_CYCLE -> {
        val cycleId = activeCycle?.id.orEmpty()
        val cycleMeals = allGroupMeals.filter { it.cycleId == cycleId }
        val cycleTx = allGroupTransactions.filter { it.cycleId == cycleId }
        val summary = MessCalculator.computeCycleSummary(members, cycleMeals, cycleTx)
        ScopedExportSnapshot(
          scope = ExportScope.ACTIVE_CYCLE,
          periodTitle = activeCycle?.label ?: "Active Cycle",
          periodType = activeCycle?.cycleType ?: "CYCLE",
          periodStatus = activeCycle?.status ?: "ACTIVE",
          members = members,
          meals = cycleMeals,
          transactions = cycleTx,
          summary = summary,
        )
      }
    }
  }

  fun buildDefaultFileName(
    groupName: String,
    periodTitle: String?,
    format: ReportFormat,
    scope: ExportScope = ExportScope.ACTIVE_CYCLE,
  ): String {
    val cleanGroup = groupName.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifBlank { "Mess" }.take(26)
    val cleanPeriod = (periodTitle ?: "cycle").replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifBlank { "cycle" }.take(22)
    val suffix = if (scope == ExportScope.CURRENT_MONTH) "_Monthly" else ""
    return "MessExpense_${cleanGroup}_${cleanPeriod}${suffix}.${format.extension}"
  }

  fun generateReportBytes(
    format: ReportFormat,
    group: MessGroup,
    cycle: MessCycle,
    summary: CycleSummary,
    members: List<MessMember>,
    mealEntries: List<MealEntry>,
    transactions: List<MessTransaction>,
    periodTitle: String = cycle.label,
    periodType: String = cycle.cycleType,
    periodStatus: String = cycle.status,
  ): ByteArray {
    val baos = ByteArrayOutputStream()
    when (format) {
      ReportFormat.XLSX -> {
        XlsxCodec.writeWorkbook(
          outputStream = baos,
          group = group,
          cycle = cycle,
          summary = summary,
          members = members,
          mealEntries = mealEntries,
          transactions = transactions,
          periodTitle = periodTitle,
          periodType = periodType,
          periodStatus = periodStatus,
        )
      }

      ReportFormat.CSV -> {
        baos.write(
          buildCsvString(
            group = group,
            summary = summary,
            members = members,
            mealEntries = mealEntries,
            transactions = transactions,
            periodTitle = periodTitle,
            periodType = periodType,
            periodStatus = periodStatus,
          ).toByteArray(Charsets.UTF_8)
        )
      }
    }
    return baos.toByteArray()
  }

  private fun buildCsvString(
    group: MessGroup,
    summary: CycleSummary,
    members: List<MessMember>,
    mealEntries: List<MealEntry>,
    transactions: List<MessTransaction>,
    periodTitle: String,
    periodType: String,
    periodStatus: String,
  ): String {
    val memberById = members.associateBy { it.id }
    val sb = StringBuilder()

    fun row(vararg cells: Any) {
      sb.append(
        cells.joinToString(",") { cell ->
          val raw = cell.toString()
          if (raw.contains(',') || raw.contains('"') || raw.contains('\n')) {
            "\"${raw.replace("\"", "\"\"")}\""
          } else {
            raw
          }
        }
      ).append('\n')
    }

    row("[MESS EXPENSE REPORT]")
    row("Mess Group", group.name)
    row("Period", periodTitle, "Type", periodType, "Status", periodStatus)
    row("Currency", group.currencySymbol)
    sb.append('\n')

    row("[SUMMARY METRICS]")
    row("Metric", "Value")
    row("Total Meals", MessCalculator.formatMeals(summary.totalMeals))
    row("Total Bazaar", summary.totalBazaar)
    row("Total Fixed / Utilities", summary.totalFixed)
    row("Meal Rate", summary.mealRate)
    row("Total Deposits", summary.totalDeposits)
    row("Mess Pool Balance", summary.messPoolCashBalance)
    sb.append('\n')

    row("[MEMBER BALANCE SHEET & STANDINGS]")
    row(
      "Member",
      "Active",
      "Meals",
      "Meal Cost",
      "Fixed Share",
      "Total Payable",
      "Deposited",
      "Out of Pocket",
      "Net Balance",
      "Standing",
    )
    summary.standings.forEach { st ->
      val standingLabel =
        when {
          kotlin.math.abs(st.netBalance) < 0.01 -> "Settled"
          st.netBalance > 0.0 -> "Refundable"
          else -> "Due Payable"
        }
      row(
        st.member.name,
        if (st.member.isActive) "Yes" else "No",
        st.totalMeals,
        st.mealCost,
        st.fixedShare,
        st.totalPayable,
        st.totalDeposited,
        st.outOfPocketPaid,
        st.netBalance,
        standingLabel,
      )
    }
    sb.append('\n')

    row("[SETTLEMENT PLAN]")
    row("From", "To", "Amount", "Transfer Type", "Status")
    if (summary.settlementTransfers.isEmpty()) {
      row("All Members", "Balanced", 0.0, "Settled", "No transfers needed")
    } else {
      summary.settlementTransfers.forEach { tr ->
        row(tr.fromMemberName, tr.toRecipientName, tr.amount, tr.category, "Pending Settlement")
      }
    }
    sb.append('\n')

    row("[DAILY MEAL RECORDS]")
    row("Meal Date", "Member", "Lunch", "Dinner", "Guest Meals", "Total Meals")
    mealEntries
      .sortedWith(
        compareByDescending<MealEntry> { it.dateEpochDay }
          .thenBy { memberById[it.memberId]?.name ?: "" }
      )
      .forEach { entry ->
        row(
          LocalDate.ofEpochDay(entry.dateEpochDay).toString(),
          memberById[entry.memberId]?.name ?: "Unknown",
          entry.lunch,
          entry.dinner,
          entry.guestMeals,
          entry.totalMeals(),
        )
      }
    sb.append('\n')

    row("[EXPENSES & DEPOSITS LEDGER]")
    row("Transaction Date", "Type", "Title", "Amount", "Paid By", "Notes")
    transactions
      .sortedByDescending { it.dateEpochDay }
      .forEach { tx ->
        val payer =
          if (tx.paidByMemberId == MessTransaction.POOL_PAYER_ID) {
            "Mess Pool"
          } else {
            memberById[tx.paidByMemberId]?.name ?: "Mess Pool"
          }
        row(
          LocalDate.ofEpochDay(tx.dateEpochDay).toString(),
          tx.type,
          tx.title,
          tx.amount,
          payer,
          tx.notes,
        )
      }

    return sb.toString()
  }

  suspend fun streamBytesWithProgress(
    bytes: ByteArray,
    outputStream: OutputStream,
    throttleMillis: Long = 22L,
    onProgress: suspend (written: Long, total: Long) -> Unit,
  ) {
    val total = bytes.size.toLong().coerceAtLeast(1L)
    val chunkSize = (bytes.size / 18).coerceIn(256, 2048)
    var offset = 0
    while (offset < bytes.size) {
      coroutineContext.ensureActive()
      val count = minOf(chunkSize, bytes.size - offset)
      outputStream.write(bytes, offset, count)
      offset += count
      onProgress(offset.toLong(), total)
      if (throttleMillis > 0L && offset < bytes.size) {
        delay(throttleMillis)
      }
    }
    outputStream.flush()
  }

  suspend fun downloadToPublicDownloads(
    context: Context,
    fileName: String,
    format: ReportFormat,
    group: MessGroup,
    cycle: MessCycle,
    snapshot: ScopedExportSnapshot,
    onProgress: suspend (DownloadProgressUpdate) -> Unit,
  ): DownloadedReportResult = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      error("Direct Downloads requires Android 10 or newer. Please use Choose Folder.")
    }

    onProgress(
      DownloadProgressUpdate(
        phase = DownloadPhase.Preparing,
        progress = 0.08f,
        statusText = "Collecting ${snapshot.periodTitle} records & settlements…",
      )
    )
    delay(120L)
    coroutineContext.ensureActive()

    onProgress(
      DownloadProgressUpdate(
        phase = DownloadPhase.Building,
        progress = 0.24f,
        statusText = "Generating ${format.label}…",
      )
    )
    val bytes =
      generateReportBytes(
        format = format,
        group = group,
        cycle = cycle,
        summary = snapshot.summary,
        members = snapshot.members,
        mealEntries = snapshot.meals,
        transactions = snapshot.transactions,
        periodTitle = snapshot.periodTitle,
        periodType = snapshot.periodType,
        periodStatus = snapshot.periodStatus,
      )
    coroutineContext.ensureActive()

    val resolver = context.contentResolver
    val values =
      ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MessExpense")
        put(MediaStore.Downloads.IS_PENDING, 1)
      }

    val itemUri =
      resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("Unable to create file in Downloads.")

    try {
      resolver.openOutputStream(itemUri)?.use { out ->
        streamBytesWithProgress(bytes = bytes, outputStream = out) { written, total ->
          val streamFraction = written.toFloat() / total.toFloat()
          val overallProgress = 0.28f + (streamFraction * 0.64f)
          onProgress(
            DownloadProgressUpdate(
              phase = DownloadPhase.Streaming(written, total),
              progress = overallProgress.coerceIn(0.28f, 0.92f),
              statusText = "Saving to Downloads/MessExpense…",
            )
          )
        }
      } ?: error("Could not open Downloads output stream.")

      coroutineContext.ensureActive()
      onProgress(
        DownloadProgressUpdate(
          phase = DownloadPhase.Finalizing,
          progress = 0.96f,
          statusText = "Finalizing file…",
        )
      )
      val doneValues = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
      resolver.update(itemUri, doneValues, null, null)

      DownloadedReportResult(
        fileName = fileName,
        mimeType = format.mimeType,
        totalBytes = bytes.size.toLong(),
        locationLabel = "Downloads/MessExpense",
        contentUri = itemUri,
      )
    } catch (t: Throwable) {
      runCatching { resolver.delete(itemUri, null, null) }
      throw t
    }
  }

  suspend fun downloadToSafUri(
    context: Context,
    targetUri: Uri,
    fileName: String,
    format: ReportFormat,
    group: MessGroup,
    cycle: MessCycle,
    snapshot: ScopedExportSnapshot,
    onProgress: suspend (DownloadProgressUpdate) -> Unit,
  ): DownloadedReportResult = withContext(Dispatchers.IO) {
    onProgress(
      DownloadProgressUpdate(
        phase = DownloadPhase.Preparing,
        progress = 0.08f,
        statusText = "Collecting ${snapshot.periodTitle} records & settlements…",
      )
    )
    delay(120L)
    coroutineContext.ensureActive()

    onProgress(
      DownloadProgressUpdate(
        phase = DownloadPhase.Building,
        progress = 0.24f,
        statusText = "Generating ${format.label}…",
      )
    )
    val bytes =
      generateReportBytes(
        format = format,
        group = group,
        cycle = cycle,
        summary = snapshot.summary,
        members = snapshot.members,
        mealEntries = snapshot.meals,
        transactions = snapshot.transactions,
        periodTitle = snapshot.periodTitle,
        periodType = snapshot.periodType,
        periodStatus = snapshot.periodStatus,
      )
    coroutineContext.ensureActive()

    context.contentResolver.openOutputStream(targetUri)?.use { out ->
      streamBytesWithProgress(bytes = bytes, outputStream = out) { written, total ->
        val streamFraction = written.toFloat() / total.toFloat()
        val overallProgress = 0.28f + (streamFraction * 0.67f)
        onProgress(
          DownloadProgressUpdate(
            phase = DownloadPhase.Streaming(written, total),
            progress = overallProgress.coerceIn(0.28f, 0.95f),
            statusText = "Writing file to selected folder…",
          )
        )
      }
    } ?: error("Could not open selected destination file.")

    onProgress(
      DownloadProgressUpdate(
        phase = DownloadPhase.Finalizing,
        progress = 0.98f,
        statusText = "Finalizing file…",
      )
    )

    DownloadedReportResult(
      fileName = fileName,
      mimeType = format.mimeType,
      totalBytes = bytes.size.toLong(),
      locationLabel = "Selected device folder",
      contentUri = targetUri,
    )
  }

  fun formatBytes(bytes: Long): String {
    return when {
      bytes < 1024L -> "$bytes B"
      bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
      else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
  }
}
