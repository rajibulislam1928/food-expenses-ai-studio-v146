package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(false) }
  var isCreatingAccount by remember { mutableStateOf(false) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  val heroBitmap by
    produceState<ImageBitmap?>(initialValue = null, context) {
      value = withContext(Dispatchers.IO) {
        runCatching {
          val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
          BitmapFactory.decodeResource(context.resources, R.drawable.img_mess_hero, options)?.asImageBitmap()
        }.getOrNull()
      }
    }

  Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
          MaterialTheme.colorScheme.background,
        )))
        .padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Card(
          modifier = Modifier.fillMaxWidth().height(190.dp),
          shape = RoundedCornerShape(24.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
          Box(
            modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
              MaterialTheme.colorScheme.primaryContainer,
            ))),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Filled.Restaurant, contentDescription = null,
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), modifier = Modifier.size(48.dp))
            val loadedBitmap = heroBitmap
            androidx.compose.animation.AnimatedVisibility(visible = loadedBitmap != null, enter = fadeIn(tween(220)), modifier = Modifier.fillMaxSize()) {
              if (loadedBitmap != null) Image(bitmap = loadedBitmap,
                contentDescription = "Food Expenses shared kitchen ledger illustration",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
          }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground)
          Text(stringResource(R.string.tagline_subtitle), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
          Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureRow(Icons.Filled.Restaurant, "Daily Meal Matrix & Live Rate",
              "Track lunch, dinner & guests with automatic per-meal rate math.")
            FeatureRow(Icons.Filled.CloudSync, "Real-Time Roommate Sync",
              "Invite hostel mates with a 6-character code so everyone sees live balances.")
            FeatureRow(Icons.Filled.TableChart, "Excel (.xlsx) Backup & Restore",
              "Export multi-sheet monthly or weekly reports directly to your device.")
          }
        }

        Text(if (isCreatingAccount) "Create your account" else "Sign in with email",
          style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = email, onValueChange = { email = it; errorMessage = null },
          modifier = Modifier.fillMaxWidth().testTag("email_field"), label = { Text("Email") },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(value = password, onValueChange = { password = it; errorMessage = null },
          modifier = Modifier.fillMaxWidth().testTag("password_field"), label = { Text("Password") },
          singleLine = true,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible },
              modifier = Modifier.testTag("password_visibility_toggle")) {
              Icon(
                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password",
              )
            }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))

        errorMessage?.let {
          Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()) {
            Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
          }
        }
        statusMessage?.let {
          Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
          onClick = {
            val cleanEmail = email.trim()
            when {
              cleanEmail.isBlank() -> errorMessage = "Enter your email address."
              !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> errorMessage = "Enter a valid email address."
              password.isBlank() -> errorMessage = "Enter your password."
              isCreatingAccount && password.length < 6 -> errorMessage = "Use a password with at least 6 characters."
              else -> {
                isLoading = true
                errorMessage = null
                statusMessage = null
                scope.launch {
                  try {
                    if (isCreatingAccount) {
                      Firebase.auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                    } else {
                      Firebase.auth.signInWithEmailAndPassword(cleanEmail, password).await()
                    }
                    onAuthSuccess()
                  } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: if (isCreatingAccount) "Account creation failed. Please try again." else "Sign in failed. Please check your details."
                  } finally {
                    isLoading = false
                  }
                }
              }
            }
          },
          enabled = !isLoading,
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("email_auth_button"),
          shape = RoundedCornerShape(16.dp),
        ) {
          if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
          else Text(if (isCreatingAccount) "Create account" else "Sign in", style = MaterialTheme.typography.titleMedium)
        }

        if (!isCreatingAccount) {
          TextButton(onClick = {
            val cleanEmail = email.trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
              errorMessage = "Enter your email above, then tap Forgot password."
              return@TextButton
            }
            isLoading = true
            errorMessage = null
            statusMessage = null
            scope.launch {
              try {
                Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
                statusMessage = "Password reset email sent. Check your inbox."
              } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Could not send the reset email. Please try again."
              } finally { isLoading = false }
            }
          }, enabled = !isLoading, modifier = Modifier.testTag("forgot_password_button")) {
            Text("Forgot password?")
          }
        }
        TextButton(onClick = {
          isCreatingAccount = !isCreatingAccount
          passwordVisible = false
          errorMessage = null
          statusMessage = null
        }, enabled = !isLoading, modifier = Modifier.testTag("auth_mode_toggle")) {
          Text(if (isCreatingAccount) "Already have an account? Sign in" else "Create a new account")
        }
      }
    }
  }
}

@Composable
private fun FeatureRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(38.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
