package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MessCycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

fun clampEpochDayToCycle(candidateEpochDay: Long, cycle: MessCycle?): Long {
  if (cycle == null || cycle.endDateEpochDay < cycle.startDateEpochDay) return candidateEpochDay
  return candidateEpochDay.coerceIn(cycle.startDateEpochDay, cycle.endDateEpochDay)
}

fun isEpochDayInCycle(epochDay: Long, cycle: MessCycle?): Boolean {
  if (cycle == null || cycle.endDateEpochDay < cycle.startDateEpochDay) return true
  return epochDay in cycle.startDateEpochDay..cycle.endDateEpochDay
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleDatePickerSection(
  selectedEpochDay: Long,
  activeCycle: MessCycle?,
  onDateSelected: (Long) -> Unit,
  tagPrefix: String = "tx",
) {
  val today = remember { LocalDate.now().toEpochDay() }
  val yesterday = today - 1L
  val todayInCycle = remember(today, activeCycle) { isEpochDayInCycle(today, activeCycle) }
  val yesterdayInCycle = remember(yesterday, activeCycle) { isEpochDayInCycle(yesterday, activeCycle) }

  var showDatePicker by remember { mutableStateOf(false) }

  val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US) }
  val shortFormatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.US) }
  val formattedSelected =
    remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay).format(dateFormatter) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Expense Date", style = MaterialTheme.typography.labelLarge)
      if (activeCycle != null && activeCycle.endDateEpochDay >= activeCycle.startDateEpochDay) {
        val startStr = LocalDate.ofEpochDay(activeCycle.startDateEpochDay).format(shortFormatter)
        val endStr = LocalDate.ofEpochDay(activeCycle.endDateEpochDay).format(shortFormatter)
        Text(
          text = "Cycle: $startStr – $endStr",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (todayInCycle) {
        FilterChip(
          selected = selectedEpochDay == today,
          onClick = { onDateSelected(today) },
          label = { Text("Today") },
          modifier = Modifier.height(48.dp).testTag("${tagPrefix}_date_today"),
        )
      }
      if (yesterdayInCycle) {
        FilterChip(
          selected = selectedEpochDay == yesterday,
          onClick = { onDateSelected(yesterday) },
          label = { Text("Yesterday") },
          modifier = Modifier.height(48.dp).testTag("${tagPrefix}_date_yesterday"),
        )
      }
      OutlinedButton(
        onClick = { showDatePicker = true },
        modifier = Modifier.weight(1f).height(48.dp).testTag("${tagPrefix}_pick_date_button"),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = formattedSelected,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }

  if (showDatePicker) {
    val cycleStart = activeCycle?.startDateEpochDay
    val cycleEnd = activeCycle?.endDateEpochDay
    val hasValidRange = cycleStart != null && cycleEnd != null && cycleEnd >= cycleStart
    val initialMillis = selectedEpochDay * MILLIS_PER_DAY

    val datePickerState =
      rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates =
          object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
              if (!hasValidRange) return true
              val day = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY)
              return day in cycleStart!!..cycleEnd!!
            }

            override fun isSelectableYear(year: Int): Boolean {
              if (!hasValidRange) return true
              val startYear = LocalDate.ofEpochDay(cycleStart!!).year
              val endYear = LocalDate.ofEpochDay(cycleEnd!!).year
              return year in startYear..endYear
            }
          },
      )

    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val pickedDay = Math.floorDiv(millis, MILLIS_PER_DAY)
              onDateSelected(clampEpochDayToCycle(pickedDay, activeCycle))
            }
            showDatePicker = false
          },
          modifier = Modifier.testTag("${tagPrefix}_confirm_date_button"),
        ) {
          Text(stringResource(R.string.confirm_date))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text("Cancel")
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
