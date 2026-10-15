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
            saveScreenshot("english-light.png")
            assertNavigationDirection(arabic = false)
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
            saveScreenshot("arabic-system.png")
            assertNavigationDirection(arabic = true)
        } finally {
            compose.runOnUiThread { AppSettings.setLanguage(oldArabic); AppSettings.setTheme(oldTheme) }
        }
    }

    private fun saveScreenshot(name: String) {
        val prefix = if (isTabletRun()) "tablet-" else ""
        TestEvidence.output(compose.activity, prefix + name).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun isTabletRun() = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("tabletUi") == "true"

    private fun assertNavigationDirection(arabic: Boolean) {
        val home = compose.onNodeWithText(if (arabic) "الرئيسية" else "Home").fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithText(if (arabic) "الإعدادات" else "Settings").fetchSemanticsNode().boundsInRoot
        val vertical = kotlin.math.abs(home.center.y - settings.center.y) > 40f
        val prefix = if (isTabletRun()) "tablet-" else ""
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        TestEvidence.output(compose.activity, prefix + if (arabic) "navigation-ar.json" else "navigation-en.json").bufferedWriter().use {
            it.write(org.json.JSONObject().put("rootWidth", root.width).put("rootHeight", root.height)
                .put("densityDpi", compose.activity.resources.configuration.densityDpi)
                .put("fontScale", compose.activity.resources.configuration.fontScale)
                .put("homeX", home.center.x).put("homeY", home.center.y)
                .put("settingsX", settings.center.x).put("settingsY", settings.center.y)
                .put("vertical", vertical).put("tabletRun", isTabletRun()).toString(2))
        }
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
