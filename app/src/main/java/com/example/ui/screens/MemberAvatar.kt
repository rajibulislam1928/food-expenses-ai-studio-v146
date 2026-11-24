package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun MemberAvatar(
  name: String,
  photoBase64: String,
  size: Dp = 40.dp,
  modifier: Modifier = Modifier,
) {
  val bitmap = remember(photoBase64) {
    photoBase64.takeIf { it.isNotBlank() }?.let { encoded ->
      runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
      }.getOrNull()
    }
  }
  val label = name.ifBlank { "Member" }
  var showPhoto by remember { mutableStateOf(false) }
  if (bitmap != null) {
    Image(
      bitmap = bitmap,
      contentDescription = "View $label profile photo",
      contentScale = ContentScale.Crop,
      modifier = modifier.size(size).clip(CircleShape)
        .semantics {
          role = Role.Button
          contentDescription = "View $label profile photo"
        }
        .clickable { showPhoto = true },
    )
    if (showPhoto) {
      Dialog(onDismissRequest = { showPhoto = false }) {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 6.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp),
          ) {
            Text("$label’s profile photo", style = MaterialTheme.typography.titleMedium)
            Image(
              bitmap = bitmap,
              contentDescription = "$label profile photo, enlarged",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                .testTag("profile_photo_preview_image"),
            )
            TextButton(
              onClick = { showPhoto = false },
              modifier = Modifier.testTag("profile_photo_preview_close"),
            ) {
              Text("Close")
            }
          }
        }
      }
    }
  } else {
    Box(
      modifier = modifier.size(size).clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer)
        .semantics { contentDescription = "$label profile" },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label.take(1).uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}
