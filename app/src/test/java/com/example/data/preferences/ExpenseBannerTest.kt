package com.example.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExpenseBannerTest {

  @Test
  fun `app build config matches v1_4_12 and versionCode 52`() {
    assertEquals(52, BuildConfig.VERSION_CODE)
    assertEquals("1.4.12", BuildConfig.VERSION_NAME)
  }

  @Test
  fun `expense banner visibility persists dismissal per version 1_4_6 and resets on newer version`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val prefs = ExpenseBannerPreferences(context)

      assertTrue(prefs.isBannerVisible("1.4.6").first())

      prefs.dismissBannerForVersion("1.4.6")
      assertFalse(prefs.isBannerVisible("1.4.6").first())
      assertTrue(prefs.isBannerVisible("1.4.10").first())
    }
}
