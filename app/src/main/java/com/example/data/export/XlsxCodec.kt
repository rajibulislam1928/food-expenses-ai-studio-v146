package com.example.data.export

import android.util.Xml
import com.example.data.model.MealEntry
import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import com.example.domain.CycleSummary
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

data class ImportedMealRow(
  val memberName: String,
  val dateEpochDay: Long,
  val lunch: Double,
  val dinner: Double,
  val guestMeals: Double,
)

data class ImportedTransactionRow(
  val type: String,
  val title: String,
  val notes: String,
  val amount: Double,
  val payerName: String,
  val dateEpochDay: Long,
)

data class ImportedWorkbookData(
  val memberNames: List<String>,
  val meals: List<ImportedMealRow>,
  val transactions: List<ImportedTransactionRow>,
)

/**
 * Self-contained OpenXML (.xlsx) workbook reader and writer using standard Android Zip and
 * XmlPullParser APIs so multi-sheet Excel exports work offline with zero dependency bloat.
 */
object XlsxCodec {

  private data class SheetData(val name: String, val rows: List<List<Any>>)

  fun writeWorkbook(
    outputStream: OutputStream,
    group: MessGroup,
    cycle: MessCycle,
    summary: CycleSummary,
    members: List<MessMember>,
    mealEntries: List<MealEntry>,
    transactions: List<MessTransaction>,
    periodTitle: String = cycle.label,
    periodType: String = cycle.cycleType,
    periodStatus: String = cycle.status,
  ) {
    val memberById = members.associateBy { it.id }

    val summaryRows = buildList<List<Any>> {
      add(listOf("Mess Expense Report", group.name))
      add(listOf("Period", periodTitle, "Type", periodType, "Status", periodStatus))
      add(listOf("Currency", group.currencySymbol))
      add(emptyList())
      add(listOf("Metric", "Value"))
      add(listOf("Total Meals", summary.totalMeals))
      add(listOf("Total Bazaar", summary.totalBazaar))
      add(listOf("Total Fixed / Utilities", summary.totalFixed))
      add(listOf("Meal Rate", summary.mealRate))
      add(listOf("Total Deposits", summary.totalDeposits))
      add(listOf("Mess Pool Balance", summary.messPoolCashBalance))
      add(emptyList())
      add(
        listOf(
          "Member",
          "Active",
          "Meals",
          "Meal Cost",
          "Fixed Share",
          "Total Payable",
          "Deposited",
          "Out of Pocket",
          "Net Balance",
        )
      )
      summary.standings.forEach { st ->
        add(
          listOf(
            st.member.name,
            if (st.member.isActive) "Yes" else "No",
            st.totalMeals,
            st.mealCost,
            st.fixedShare,
            st.totalPayable,
            st.totalDeposited,
            st.outOfPocketPaid,
            st.netBalance,
          )
        )
      }
    }

    val settlementRows = buildList<List<Any>> {
      add(listOf("From", "To", "Amount", "Transfer Type", "Status"))
      if (summary.settlementTransfers.isEmpty()) {
        add(listOf("All Members", "Balanced", 0.0, "Settled", "No transfers needed"))
      } else {
        summary.settlementTransfers.forEach { tr ->
          add(
            listOf(
              tr.fromMemberName,
              tr.toRecipientName,
              tr.amount,
              tr.category,
              "Pending Settlement",
            )
          )
        }
      }
      add(emptyList())
      add(listOf("Member", "Total Payable", "Total Credits", "Net Balance", "Standing"))
      summary.standings.forEach { st ->
        val standingLabel =
          when {
            kotlin.math.abs(st.netBalance) < 0.01 -> "Settled"
            st.netBalance > 0.0 -> "Refundable"
            else -> "Due Payable"
          }
        add(listOf(st.member.name, st.totalPayable, st.totalCredits, st.netBalance, standingLabel))
      }
    }

    val memberRows = buildList<List<Any>> {
      add(listOf("Name", "Role", "Active"))
      members.forEach { m ->
        add(listOf(m.name, m.role, if (m.isActive) "YES" else "NO"))
      }
    }

    val mealRows = buildList<List<Any>> {
      add(listOf("Date", "Member", "Lunch", "Dinner", "Guest Meals", "Total Meals"))
      mealEntries
        .sortedWith(compareByDescending<MealEntry> { it.dateEpochDay }.thenBy { memberById[it.memberId]?.name ?: "" })
        .forEach { entry ->
          val memberName = memberById[entry.memberId]?.name ?: "Unknown"
          add(
            listOf(
              LocalDate.ofEpochDay(entry.dateEpochDay).toString(),
              memberName,
              entry.lunch,
              entry.dinner,
              entry.guestMeals,
              entry.totalMeals(),
            )
          )
        }
    }

    val txRows = buildList<List<Any>> {
      add(listOf("Date", "Type", "Title", "Amount", "Paid By", "Notes"))
      transactions
        .sortedByDescending { it.dateEpochDay }
        .forEach { tx ->
          val payerName =
            if (tx.paidByMemberId == MessTransaction.POOL_PAYER_ID) {
              "Mess Pool"
            } else {
              memberById[tx.paidByMemberId]?.name ?: "Mess Pool"
            }
          add(
            listOf(
              LocalDate.ofEpochDay(tx.dateEpochDay).toString(),
              tx.type,
              tx.title,
              tx.amount,
              payerName,
              tx.notes,
            )
          )
        }
    }

    val sheets =
      listOf(
        SheetData("Summary", summaryRows),
        SheetData("Settlements", settlementRows),
        SheetData("Members", memberRows),
        SheetData("Meals", mealRows),
        SheetData("Transactions", txRows),
      )

    ZipOutputStream(outputStream).use { zip ->
      writeZipEntry(zip, "[Content_Types].xml", contentTypesXml(sheets.size))
      writeZipEntry(zip, "_rels/.rels", rootRelsXml())
      writeZipEntry(zip, "xl/workbook.xml", workbookXml(sheets))
      writeZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
      writeZipEntry(zip, "xl/styles.xml", stylesXml())
      sheets.forEachIndexed { index, sheet ->
        writeZipEntry(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet.rows))
      }
    }
  }

  fun readWorkbook(inputStream: InputStream): ImportedWorkbookData {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(inputStream).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          entries[entry.name] = zip.readBytes()
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }

    val sharedStrings =
      entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

    val sheetNamesByTarget = parseWorkbookTargets(entries["xl/workbook.xml"], entries["xl/_rels/workbook.xml.rels"])
    val parsedSheets = mutableMapOf<String, List<List<String>>>()
    sheetNamesByTarget.forEach { (sheetName, zipPath) ->
      val bytes = entries[zipPath] ?: entries["xl/$zipPath"]
      if (bytes != null) {
        parsedSheets[sheetName.lowercase()] = parseSheetRows(bytes, sharedStrings)
      }
    }

    val memberNames = linkedSetOf<String>()
    parsedSheets["members"]?.drop(1)?.forEach { row ->
      val name = row.getOrNull(0)?.trim().orEmpty()
      if (name.isNotBlank()) memberNames.add(name)
    }

    val meals = mutableListOf<ImportedMealRow>()
    val mealSheetRows = parsedSheets["meals"].orEmpty()
    val hasLegacyBreakfastCol =
      mealSheetRows.firstOrNull()?.any { it.equals("Breakfast", ignoreCase = true) } == true
    val lunchCol = if (hasLegacyBreakfastCol) 3 else 2
    val dinnerCol = if (hasLegacyBreakfastCol) 4 else 3
    val guestCol = if (hasLegacyBreakfastCol) 5 else 4
    mealSheetRows.drop(1).forEach { row ->
      val dateStr = row.getOrNull(0)?.trim().orEmpty()
      val memberName = row.getOrNull(1)?.trim().orEmpty()
      if (dateStr.isNotBlank() && memberName.isNotBlank()) {
        val epochDay = parseEpochDay(dateStr)
        val lunch = row.getOrNull(lunchCol)?.toDoubleOrNull() ?: 0.0
        val dinner = row.getOrNull(dinnerCol)?.toDoubleOrNull() ?: 0.0
        val guest = row.getOrNull(guestCol)?.toDoubleOrNull() ?: 0.0
        memberNames.add(memberName)
        meals.add(
          ImportedMealRow(
            memberName = memberName,
            dateEpochDay = epochDay,
            lunch = lunch.coerceIn(0.0, 20.0),
            dinner = dinner.coerceIn(0.0, 20.0),
            guestMeals = guest.coerceIn(0.0, 50.0),
          )
        )
      }
    }

    val transactions = mutableListOf<ImportedTransactionRow>()
    parsedSheets["transactions"]?.drop(1)?.forEach { row ->
      val dateStr = row.getOrNull(0)?.trim().orEmpty()
      val rawType = row.getOrNull(1)?.trim()?.uppercase().orEmpty()
      val title = row.getOrNull(2)?.trim().orEmpty()
      val amount = row.getOrNull(3)?.toDoubleOrNull() ?: 0.0
      val payerName = row.getOrNull(4)?.trim().orEmpty()
      val notes = row.getOrNull(5)?.trim().orEmpty()
      if (title.isNotBlank() && amount > 0.0) {
        val normalizedType =
          TransactionType.entries.firstOrNull { it.name == rawType }?.name
            ?: TransactionType.BAZAAR.name
        if (payerName.isNotBlank() && !payerName.equals("Mess Pool", ignoreCase = true)) {
          memberNames.add(payerName)
        }
        transactions.add(
          ImportedTransactionRow(
            type = normalizedType,
            title = title.take(100),
            notes = notes.take(300),
            amount = amount,
            payerName = payerName,
            dateEpochDay = parseEpochDay(dateStr),
          )
        )
      }
    }

    return ImportedWorkbookData(
      memberNames = memberNames.toList(),
      meals = meals,
      transactions = transactions,
    )
  }

  private fun parseEpochDay(raw: String): Long {
    return runCatching { LocalDate.parse(raw).toEpochDay() }
      .getOrElse {
        val excelSerial = raw.toDoubleOrNull()
        if (excelSerial != null && excelSerial > 20000) {
          LocalDate.of(1899, 12, 30).plusDays(excelSerial.toLong()).toEpochDay()
        } else {
          LocalDate.now().toEpochDay()
        }
      }
  }

  private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val result = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
    var inSi = false
    val current = StringBuilder()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          if (parser.name == "si") {
            inSi = true
            current.clear()
          } else if (inSi && parser.name == "t") {
            current.append(parser.nextText())
          }
        }
        XmlPullParser.END_TAG -> {
          if (parser.name == "si") {
            inSi = false
            result.add(current.toString())
          }
        }
      }
      event = parser.next()
    }
    return result
  }

  private fun parseWorkbookTargets(
    workbookBytes: ByteArray?,
    relsBytes: ByteArray?,
  ): Map<String, String> {
    if (workbookBytes == null || relsBytes == null) {
      return mapOf(
        "Summary" to "xl/worksheets/sheet1.xml",
        "Members" to "xl/worksheets/sheet2.xml",
        "Meals" to "xl/worksheets/sheet3.xml",
        "Transactions" to "xl/worksheets/sheet4.xml",
      )
    }
    val relsMap = mutableMapOf<String, String>()
    val relParser = Xml.newPullParser()
    relParser.setInput(ByteArrayInputStream(relsBytes), "UTF-8")
    var relEvent = relParser.eventType
    while (relEvent != XmlPullParser.END_DOCUMENT) {
      if (relEvent == XmlPullParser.START_TAG && relParser.name == "Relationship") {
        val id = relParser.getAttributeValue(null, "Id")
        val target = relParser.getAttributeValue(null, "Target")?.trimStart('/')
        if (id != null && target != null) {
          relsMap[id] = if (target.startsWith("xl/")) target else "xl/$target"
        }
      }
      relEvent = relParser.next()
    }

    val result = linkedMapOf<String, String>()
    val wbParser = Xml.newPullParser()
    wbParser.setInput(ByteArrayInputStream(workbookBytes), "UTF-8")
    var wbEvent = wbParser.eventType
    while (wbEvent != XmlPullParser.END_DOCUMENT) {
      if (wbEvent == XmlPullParser.START_TAG && wbParser.name == "sheet") {
        val name = wbParser.getAttributeValue(null, "name")
        val rid =
          wbParser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            ?: wbParser.getAttributeValue(null, "r:id")
        val target = relsMap[rid]
        if (name != null && target != null) {
          result[name] = target
        }
      }
      wbEvent = wbParser.next()
    }
    return result
  }

  private fun parseSheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
    var currentRow = mutableListOf<String>()
    var cellType: String? = null
    var cellColIndex = 0
    var cellValue = ""
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "row" -> currentRow = mutableListOf()
            "c" -> {
              cellType = parser.getAttributeValue(null, "t")
              val ref = parser.getAttributeValue(null, "r") ?: ""
              cellColIndex = columnIndexFromRef(ref, currentRow.size)
              cellValue = ""
            }
            "v" -> cellValue = parser.nextText()
            "t" -> if (cellType == "inlineStr") cellValue = parser.nextText()
          }
        }
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "c" -> {
              val resolved =
                when (cellType) {
                  "s" -> sharedStrings.getOrNull(cellValue.toIntOrNull() ?: -1) ?: ""
                  else -> cellValue
                }
              while (currentRow.size < cellColIndex) {
                currentRow.add("")
              }
              currentRow.add(resolved)
            }
            "row" -> rows.add(currentRow)
          }
        }
      }
      event = parser.next()
    }
    return rows
  }

  private fun columnIndexFromRef(ref: String, fallback: Int): Int {
    val letters = ref.takeWhile { it.isLetter() }
    if (letters.isEmpty()) return fallback
    var index = 0
    for (ch in letters.uppercase()) {
      index = index * 26 + (ch - 'A' + 1)
    }
    return (index - 1).coerceAtLeast(0)
  }

  private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
  }

  private fun contentTypesXml(sheetCount: Int): String {
    val overrides =
      (1..sheetCount).joinToString("\n") { idx ->
        """  <Override PartName="/xl/worksheets/sheet$idx.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
      }
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$overrides
</Types>"""
  }

  private fun rootRelsXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

  private fun workbookXml(sheets: List<SheetData>): String {
    val sheetTags =
      sheets.mapIndexed { index, sheet ->
        """    <sheet name="${escapeXml(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
      }.joinToString("\n")
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$sheetTags
  </sheets>
</workbook>"""
  }

  private fun workbookRelsXml(sheetCount: Int): String {
    val sheetRels =
      (1..sheetCount).joinToString("\n") { idx ->
        """  <Relationship Id="rId$idx" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$idx.xml"/>"""
      }
    val stylesId = sheetCount + 1
    return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$sheetRels
  <Relationship Id="rId$stylesId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
  }

  private fun stylesXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""

  private fun sheetXml(rows: List<List<Any>>): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
    rows.forEachIndexed { rowIdx, row ->
      val r = rowIdx + 1
      sb.append("""<row r="$r">""")
      row.forEachIndexed { colIdx, value ->
        val ref = "${columnLetter(colIdx)}$r"
        val style = if (rowIdx == 0) """ s="1"""" else ""
        when (value) {
          is Number -> sb.append("""<c r="$ref"$style><v>${value.toDouble()}</v></c>""")
          else -> {
            val text = escapeXml(value.toString())
            sb.append("""<c r="$ref"$style t="inlineStr"><is><t>$text</t></is></c>""")
          }
        }
      }
      sb.append("</row>")
    }
    sb.append("</sheetData></worksheet>")
    return sb.toString()
  }

  private fun columnLetter(index: Int): String {
    var n = index
    val sb = StringBuilder()
    while (n >= 0) {
      sb.append(('A'.code + (n % 26)).toChar())
      n = (n / 26) - 1
    }
    return sb.reverse().toString()
  }

  private fun escapeXml(raw: String): String =
    raw
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
