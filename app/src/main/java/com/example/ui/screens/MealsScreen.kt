package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CycleStatus
import com.example.data.model.MealEntry
import com.example.data.model.MessMember
import com.example.domain.MessCalculator
import com.example.ui.MessDashboardData
import com.example.ui.theme.TabularNumberStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MealsScreen(
  data: MessDashboardData,
  selectedDateEpochDay: Long,
  onSelectDate: (Long) -> Unit,
  onUpdateMeal: (
    memberId: String,
    dateEpochDay: Long,
    lunch: Double,
    dinner: Double,
    guestMeals: Double,
  ) -> Unit,
  onCopyYesterday: (Long) -> Unit,
  onMarkStandardDay: (Long) -> Unit,
) {
  val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)
  val todayEpochDay = LocalDate.now().toEpochDay()
  val isClosed = data.activeCycle?.status == CycleStatus.CLOSED.name

  val mealsForDate =
    data.cycleMeals.filter { it.dateEpochDay == selectedDateEpochDay }.associateBy { it.memberId }
  val dayTotalMeals = mealsForDate.values.sumOf { it.totalMeals() }

  val weekDays = (-3L..3L).map { offset -> selectedDateEpochDay + offset }
  val mealsByDay =
    data.cycleMeals.groupBy { it.dateEpochDay }.mapValues { (_, list) ->
      list.sumOf { it.totalMeals() }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 1. Date Header & Day Total
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            IconButton(
              onClick = { onSelectDate(selectedDateEpochDay - 1) },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("meals_prev_day"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
              )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Day Total: ${MessCalculator.formatMeals(dayTotalMeals)} meals",
                style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
                color = MaterialTheme.colorScheme.primary,
              )
            }

            IconButton(
              onClick = { onSelectDate(selectedDateEpochDay + 1) },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("meals_next_day"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
              )
            }
          }

          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(weekDays) { dayEpoch ->
              val d = LocalDate.ofEpochDay(dayEpoch)
              val isSelected = dayEpoch == selectedDateEpochDay
              val isToday = dayEpoch == todayEpochDay
              val dayCount = mealsByDay[dayEpoch] ?: 0.0
              Surface(
                shape = RoundedCornerShape(14.dp),
                color =
                  when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                  },
                modifier =
                  Modifier.width(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDate(dayEpoch) },
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                  Text(
                    text = d.format(DateTimeFormatter.ofPattern("EEE", Locale.US)),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                      if (isSelected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = d.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall.merge(TabularNumberStyle),
                    color =
                      if (isSelected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = MessCalculator.formatMeals(dayCount),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNumberStyle),
                    color =
                      if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                      else MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }

          if (!isClosed && data.members.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = { onCopyYesterday(selectedDateEpochDay) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("copy_yesterday_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.ContentCopy,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Yesterday", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              FilledTonalButton(
                onClick = { onMarkStandardDay(selectedDateEpochDay) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("mark_all_standard_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.DoneAll,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mark All L+D", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }
    }

    if (data.members.isEmpty()) {
      item {
        EmptyStateCard(
          icon = Icons.Filled.Group,
          title = "No members in this mess",
          message = "Add members in the Ledger tab to begin logging daily meals.",
        )
      }
    } else {
      items(data.members, key = { it.id }) { member ->
        val entry = mealsForDate[member.id] ?: MealEntry(memberId = member.id, dateEpochDay = selectedDateEpochDay)
        val updatedByName =
          entry.updatedByUid.takeIf { it.isNotBlank() }?.let { uid ->
            MessCalculator.resolveActorName(
              uid = uid,
              members = data.members,
              currentUserId = data.currentUserId,
              currentUserDisplayName = data.userProfile?.displayName.orEmpty(),
            )
          }
        MemberMealCard(
          member = member,
          entry = entry,
          updatedByName = updatedByName,
          isReadOnly = isClosed,
          onUpdate = { l, d, g ->
            onUpdateMeal(member.id, selectedDateEpochDay, l, d, g)
          },
        )
      }
    }
  }
}

@Composable
private fun MemberMealCard(
  member: MessMember,
  entry: MealEntry,
  updatedByName: String?,
  isReadOnly: Boolean,
  onUpdate: (Double, Double, Double) -> Unit,
) {
  val total = entry.totalMeals()

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier.size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = member.name.take(1).uppercase(),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          Column {
            Text(
              text = member.name,
              style = MaterialTheme.typography.titleMedium,
            )
            if (!updatedByName.isNullOrBlank()) {
              Text(
                text = "Updated by $updatedByName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("meal_updated_by_${member.id}"),
              )
            }
            if (!member.isActive) {
              Text(
                text = "Inactive for fixed bills",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Surface(
          color =
            if (total > 0.0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(10.dp),
        ) {
          Text(
            text = "${MessCalculator.formatMeals(total)} meals",
            style = MaterialTheme.typography.labelLarge.merge(TabularNumberStyle),
            color =
              if (total > 0.0) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MealStepperCell(
          label = "Lunch",
          value = entry.lunch,
          enabled = !isReadOnly,
          tagPrefix = "meal_l_${member.id}",
          onDelta = { delta ->
            val next = (entry.lunch + delta).coerceIn(0.0, 20.0)
            onUpdate(next, entry.dinner, entry.guestMeals)
          },
          modifier = Modifier.weight(1f),
        )
        MealStepperCell(
          label = "Dinner",
          value = entry.dinner,
          enabled = !isReadOnly,
          tagPrefix = "meal_d_${member.id}",
          onDelta = { delta ->
            val next = (entry.dinner + delta).coerceIn(0.0, 20.0)
            onUpdate(entry.lunch, next, entry.guestMeals)
          },
          modifier = Modifier.weight(1f),
        )
      }

      MealStepperCell(
        label = "Guest",
        value = entry.guestMeals,
        enabled = !isReadOnly,
        tagPrefix = "meal_g_${member.id}",
        onDelta = { delta ->
          val next = (entry.guestMeals + delta).coerceIn(0.0, 50.0)
          onUpdate(entry.lunch, entry.dinner, next)
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun MealStepperCell(
  label: String,
  value: Double,
  enabled: Boolean,
  tagPrefix: String,
  onDelta: (Double) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { onDelta(-1.0) },
          enabled = enabled && value > 0.0,
          modifier = Modifier.size(48.dp).testTag("${tagPrefix}_dec"),
        ) {
          Icon(
            imageVector = Icons.Filled.Remove,
            contentDescription = "Decrease $label",
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = MessCalculator.formatMeals(value),
          style =
            MaterialTheme.typography.titleMedium.merge(TabularNumberStyle).copy(
              fontWeight = FontWeight.Bold
            ),
        )
        IconButton(
          onClick = { onDelta(1.0) },
          enabled = enabled,
          modifier = Modifier.size(48.dp).testTag("${tagPrefix}_inc"),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Increase $label",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}
