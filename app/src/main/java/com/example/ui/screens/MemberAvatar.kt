package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
  if (bitmap != null) {
    Image(
      bitmap = bitmap,
      contentDescription = "$label profile photo",
      contentScale = ContentScale.Crop,
      modifier = modifier.size(size).clip(CircleShape),
    )
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
