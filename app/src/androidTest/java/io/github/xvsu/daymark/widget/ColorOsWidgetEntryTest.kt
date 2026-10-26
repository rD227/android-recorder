package io.github.xvsu.daymark.widget

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xvsu.daymark.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The former MAIN/HOME intent opened this sheet and immediately closed it again. */
@RunWith(AndroidJUnit4::class)
class ColorOsWidgetEntryTest {
    @Test
    fun opensNativeWidgetSheetFromTheForegroundAppWithoutRoot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = ColorOsWidgetEntry.intent(context)
        assumeNotNull(intent)
        assumeTrue(context.resources.configuration.locales[0].language == "zh")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Wait for the app's own window so a previously open launcher sheet cannot pass.
            val appDeadline = SystemClock.uptimeMillis() + 8_000
            while (SystemClock.uptimeMillis() < appDeadline &&
                instrumentation.uiAutomation.rootInActiveWindow?.packageName != context.packageName
            ) {
                SystemClock.sleep(50)
            }
            assertTrue(
                "The app must be foreground before navigating",
                instrumentation.uiAutomation.rootInActiveWindow?.packageName == context.packageName,
            )
            scenario.onActivity { it.startActivity(requireNotNull(intent)) }
            val deadline = SystemClock.uptimeMillis() + 8_000
            var widgetSheetVisible = false
            var foregroundPackage: CharSequence? = null
            var visibleSince = 0L
            while (SystemClock.uptimeMillis() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                foregroundPackage = root?.packageName
                widgetSheetVisible = foregroundPackage == "com.android.launcher" &&
                    root.findAccessibilityNodeInfosByText("插件").isNotEmpty()
                if (widgetSheetVisible) {
                    if (visibleSince == 0L) visibleSince = SystemClock.uptimeMillis()
                    // Regression: the Home-key handler closed the sheet just after it appeared.
                    if (SystemClock.uptimeMillis() - visibleSince >= 800) break
                } else {
                    visibleSince = 0L
                }
                SystemClock.sleep(50)
            }
            assertTrue(
                "Expected the native widget sheet to remain open, foreground=$foregroundPackage",
                widgetSheetVisible && visibleSince != 0L && SystemClock.uptimeMillis() - visibleSince >= 800,
            )
        }
    }
}
