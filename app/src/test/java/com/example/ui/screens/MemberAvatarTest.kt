package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemberAvatarTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun tappingProfilePhotoOpensLargeViewAndCloseDismissesIt() {
    val onePixelPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1WQAAAAASUVORK5CYII="
    composeRule.setContent {
      MaterialTheme {
        MemberAvatar(name = "Ava", photoBase64 = onePixelPng)
      }
    }

    composeRule.onNodeWithContentDescription("View Ava profile photo").performClick()
    composeRule.onNodeWithTag("profile_photo_preview_image").fetchSemanticsNode()
    composeRule.onNodeWithContentDescription("Ava profile photo, enlarged").fetchSemanticsNode()
    composeRule.onNodeWithTag("profile_photo_preview_close").performClick()
    composeRule.onAllNodesWithTag("profile_photo_preview_image").assertCountEquals(0)
  }
}
