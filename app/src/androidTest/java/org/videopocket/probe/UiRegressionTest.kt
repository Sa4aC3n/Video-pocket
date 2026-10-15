package org.videopocket.probe

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.videopocket.probe.core.AppSettings
import org.videopocket.probe.core.ThemeMode
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languageNavigationAndThemeControlsWork() {
        val oldArabic = AppSettings.isArabic
        val oldTheme = AppSettings.themeMode
        try {
            compose.runOnUiThread { AppSettings.setLanguage(false); AppSettings.setTheme(ThemeMode.LIGHT) }
            compose.onNodeWithText("Paste video link").assertIsDisplayed()
            assertNavigationDirection(arabic = false)
            saveScreenshot("english-light.png")
            compose.onNodeWithText("Pocket").performClick()
            compose.onNodeWithText("Search title, format or collection").assertIsDisplayed()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Dark Mode").performClick().assertIsSelected()
            assertEquals(ThemeMode.DARK, AppSettings.themeMode)
            saveScreenshot("english-dark-settings.png")
            compose.onNodeWithText("العربية").performClick()
            assertTrue(AppSettings.isArabic)
            compose.onNodeWithText("تلقائي حسب النظام").performClick().assertIsSelected()
            assertEquals(ThemeMode.SYSTEM, AppSettings.themeMode)
            compose.onNodeWithText("الرئيسية").performClick()
            compose.onNodeWithText("الصق رابط الفيديو").assertIsDisplayed()
            assertNavigationDirection(arabic = true)
            saveScreenshot("arabic-system.png")
        } finally {
            compose.runOnUiThread { AppSettings.setLanguage(oldArabic); AppSettings.setTheme(oldTheme) }
        }
    }

    private fun saveScreenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        val prefix = if (isTabletRun()) "tablet-" else ""
        File(folder, prefix + name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun isTabletRun() = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("tabletUi") == "true"

    private fun assertNavigationDirection(arabic: Boolean) {
        val home = compose.onNodeWithText(if (arabic) "الرئيسية" else "Home").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithText(if (arabic) "الإعدادات" else "Settings").fetchSemanticsNode().boundsInRoot
        val vertical = kotlin.math.abs(home.center.x - settings.center.x) < 2f
        assertEquals("Navigation must adapt to the tested window width", isTabletRun(), vertical)
        if (vertical) {
            assertTrue(home.top < settings.top)
            val middle = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x
            assertTrue("Rail must follow interface direction", if (arabic) home.center.x > middle else home.center.x < middle)
        } else {
            assertTrue("Navigation must follow interface direction", if (arabic) home.right > settings.right else home.left < settings.left)
        }
    }
}
