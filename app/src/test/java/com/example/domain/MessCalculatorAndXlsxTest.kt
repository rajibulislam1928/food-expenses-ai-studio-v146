package com.example.domain

import com.example.data.export.ExportScope
import com.example.data.export.ReportDownloader
import com.example.data.export.ReportFormat
import com.example.data.export.XlsxCodec
import com.example.data.model.CycleMode
import com.example.data.model.CycleStatus
import com.example.data.model.MealEntry
import com.example.data.model.MemberRole
import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import com.example.data.model.MessMember
import com.example.data.model.MessTransaction
import com.example.data.model.TransactionType
import com.example.ui.screens.clampEpochDayToCycle
import com.example.ui.screens.isEpochDayInCycle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessCalculatorAndXlsxTest {

  @Test
  fun `computeCycleSummary handles weighted meals, fixed splits, out-of-pocket and settlements`() {
    val members =
      listOf(
        MessMember(id = "m1", name = "Rahim", role = MemberRole.MANAGER.name, isActive = true),
        MessMember(id = "m2", name = "Karim", role = MemberRole.MEMBER.name, isActive = true),
        MessMember(id = "m3", name = "PausedGuest", role = MemberRole.MEMBER.name, isActive = false),
      )

    // Rahim: 4 + 4 + 2 = 10 meals
    // Karim: 5 + 5 + 0 = 10 meals
    // Total meals = 20
    val meals =
      listOf(
        MealEntry(memberId = "m1", dateEpochDay = 20000L, lunch = 4.0, dinner = 4.0, guestMeals = 2.0),
        MealEntry(memberId = "m2", dateEpochDay = 20000L, lunch = 5.0, dinner = 5.0, guestMeals = 0.0),
      )

    // Bazaar = 1200 (1000 from pool, 200 out-of-pocket by Rahim) -> Meal rate = 1200 / 20 = 60.0
    // Fixed = 600 from pool -> Split evenly between 2 active members = 300.0 each
    // Rahim total payable = 10*60 + 300 = 900; credits = 1000 deposit + 200 out-of-pocket = 1200 -> +300 refund
    // Karim total payable = 10*60 + 300 = 900; credits = 600 deposit -> -300 due
    // Pool balance = 1600 deposits - 1000 pool bazaar - 600 pool fixed = 0.0
    val baseTx =
      listOf(
        MessTransaction(
          id = "t1",
          type = TransactionType.DEPOSIT.name,
          title = "Deposit",
          amount = 1000.0,
          paidByMemberId = "m1",
          dateEpochDay = 20000L,
        ),
        MessTransaction(
          id = "t2",
          type = TransactionType.DEPOSIT.name,
          title = "Deposit",
          amount = 600.0,
          paidByMemberId = "m2",
          dateEpochDay = 20000L,
        ),
        MessTransaction(
          id = "t3",
          type = TransactionType.BAZAAR.name,
          title = "Pool Bazaar",
          amount = 1000.0,
          paidByMemberId = MessTransaction.POOL_PAYER_ID,
          dateEpochDay = 20000L,
        ),
        MessTransaction(
          id = "t4",
          type = TransactionType.BAZAAR.name,
          title = "Urgent Bazaar",
          amount = 200.0,
          paidByMemberId = "m1",
          dateEpochDay = 20000L,
        ),
        MessTransaction(
          id = "t5",
          type = TransactionType.FIXED_UTILITY.name,
          title = "Utilities",
          amount = 600.0,
          paidByMemberId = MessTransaction.POOL_PAYER_ID,
          dateEpochDay = 20000L,
        ),
      )

    val summaryBeforeSettle =
      MessCalculator.computeCycleSummary(members, meals, baseTx)

    assertEquals(20.0, summaryBeforeSettle.totalMeals, 0.001)
    assertEquals(1200.0, summaryBeforeSettle.totalBazaar, 0.001)
    assertEquals(60.0, summaryBeforeSettle.mealRate, 0.001)
    assertEquals(300.0, summaryBeforeSettle.fixedSharePerMember, 0.001)
    assertEquals(0.0, summaryBeforeSettle.messPoolCashBalance, 0.001)
    assertEquals(1, summaryBeforeSettle.settlementTransfers.size)
    assertEquals("Karim", summaryBeforeSettle.settlementTransfers.first().fromMemberName)
    assertEquals("Rahim", summaryBeforeSettle.settlementTransfers.first().toRecipientName)
    assertEquals(300.0, summaryBeforeSettle.settlementTransfers.first().amount, 0.001)

    val rahimBefore = summaryBeforeSettle.standings.first { it.member.id == "m1" }
    val karimBefore = summaryBeforeSettle.standings.first { it.member.id == "m2" }
    val pausedBefore = summaryBeforeSettle.standings.first { it.member.id == "m3" }

    assertEquals(300.0, rahimBefore.netBalance, 0.001)
    assertEquals(-300.0, karimBefore.netBalance, 0.001)
    assertEquals(0.0, pausedBefore.fixedShare, 0.001)

    val settledTx =
      baseTx +
        listOf(
          MessTransaction(
            id = "s1",
            type = TransactionType.SETTLEMENT.name,
            title = "Due Paid",
            notes = MessTransaction.SETTLEMENT_DUE_IN,
            amount = 300.0,
            paidByMemberId = "m2",
            dateEpochDay = 20001L,
          ),
          MessTransaction(
            id = "s2",
            type = TransactionType.SETTLEMENT.name,
            title = "Refund Out",
            notes = MessTransaction.SETTLEMENT_REFUND_OUT,
            amount = 300.0,
            paidByMemberId = "m1",
            dateEpochDay = 20001L,
          ),
        )

    val summaryAfterSettle =
      MessCalculator.computeCycleSummary(members, meals, settledTx)

    val rahimAfter = summaryAfterSettle.standings.first { it.member.id == "m1" }
    val karimAfter = summaryAfterSettle.standings.first { it.member.id == "m2" }
    assertEquals(0.0, rahimAfter.netBalance, 0.001)
    assertEquals(0.0, karimAfter.netBalance, 0.001)
    assertEquals(0.0, summaryAfterSettle.messPoolCashBalance, 0.001)
    assertTrue(summaryAfterSettle.settlementTransfers.isEmpty())
  }

  @Test
  fun `xlsx workbook write and read round-trip preserves members, meals, transactions, and settlements sheet`() {
    val today = LocalDate.of(2026, 11, 12).toEpochDay()
    val group =
      MessGroup(
        id = "g1",
        name = "Emerald Flat 4B",
        currencySymbol = "৳",
        cycleMode = CycleMode.MONTHLY.name,
      )
    val cycle =
      MessCycle(
        id = "c1",
        groupId = "g1",
        label = "Nov 2026",
        cycleType = CycleMode.MONTHLY.name,
        startDateEpochDay = today,
        endDateEpochDay = today + 30,
        status = CycleStatus.ACTIVE.name,
      )
    val members =
      listOf(
        MessMember(id = "m1", groupId = "g1", name = "Rahim", role = MemberRole.MANAGER.name, isActive = true),
        MessMember(id = "m2", groupId = "g1", name = "Karim", role = MemberRole.MEMBER.name, isActive = true),
      )
    val meals =
      listOf(
        MealEntry(
          id = "c1_${today}_m1",
          groupId = "g1",
          cycleId = "c1",
          memberId = "m1",
          dateEpochDay = today,
          lunch = 1.0,
          dinner = 1.0,
          guestMeals = 2.0,
        )
      )
    val transactions =
      listOf(
        MessTransaction(
          id = "tx1",
          groupId = "g1",
          cycleId = "c1",
          type = TransactionType.BAZAAR.name,
          title = "Fish & Rice",
          notes = "Morning market",
          amount = 750.0,
          paidByMemberId = MessTransaction.POOL_PAYER_ID,
          dateEpochDay = today,
        ),
        MessTransaction(
          id = "tx2",
          groupId = "g1",
          cycleId = "c1",
          type = TransactionType.DEPOSIT.name,
          title = "Monthly Deposit",
          notes = "",
          amount = 2500.0,
          paidByMemberId = "m2",
          dateEpochDay = today,
        ),
      )

    val summary = MessCalculator.computeCycleSummary(members, meals, transactions)
    val out = ByteArrayOutputStream()
    XlsxCodec.writeWorkbook(out, group, cycle, summary, members, meals, transactions)
    val bytes = out.toByteArray()
    assertTrue(bytes.isNotEmpty())

    // Verify 5 sheets including Settlements in workbook.xml
    var workbookXmlText = ""
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (entry.name == "xl/workbook.xml") {
          workbookXmlText = zip.readBytes().toString(Charsets.UTF_8)
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }
    assertTrue(workbookXmlText.contains("Settlements"))

    val parsed = XlsxCodec.readWorkbook(ByteArrayInputStream(bytes))
    assertTrue(parsed.memberNames.contains("Rahim"))
    assertTrue(parsed.memberNames.contains("Karim"))
    assertEquals(1, parsed.meals.size)
    assertEquals("Rahim", parsed.meals.first().memberName)
    assertEquals(1.0, parsed.meals.first().lunch, 0.001)
    assertEquals(1.0, parsed.meals.first().dinner, 0.001)
    assertEquals(2.0, parsed.meals.first().guestMeals, 0.001)
    assertEquals(2, parsed.transactions.size)
    val parsedDeposit = parsed.transactions.first { it.type == TransactionType.DEPOSIT.name }
    assertEquals(2500.0, parsedDeposit.amount, 0.001)
    assertEquals("Karim", parsedDeposit.payerName)
  }

  @Test
  fun `current month snapshot aggregates across weekly cycles and exports structured csv with settlement plan`() = runBlocking {
    val targetMonth = YearMonth.of(2026, 11)
    val nov2 = LocalDate.of(2026, 11, 2).toEpochDay()
    val nov12 = LocalDate.of(2026, 11, 12).toEpochDay()
    val oct28 = LocalDate.of(2026, 10, 28).toEpochDay()

    val group = MessGroup(id = "g1", name = "Emerald Flat 4B", currencySymbol = "৳", cycleMode = CycleMode.WEEKLY.name)
    val activeWeek =
      MessCycle(
        id = "week_2",
        groupId = "g1",
        label = "Nov 9–Nov 15",
        cycleType = CycleMode.WEEKLY.name,
        startDateEpochDay = nov12 - 3,
        endDateEpochDay = nov12 + 3,
        status = CycleStatus.ACTIVE.name,
      )
    val members =
      listOf(
        MessMember(id = "m1", groupId = "g1", name = "Rahim", role = MemberRole.MANAGER.name, isActive = true),
        MessMember(id = "m2", groupId = "g1", name = "Karim", role = MemberRole.MEMBER.name, isActive = true),
      )
    val allMeals =
      listOf(
        MealEntry(id = "e_oct", groupId = "g1", cycleId = "week_0", memberId = "m1", dateEpochDay = oct28, lunch = 10.0),
        MealEntry(id = "e_nov2", groupId = "g1", cycleId = "week_1", memberId = "m1", dateEpochDay = nov2, lunch = 1.0, dinner = 1.0),
        MealEntry(id = "e_nov12", groupId = "g1", cycleId = "week_2", memberId = "m2", dateEpochDay = nov12, lunch = 1.0, dinner = 1.0),
      )
    val allTx =
      listOf(
        MessTransaction(id = "t_oct", groupId = "g1", cycleId = "week_0", type = TransactionType.BAZAAR.name, title = "Old Oct", amount = 999.0, paidByMemberId = "m1", dateEpochDay = oct28),
        MessTransaction(id = "t_nov2", groupId = "g1", cycleId = "week_1", type = TransactionType.BAZAAR.name, title = "Week 1 Rice", amount = 400.0, paidByMemberId = "m1", dateEpochDay = nov2),
        MessTransaction(id = "t_nov12", groupId = "g1", cycleId = "week_2", type = TransactionType.BAZAAR.name, title = "Week 2 Fish", amount = 200.0, paidByMemberId = MessTransaction.POOL_PAYER_ID, dateEpochDay = nov12),
        MessTransaction(id = "d_nov12", groupId = "g1", cycleId = "week_2", type = TransactionType.DEPOSIT.name, title = "Pool Deposit", amount = 200.0, paidByMemberId = "m1", dateEpochDay = nov12),
      )

    val monthlySnapshot =
      ReportDownloader.buildScopedSnapshot(
        scope = ExportScope.CURRENT_MONTH,
        activeCycle = activeWeek,
        members = members,
        allGroupMeals = allMeals,
        allGroupTransactions = allTx,
        currentMonth = targetMonth,
      )

    assertEquals("Nov 2026", monthlySnapshot.periodTitle)
    assertEquals(2, monthlySnapshot.meals.size)
    assertEquals(3, monthlySnapshot.transactions.size)
    assertEquals(4.0, monthlySnapshot.summary.totalMeals, 0.001)
    assertEquals(600.0, monthlySnapshot.summary.totalBazaar, 0.001)
    assertEquals(150.0, monthlySnapshot.summary.mealRate, 0.001)
    assertEquals(1, monthlySnapshot.summary.settlementTransfers.size)
    assertEquals("Karim", monthlySnapshot.summary.settlementTransfers.first().fromMemberName)
    assertEquals("Rahim", monthlySnapshot.summary.settlementTransfers.first().toRecipientName)
    assertEquals(300.0, monthlySnapshot.summary.settlementTransfers.first().amount, 0.001)

    val csvBytes =
      ReportDownloader.generateReportBytes(
        format = ReportFormat.CSV,
        group = group,
        cycle = activeWeek,
        summary = monthlySnapshot.summary,
        members = monthlySnapshot.members,
        mealEntries = monthlySnapshot.meals,
        transactions = monthlySnapshot.transactions,
        periodTitle = monthlySnapshot.periodTitle,
        periodType = monthlySnapshot.periodType,
        periodStatus = monthlySnapshot.periodStatus,
      )
    val csvText = csvBytes.toString(Charsets.UTF_8)
    assertTrue(csvText.contains("[SETTLEMENT PLAN]"))
    assertTrue(csvText.contains("Karim,Rahim,300.0,Member-to-Member"))
    assertTrue(csvText.contains("Week 1 Rice"))
    assertTrue(!csvText.contains("Old Oct"))

    val largeBytes = ByteArray(10_000) { (it % 127).toByte() }
    val out = ByteArrayOutputStream()
    val updates = mutableListOf<Pair<Long, Long>>()
    ReportDownloader.streamBytesWithProgress(bytes = largeBytes, outputStream = out, throttleMillis = 0L) { written, total ->
      updates.add(written to total)
    }

    assertTrue(updates.size > 1)
    assertEquals(10_000L to 10_000L, updates.last())
    assertTrue(updates.zipWithNext().all { (a, b) -> b.first > a.first })
    assertArrayEquals(largeBytes, out.toByteArray())
    assertEquals(
      "MessExpense_Emerald_Flat_4B_Nov_2026_Monthly.xlsx",
      ReportDownloader.buildDefaultFileName(group.name, "Nov 2026", ReportFormat.XLSX, ExportScope.CURRENT_MONTH),
    )
  }

  @Test
  fun `reminder message builder formats detailed and short templates for negative balance members`() {
    val group = MessGroup(id = "g1", name = "Emerald Flat 4B", currencySymbol = "৳")
    val cycle = MessCycle(id = "c1", groupId = "g1", label = "Nov 2026")
    val rahim = MessMember(id = "m1", groupId = "g1", name = "Rahim", isActive = true)
    val karim = MessMember(id = "m2", groupId = "g1", name = "Karim", isActive = true)

    val karimDue =
      MemberStanding(
        member = karim,
        totalMeals = 10.0,
        mealCost = 600.0,
        fixedShare = 300.0,
        totalPayable = 900.0,
        totalDeposited = 600.0,
        outOfPocketPaid = 0.0,
        settlementDuePaid = 0.0,
        settlementRefundReceived = 0.0,
        netBalance = -300.0,
      )
    val rahimRefund =
      MemberStanding(
        member = rahim,
        totalMeals = 10.0,
        mealCost = 600.0,
        fixedShare = 300.0,
        totalPayable = 900.0,
        totalDeposited = 1000.0,
        outOfPocketPaid = 200.0,
        settlementDuePaid = 0.0,
        settlementRefundReceived = 0.0,
        netBalance = 300.0,
      )

    assertTrue(ReminderMessageBuilder.hasNegativeBalance(karimDue))
    assertTrue(!ReminderMessageBuilder.hasNegativeBalance(rahimRefund))

    val transfers =
      listOf(
        SettlementTransfer(
          fromMemberName = "Karim",
          toRecipientName = "Rahim",
          amount = 300.0,
          category = "Member-to-Member",
        )
      )

    val detailed =
      ReminderMessageBuilder.buildMessage(
        standing = karimDue,
        group = group,
        cycle = cycle,
        settlementTransfers = transfers,
        style = ReminderStyle.DETAILED,
      )
    assertTrue(detailed.contains("Hi Karim"))
    assertTrue(detailed.contains("Emerald Flat 4B"))
    assertTrue(detailed.contains("Nov 2026"))
    assertTrue(detailed.contains("Balance Due: ৳300"))
    assertTrue(detailed.contains("Suggested payment: ৳300 to Rahim."))

    val shortMsg =
      ReminderMessageBuilder.buildMessage(
        standing = karimDue,
        group = group,
        cycle = cycle,
        settlementTransfers = emptyList(),
        style = ReminderStyle.SHORT,
      )
    assertTrue(shortMsg.contains("Karim"))
    assertTrue(shortMsg.contains("৳300"))
    assertTrue(shortMsg.contains("Mess Pool"))
  }

  @Test
  fun `bulk bazaar validator skips blank rows, flags incomplete rows, and totals valid groceries`() {
    val rows =
      listOf(
        BulkBazaarRowDraft(id = 1, title = "  Rice (5kg)  ", amountText = "420.50"),
        BulkBazaarRowDraft(id = 2, title = "", amountText = ""),
        BulkBazaarRowDraft(id = 3, title = "Cooking Oil", amountText = "315"),
        BulkBazaarRowDraft(id = 4, title = "Onions", amountText = ""),
        BulkBazaarRowDraft(id = 5, title = "", amountText = "90"),
      )

    val result = BulkBazaarValidator.validate(rows)
    assertEquals(2, result.validItems.size)
    assertEquals("Rice (5kg)", result.validItems[0].title)
    assertEquals(420.50, result.validItems[0].amount, 0.001)
    assertEquals("Cooking Oil", result.validItems[1].title)
    assertEquals(315.0, result.validItems[1].amount, 0.001)
    assertEquals(735.50, result.totalAmount, 0.001)
    assertEquals(setOf(4, 5), result.incompleteRowIds)
  }

  @Test
  fun `cycle date clamping keeps in-range dates and clamps out-of-range dates into cycle bounds`() {
    val start = LocalDate.of(2026, 11, 1).toEpochDay()
    val mid = LocalDate.of(2026, 11, 15).toEpochDay()
    val end = LocalDate.of(2026, 11, 30).toEpochDay()
    val cycle =
      MessCycle(
        id = "c1",
        groupId = "g1",
        label = "Nov 2026",
        startDateEpochDay = start,
        endDateEpochDay = end,
      )

    assertTrue(isEpochDayInCycle(mid, cycle))
    assertTrue(isEpochDayInCycle(start, cycle))
    assertTrue(isEpochDayInCycle(end, cycle))
    assertTrue(!isEpochDayInCycle(start - 1, cycle))
    assertTrue(!isEpochDayInCycle(end + 1, cycle))

    assertEquals(mid, clampEpochDayToCycle(mid, cycle))
    assertEquals(start, clampEpochDayToCycle(start - 5, cycle))
    assertEquals(end, clampEpochDayToCycle(end + 10, cycle))
  }

  @Test
  fun `computeMemberContributions and roommate filters calculate totals and match names case-insensitively`() {
    val m1 = MessMember(id = "m1", name = "Rahim Uddin", isActive = true)
    val m2 = MessMember(id = "m2", name = "Karim Hasan", isActive = true)
    val m3 = MessMember(id = "m3", name = "Tanvir Ahmed", isActive = true)

    val day1 = LocalDate.of(2026, 11, 2).toEpochDay()
    val day2 = LocalDate.of(2026, 11, 10).toEpochDay()
    val day3 = LocalDate.of(2026, 11, 12).toEpochDay()

    val tx =
      listOf(
        MessTransaction(
          id = "t1",
          type = TransactionType.BAZAAR.name,
          title = "Fresh Fish",
          amount = 350.0,
          paidByMemberId = "m1",
          dateEpochDay = day1,
        ),
        MessTransaction(
          id = "t2",
          type = TransactionType.FIXED_UTILITY.name,
          title = "WiFi Bill",
          amount = 400.0,
          paidByMemberId = "m1",
          dateEpochDay = day2,
        ),
        MessTransaction(
          id = "t3",
          type = TransactionType.DEPOSIT.name,
          title = "November Deposit",
          amount = 1000.0,
          paidByMemberId = "m1",
          dateEpochDay = day3,
        ),
        MessTransaction(
          id = "t4",
          type = TransactionType.SETTLEMENT.name,
          title = "Due Settlement",
          notes = MessTransaction.SETTLEMENT_DUE_IN,
          amount = 150.0,
          paidByMemberId = "m1",
          dateEpochDay = day3,
        ),
        MessTransaction(
          id = "t5",
          type = TransactionType.SETTLEMENT.name,
          title = "Pool Refund",
          notes = MessTransaction.SETTLEMENT_REFUND_OUT,
          amount = 200.0,
          paidByMemberId = "m1",
          dateEpochDay = day3,
        ),
        MessTransaction(
          id = "t6",
          type = TransactionType.BAZAAR.name,
          title = "Pool Vegetables",
          amount = 500.0,
          paidByMemberId = MessTransaction.POOL_PAYER_ID,
          dateEpochDay = day2,
        ),
      )

    val contrib = MessCalculator.computeMemberContributions("m1", tx)
    assertEquals(350.0, contrib.bazaarPaid, 0.001)
    assertEquals(400.0, contrib.fixedPaid, 0.001)
    assertEquals(1000.0, contrib.deposited, 0.001)
    assertEquals(150.0, contrib.settlementPaid, 0.001)
    assertEquals(750.0, contrib.outOfPocketTotal, 0.001)
    assertEquals(1900.0, contrib.totalContributed, 0.001)
    assertEquals(5, contrib.transactions.size)
    assertEquals(day3, contrib.transactions.first().dateEpochDay)

    val summary = MessCalculator.computeCycleSummary(listOf(m1, m2, m3), emptyList(), tx)
    assertEquals(3, MessCalculator.filterStandings(summary.standings, "  ").size)
    assertEquals(listOf("Rahim Uddin"), MessCalculator.filterStandings(summary.standings, "rahim").map { it.member.name })
    assertEquals(listOf("Tanvir Ahmed"), MessCalculator.filterMembers(listOf(m1, m2, m3), "TANVIR").map { it.name })
  }
}
