package com.example.data.repository

import com.example.data.model.CycleMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MessRepositoryCycleBoundsTest {
  @Test
  fun `weekly default remains Saturday for existing groups`() {
    val reference = LocalDate.of(2026, 11, 17)
    val bounds = MessRepository.defaultCycleBounds(CycleMode.WEEKLY, reference)
    assertEquals(LocalDate.of(2026, 11, 14).toEpochDay(), bounds.second)
    assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), bounds.third)
  }

  @Test
  fun `weekly Sunday start uses previous Sunday`() {
    val reference = LocalDate.of(2026, 11, 17)
    val bounds = MessRepository.defaultCycleBounds(CycleMode.WEEKLY, reference, weekStartDay = 7)
    assertEquals(LocalDate.of(2026, 11, 15).toEpochDay(), bounds.second)
    assertEquals(LocalDate.of(2026, 11, 21).toEpochDay(), bounds.third)
  }

  @Test
  fun `custom weekly anchor repeats every seven days`() {
    val anchor = LocalDate.of(2026, 11, 3).toEpochDay()
    val bounds = MessRepository.defaultCycleBounds(
      CycleMode.WEEKLY,
      LocalDate.of(2026, 11, 17),
      customWeekStartEpochDay = anchor,
    )
    assertEquals(LocalDate.of(2026, 11, 17).toEpochDay(), bounds.second)
    assertEquals(LocalDate.of(2026, 11, 23).toEpochDay(), bounds.third)
  }
}
