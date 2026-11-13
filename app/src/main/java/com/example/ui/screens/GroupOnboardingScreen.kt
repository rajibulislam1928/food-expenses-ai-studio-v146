package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CycleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupOnboardingScreen(
  defaultUserName: String,
  isBusy: Boolean,
  onCreateGroup: (
    name: String,
    currency: String,
    cycleMode: CycleMode,
    creatorName: String,
    roommatesCsv: String,
  ) -> Unit,
  onJoinGroup: (inviteCode: String, memberName: String) -> Unit,
  onSignOut: () -> Unit,
  showSignOutInTopBar: Boolean = true,
) {
  var selectedMode by remember { mutableStateOf(0) } // 0 = Create, 1 = Join
  var messName by remember { mutableStateOf("") }
  var creatorName by remember { mutableStateOf(defaultUserName.ifBlank { "Manager" }) }
  var currency by remember { mutableStateOf("৳") }
  var cycleMode by remember { mutableStateOf(CycleMode.MONTHLY) }
  var roommatesCsv by remember { mutableStateOf("") }

  var inviteCode by remember { mutableStateOf("") }
  var joinName by remember { mutableStateOf(defaultUserName.ifBlank { "Member" }) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Setup Your Mess") },
        actions = {
          if (showSignOutInTopBar) {
            IconButton(
              onClick = onSignOut,
              modifier = Modifier.testTag("onboarding_sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign out",
              )
            }
          }
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Text(
          text = "Start a shared mess ledger or join your roommates using an invite code.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = selectedMode == 0,
            onClick = { selectedMode = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("mode_create_mess"),
            icon = { Icon(Icons.Filled.AddHome, contentDescription = null) },
          ) {
            Text("Create Mess")
          }
          SegmentedButton(
            selected = selectedMode == 1,
            onClick = { selectedMode = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("mode_join_mess"),
            icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
          ) {
            Text("Join via Code")
          }
        }

        if (selectedMode == 0) {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              OutlinedTextField(
                value = messName,
                onValueChange = { messName = it },
                label = { Text("Mess / Hostel Name") },
                placeholder = { Text("e.g. Emerald Flat 4B") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("create_mess_name_input"),
              )
              OutlinedTextField(
                value = creatorName,
                onValueChange = { creatorName = it },
                label = { Text("Your Member Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("create_creator_name_input"),
              )

              Text("Billing Cycle", style = MaterialTheme.typography.labelLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CycleMode.entries.forEach { mode ->
                  FilterChip(
                    selected = cycleMode == mode,
                    onClick = { cycleMode = mode },
                    label = { Text(mode.label) },
                  )
                }
              }

              Text("Currency Symbol", style = MaterialTheme.typography.labelLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("৳", "₹", "$", "£").forEach { sym ->
                  FilterChip(
                    selected = currency == sym,
                    onClick = { currency = sym },
                    label = { Text(sym) },
                  )
                }
              }

              OutlinedTextField(
                value = roommatesCsv,
                onValueChange = { roommatesCsv = it },
                label = { Text("Add Roommates (Optional)") },
                placeholder = { Text("Rahim, Karim, Tanvir") },
                supportingText = {
                  Text("Comma-separated names for offline/managed members")
                },
                modifier = Modifier.fillMaxWidth().testTag("create_roommates_input"),
              )

              Spacer(modifier = Modifier.height(4.dp))
              Button(
                onClick = {
                  onCreateGroup(
                    messName,
                    currency,
                    cycleMode,
                    creatorName,
                    roommatesCsv,
                  )
                },
                enabled = !isBusy && messName.isNotBlank() && creatorName.isNotBlank(),
                modifier =
                  Modifier.fillMaxWidth().height(52.dp).testTag("create_mess_submit_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(if (isBusy) "Creating..." else "Create Mess & Start Cycle")
              }
            }
          }
        } else {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it.uppercase().take(6) },
                label = { Text("6-Character Invite Code") },
                placeholder = { Text("e.g. KM7P2X") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("join_invite_code_input"),
              )
              OutlinedTextField(
                value = joinName,
                onValueChange = { joinName = it },
                label = { Text("Your Member Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("join_member_name_input"),
              )
              Button(
                onClick = { onJoinGroup(inviteCode, joinName) },
                enabled = !isBusy && inviteCode.trim().length == 6 && joinName.isNotBlank(),
                modifier =
                  Modifier.fillMaxWidth().height(52.dp).testTag("join_mess_submit_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(if (isBusy) "Joining..." else "Join Mess Group")
              }
            }
          }
        }
      }
    }
  }
}
