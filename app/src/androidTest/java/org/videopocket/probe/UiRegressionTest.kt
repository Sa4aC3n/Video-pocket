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
            val homeLeft = compose.onNodeWithText("Home").fetchSemanticsNode().boundsInRoot.left
            val settingsLeft = compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot.left
            assertTrue("English navigation must be LTR", homeLeft < settingsLeft)
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
            val homeRight = compose.onNodeWithText("الرئيسية").fetchSemanticsNode().boundsInRoot.right
            val settingsRight = compose.onNodeWithText("الإعدادات").fetchSemanticsNode().boundsInRoot.right
            assertTrue("Arabic navigation must be RTL", homeRight > settingsRight)
            saveScreenshot("arabic-system.png")
        } finally {
            compose.runOnUiThread { AppSettings.setLanguage(oldArabic); AppSettings.setTheme(oldTheme) }
        }
    }

    private fun saveScreenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(folder, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
