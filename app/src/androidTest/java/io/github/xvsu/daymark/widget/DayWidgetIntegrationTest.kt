package io.github.xvsu.daymark.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xvsu.daymark.R
import io.github.xvsu.daymark.data.CardTone
import io.github.xvsu.daymark.data.CustomCardPalette
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.data.DayRecordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Exercises the same RemoteViews and widget service used by an actual launcher. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DayWidgetIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun threeWidgetSizesKeepIndependentRecordsAndRefreshAfterEditingAndDeleting() {
        val manager = AppWidgetManager.getInstance(context)
        val store = DayRecordStore(context)
        val bindings = WidgetBindings(context)
        val today = LocalDate.now()
        val squareRecord = DayRecord(
            UUID.randomUUID().toString(), "组件验证 · 方形", today.minusDays(123), CardTone.SAGE,
        )
        val wideRecord = DayRecord(
            UUID.randomUUID().toString(), "组件验证 · 横向", today.plusDays(6), CardTone.SAGE,
        )
        val compactRecord = DayRecord(
            UUID.randomUUID().toString(), "组件验证 · 紧凑", today.minusDays(12345), CardTone.SAGE,
        )
        // A private host for this run; do not touch any launcher or existing widget IDs.
        val hostId = UUID.randomUUID().hashCode() and Int.MAX_VALUE
        val host = onMain { AppWidgetHost(context, hostId) }
        val allocatedIds = mutableListOf<Int>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            store.save(squareRecord)
            store.save(wideRecord)
            store.save(compactRecord)
            assertEquals(squareRecord, DayRecordStore(context).find(squareRecord.id))
            assertEquals(wideRecord, DayRecordStore(context).find(wideRecord.id))
            assertEquals(compactRecord, DayRecordStore(context).find(compactRecord.id))

            onMain { host.startListening() }
            val squareId = host.allocateAppWidgetId().also(allocatedIds::add)
            val wideId = host.allocateAppWidgetId().also(allocatedIds::add)
            val compactId = host.allocateAppWidgetId().also(allocatedIds::add)
            assertTrue(
                "The system must allow binding the square provider to the test host",
                manager.bindAppWidgetIdIfAllowed(
                    squareId, ComponentName(context, SquareDayWidgetProvider::class.java),
                ),
            )
            assertTrue(
                "The system must allow binding the wide provider to the test host",
                manager.bindAppWidgetIdIfAllowed(
                    wideId, ComponentName(context, WideDayWidgetProvider::class.java),
                ),
            )
            assertTrue(
                "The system must allow binding the compact provider to the test host",
                manager.bindAppWidgetIdIfAllowed(
                    compactId, ComponentName(context, CompactDayWidgetProvider::class.java),
                ),
            )
            bindings.bind(squareId, squareRecord.id)
            bindings.bind(wideId, wideRecord.id)
            bindings.bind(compactId, compactRecord.id)
            manager.updateAppWidgetOptions(squareId, widgetOptions(180, 180))
            manager.updateAppWidgetOptions(wideId, widgetOptions(270, 180))
            manager.updateAppWidgetOptions(compactId, widgetOptions(180, 72))

            val squareView = onMain {
                host.createView(context, squareId, manager.getAppWidgetInfo(squareId))
            }
            val wideView = onMain {
                host.createView(context, wideId, manager.getAppWidgetInfo(wideId))
            }
            val compactView = onMain {
                host.createView(context, compactId, manager.getAppWidgetInfo(compactId))
            }
            DayWidgetUpdates.update(context, squareId)
            DayWidgetUpdates.update(context, wideId)
            DayWidgetUpdates.update(context, compactId)

            awaitView("All three widget sizes should render their own record") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                showsRecord(squareView, squareRecord) && showsRecord(wideView, wideRecord) &&
                    showsRecord(compactView, compactRecord)
            }
            onMain {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                assertEquals(squareRecord.id, bindings.recordId(squareId))
                assertEquals(wideRecord.id, bindings.recordId(wideId))
                assertEquals(compactRecord.id, bindings.recordId(compactId))
                assertTrue(squareView.width < wideView.width)
                assertTrue(squareView.findViewById<View>(R.id.widget_count).height > 0)
                assertTrue(wideView.findViewById<View>(R.id.widget_count).height > 0)
                assertTrue(compactView.height < squareView.height)
                assertCompactTextFits(compactView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    assertEquals(
                        context.getColor(R.color.widget_dynamic_container),
                        squareView.findViewById<View>(android.R.id.background)
                            .backgroundTintList?.defaultColor,
                    )
                }
            }

            val editedSquare = squareRecord.copy(
                title = "组件验证 · 已编辑", startDate = today.minusDays(456), tone = CardTone.CLAY,
            )
            val editedCompact = compactRecord.copy(
                title = "2 × 1 紧凑组件 · 已编辑的长标题", startDate = today.minusDays(987654),
                tone = CardTone.LAVENDER,
            )
            store.save(editedSquare)
            store.save(editedCompact)
            val reloadedStore = DayRecordStore(context)
            assertEquals(editedSquare, reloadedStore.find(squareRecord.id))
            assertEquals(1, reloadedStore.getAll().count { it.id == squareRecord.id })
            assertEquals(wideRecord, reloadedStore.find(wideRecord.id))
            assertEquals(editedCompact, reloadedStore.find(compactRecord.id))
            // Exercise the shared refresh entry point, including the newly registered size.
            DayWidgetUpdates.updateAll(context)
            awaitView("Editing a SAGE widget must replace its text and background tint") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                val squareBackground = squareView.findViewById<View>(android.R.id.background)
                val tintUpdated = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    squareBackground?.backgroundTintList?.defaultColor == context.getColor(R.color.widget_clay)
                val compactTintUpdated = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    compactView.findViewById<View>(android.R.id.background)?.backgroundTintList?.defaultColor ==
                    context.getColor(R.color.widget_lavender)
                showsRecord(squareView, editedSquare) && showsRecord(wideView, wideRecord) &&
                    showsRecord(compactView, editedCompact) && tintUpdated && compactTintUpdated
            }

            // Provider dimensions describe its content; AppWidgetHostView adds launcher padding.
            // Six-digit counts and a long title must fit even at the declared 110 × 40 dp minimum.
            manager.updateAppWidgetOptions(compactId, widgetOptions(110, 40))
            DayWidgetUpdates.update(context, compactId)
            awaitView("The compact widget must apply the minimum-size typography") {
                layout(compactView, 110, 40)
                val count = compactView.findViewById<TextView>(R.id.widget_count)
                showsRecord(compactView, editedCompact) && count != null &&
                    count.textSize / context.resources.displayMetrics.scaledDensity < 18f
            }
            onMain {
                layout(compactView, 110, 40)
                assertCompactTextFits(compactView)
            }

            manager.updateAppWidgetOptions(compactId, widgetOptions(180, 72))
            DayWidgetUpdates.update(context, compactId)
            awaitView("Resizing the compact widget must restore its larger count") {
                layout(compactView, 180, 72)
                val count = compactView.findViewById<TextView>(R.id.widget_count)
                showsRecord(compactView, editedCompact) && count != null &&
                    count.textSize / context.resources.displayMetrics.scaledDensity > 18f
            }

            val customSquare = editedSquare.copy(customColor = 0xFF183C55.toInt())
            val customWide = wideRecord.copy(customColor = 0xFFF7BE67.toInt())
            val customCompact = editedCompact.copy(customColor = 0xFF4F153D.toInt())
            val sageBackground = context.getColor(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.color.widget_dynamic_container else R.color.widget_sage,
            )
            val sageForeground = context.getColor(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.color.widget_dynamic_content else R.color.widget_on_sage,
            )
            store.save(customSquare)
            DayWidgetUpdates.updateAll(context)
            awaitView("A custom color must only affect the widget bound to its record") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                customColorsMatch(squareView, customSquare.customColor!!) &&
                    widgetColorsMatch(wideView, sageBackground, sageForeground) &&
                    widgetColorsMatch(
                        compactView, context.getColor(R.color.widget_lavender), context.getColor(R.color.widget_on_lavender),
                    )
            }

            store.save(customWide)
            store.save(customCompact)
            DayWidgetUpdates.updateAll(context)
            awaitView("All widget sizes must display custom backgrounds and readable text") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                customColorsMatch(squareView, customSquare.customColor!!) &&
                    customColorsMatch(wideView, customWide.customColor!!) &&
                    customColorsMatch(compactView, customCompact.customColor!!)
            }

            // A newly pinned widget may have explicit zero options until its launcher measures it.
            // It must render at the provider minimum, including a nonzero legacy bitmap background.
            manager.updateAppWidgetOptions(compactId, widgetOptions(0, 0))
            DayWidgetUpdates.update(context, compactId)
            awaitView("Zero launcher options must render the declared minimum size") {
                layout(compactView, 110, 40)
                val count = compactView.findViewById<TextView>(R.id.widget_count)
                customColorsMatch(compactView, customCompact.customColor!!) && count.textSize > 0 &&
                    count.textSize / context.resources.displayMetrics.scaledDensity < 18f
            }
            onMain {
                layout(compactView, 110, 40)
                assertCompactTextFits(compactView)
            }
            manager.updateAppWidgetOptions(compactId, widgetOptions(180, 72))
            DayWidgetUpdates.update(context, compactId)
            awaitView("Measured launcher options must replace the initial minimum typography") {
                layout(compactView, 180, 72)
                val count = compactView.findViewById<TextView>(R.id.widget_count)
                customColorsMatch(compactView, customCompact.customColor!!) &&
                    count.textSize / context.resources.displayMetrics.scaledDensity > 18f
            }
            onMain {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                assertCompactTextFits(compactView)
                savePreview(squareView, "widget-custom-square-integration.png")
                savePreview(wideView, "widget-custom-wide-integration.png")
                savePreview(compactView, "widget-custom-compact-integration.png")
            }

            store.save(editedSquare)
            store.save(wideRecord)
            store.save(editedCompact)
            DayWidgetUpdates.updateAll(context)
            awaitView("Returning to presets must clear custom backgrounds and restore every text color") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                widgetColorsMatch(
                    squareView, context.getColor(R.color.widget_clay), context.getColor(R.color.widget_on_clay),
                ) && widgetColorsMatch(wideView, sageBackground, sageForeground) && widgetColorsMatch(
                    compactView, context.getColor(R.color.widget_lavender), context.getColor(R.color.widget_on_lavender),
                )
            }
            onMain {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                assertCompactTextFits(compactView)
                savePreview(squareView, "widget-square-integration.png")
                savePreview(wideView, "widget-wide-integration.png")
                savePreview(compactView, "widget-compact-integration.png")
            }

            store.delete(squareRecord.id)
            store.delete(compactRecord.id)
            DayWidgetUpdates.updateAll(context)
            awaitView("Deleting a record must show selection guidance without changing other widgets") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                layout(compactView, 180, 72)
                text(squareView, R.id.widget_headline) == "这张卡片已删除" &&
                    text(squareView, R.id.widget_date) == "点击选择桌面卡片" &&
                    squareView.findViewById<View>(R.id.widget_count_row)?.visibility == View.GONE &&
                    text(compactView, R.id.widget_headline) == "这张卡片已删除" &&
                    compactView.findViewById<View>(R.id.widget_count_row)?.visibility == View.GONE &&
                    compactView.findViewById<View>(R.id.widget_date)?.visibility == View.GONE &&
                    showsRecord(wideView, wideRecord)
            }
            assertEquals(null, DayRecordStore(context).find(squareRecord.id))
            assertEquals(null, DayRecordStore(context).find(compactRecord.id))
            assertEquals(wideRecord, DayRecordStore(context).find(wideRecord.id))
        } finally {
            // Only remove IDs and records allocated above, preserving any real user data.
            try {
                onMain {
                    allocatedIds.forEach { widgetId ->
                        host.deleteAppWidgetId(widgetId)
                        bindings.remove(widgetId)
                    }
                    host.stopListening()
                    host.deleteHost()
                }
            } finally {
                try {
                    store.delete(squareRecord.id)
                    store.delete(wideRecord.id)
                    store.delete(compactRecord.id)
                    // Instrumentation may end without an Activity stop to drain pending writes.
                    // Flush the test cleanup before Android terminates the instrumented process.
                    assertTrue(context.getSharedPreferences("day_records", 0).edit().commit())
                    DayWidgetUpdates.scheduleNextRefresh(context)
                } finally {
                    instrumentation.uiAutomation.dropShellPermissionIdentity()
                }
            }
        }
    }

    private fun widgetOptions(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun showsRecord(view: AppWidgetHostView, record: DayRecord): Boolean {
        val today = LocalDate.now()
        return text(view, R.id.widget_headline) == record.headline(today) &&
            text(view, R.id.widget_count) == record.dayCount(today).toString() &&
            view.findViewById<View>(R.id.widget_count_row)?.visibility == View.VISIBLE
    }

    private fun text(view: View, id: Int): String? = view.findViewById<TextView>(id)?.text?.toString()

    private fun customColorsMatch(view: AppWidgetHostView, background: Int) =
        widgetColorsMatch(view, background, CustomCardPalette.textColor(background), custom = true)

    private fun widgetColorsMatch(
        view: AppWidgetHostView,
        background: Int,
        foreground: Int,
        custom: Boolean = false,
    ): Boolean {
        val card = view.findViewById<View>(android.R.id.background)
        val image = view.findViewById<ImageView>(R.id.widget_custom_background)
        val backgroundMatches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            image.visibility == View.GONE && card.backgroundTintList?.defaultColor == background
        } else if (custom) {
            val bitmap = (image.drawable as? BitmapDrawable)?.bitmap
            image.visibility == View.VISIBLE && bitmap != null &&
                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == background &&
                Color.alpha(bitmap.getPixel(0, 0)) == 0
        } else {
            image.visibility == View.GONE && image.drawable == null &&
                (card.background as? GradientDrawable)?.color?.defaultColor == background
        }
        return backgroundMatches &&
            listOf(R.id.widget_headline, R.id.widget_count, R.id.widget_unit, R.id.widget_date)
                .all { view.findViewById<TextView>(it).currentTextColor == foreground }
    }

    /** Call only on the main thread, as a launcher would measure its hosted views. */
    private fun layout(view: View, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt() + view.paddingLeft + view.paddingRight
        val height = (heightDp * density).toInt() + view.paddingTop + view.paddingBottom
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun assertCompactTextFits(view: AppWidgetHostView) {
        val content = view.findViewById<View>(R.id.widget_content)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_date).visibility)
        val title = view.findViewById<TextView>(R.id.widget_headline)
        val row = view.findViewById<View>(R.id.widget_count_row)
        assertTrue("The compact title must stay inside the card", title.top >= content.paddingTop)
        assertTrue(
            "The compact count row must stay inside the card",
            row.bottom <= content.height - content.paddingBottom,
        )
        listOf(R.id.widget_headline, R.id.widget_count, R.id.widget_unit).forEach { id ->
            val textView = view.findViewById<TextView>(id)
            assertTrue("Compact text must have one visible line", textView.layout.lineCount == 1)
            assertTrue("Compact text must not be vertically clipped", textView.layout.height <= textView.height)
            if (id != R.id.widget_headline) {
                assertTrue("The day count and unit must not be truncated", textView.layout.getEllipsisCount(0) == 0)
                assertTrue("The day count and unit must fit their width", textView.layout.getLineWidth(0) <= textView.width)
                assertTrue("The day count and unit must stay in the row", textView.right <= row.width)
            }
        }
    }

    private fun awaitView(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(50)
        }
        assertTrue(message, onMain(condition))
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return result!!.getOrThrow()
    }

    private fun savePreview(view: View, name: String) {
        assertTrue("A rendered widget must have nonzero bounds", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            val previewFile = File(context.cacheDir, name)
            previewFile.outputStream().use { output ->
                assertTrue("The widget preview should encode as PNG", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
