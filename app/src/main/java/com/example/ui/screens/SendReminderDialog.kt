package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.MessCycle
import com.example.data.model.MessGroup
import com.example.domain.MemberStanding
import com.example.domain.MessCalculator
import com.example.domain.ReminderMessageBuilder
import com.example.domain.ReminderStyle
import com.example.domain.SettlementTransfer
import com.example.ui.theme.NegativeCrimson
import com.example.ui.theme.NegativeCrimsonDark
import com.example.ui.theme.TabularNumberStyle
import kotlin.math.abs

@Composable
fun SendReminderDialog(
  standing: MemberStanding,
  group: MessGroup,
  cycle: MessCycle?,
  settlementTransfers: List<SettlementTransfer>,
  onCopied: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val isDark = isSystemInDarkTheme()
  val negativeColor = if (isDark) NegativeCrimsonDark else NegativeCrimson
  val formattedDue = MessCalculator.formatAmount(abs(standing.netBalance), group.currencySymbol)

  var selectedStyle by remember(standing.member.id) { mutableStateOf(ReminderStyle.DETAILED) }
  var messageText by remember(standing.member.id) {
    mutableStateOf(
      ReminderMessageBuilder.buildMessage(
        standing = standing,
        group = group,
        cycle = cycle,
        settlementTransfers = settlementTransfers,
        style = ReminderStyle.DETAILED,
      )
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).widthIn(max = 460.dp),
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 6.dp,
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                  .background(MaterialTheme.colorScheme.errorContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
              )
            }
            Column {
              Text(
                text = stringResource(R.string.send_reminder),
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "${standing.member.name} · Due $formattedDue",
                style = MaterialTheme.typography.bodySmall.merge(TabularNumberStyle),
                color = negativeColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_reminder_dialog"),
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Template Style",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderStyle.entries.forEach { style ->
              FilterChip(
                selected = selectedStyle == style,
                onClick = {
                  selectedStyle = style
                  messageText =
                    ReminderMessageBuilder.buildMessage(
                      standing = standing,
                      group = group,
                      cycle = cycle,
                      settlementTransfers = settlementTransfers,
                      style = style,
                    )
                },
                label = { Text(style.label) },
                modifier =
                  Modifier.testTag(
                    if (style == ReminderStyle.DETAILED) "reminder_style_detailed"
                    else "reminder_style_short"
                  ),
              )
            }
          }
        }

        OutlinedTextField(
          value = messageText,
          onValueChange = { messageText = it },
          label = { Text(stringResource(R.string.reminder_message_label)) },
          minLines = 6,
          maxLines = 12,
          modifier = Modifier.fillMaxWidth().testTag("reminder_message_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = {
              val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(
                ClipData.newPlainText("Mess Payment Reminder", messageText)
              )
              onCopied("Copied reminder for ${standing.member.name}")
              onDismiss()
            },
            enabled = messageText.isNotBlank(),
            modifier = Modifier.weight(1f).height(50.dp).testTag("copy_reminder_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.copy_reminder), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }

          Button(
            onClick = {
              val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(
                    Intent.EXTRA_SUBJECT,
                    "${group.name} Mess Balance Reminder (${standing.member.name})",
                  )
                  putExtra(Intent.EXTRA_TEXT, messageText)
                }
              context.startActivity(
                Intent.createChooser(
                  sendIntent,
                  context.getString(R.string.send_reminder_chooser_title, standing.member.name),
                )
              )
              onDismiss()
            },
            enabled = messageText.isNotBlank(),
            modifier = Modifier.weight(1.4f).height(50.dp).testTag("share_reminder_app_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.send_via_messaging_app),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
