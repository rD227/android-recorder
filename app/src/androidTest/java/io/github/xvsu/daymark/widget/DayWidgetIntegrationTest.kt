package io.github.xvsu.daymark.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xvsu.daymark.R
import io.github.xvsu.daymark.data.CardTone
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
    fun twoWidgetSizesKeepIndependentRecordsAndRefreshAfterEditingAndDeleting() {
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
        // A private host for this run; do not touch any launcher or existing widget IDs.
        val hostId = UUID.randomUUID().hashCode() and Int.MAX_VALUE
        val host = onMain { AppWidgetHost(context, hostId) }
        val allocatedIds = mutableListOf<Int>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            store.save(squareRecord)
            store.save(wideRecord)
            assertEquals(squareRecord, DayRecordStore(context).find(squareRecord.id))
            assertEquals(wideRecord, DayRecordStore(context).find(wideRecord.id))

            onMain { host.startListening() }
            val squareId = host.allocateAppWidgetId().also(allocatedIds::add)
            val wideId = host.allocateAppWidgetId().also(allocatedIds::add)
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
            bindings.bind(squareId, squareRecord.id)
            bindings.bind(wideId, wideRecord.id)
            manager.updateAppWidgetOptions(squareId, widgetOptions(180, 180))
            manager.updateAppWidgetOptions(wideId, widgetOptions(270, 180))

            val squareView = onMain {
                host.createView(context, squareId, manager.getAppWidgetInfo(squareId))
            }
            val wideView = onMain {
                host.createView(context, wideId, manager.getAppWidgetInfo(wideId))
            }
            DayWidgetUpdates.update(context, squareId)
            DayWidgetUpdates.update(context, wideId)

            awaitView("Both widget sizes should render their own record") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                showsRecord(squareView, squareRecord) && showsRecord(wideView, wideRecord)
            }
            onMain {
                assertEquals(squareRecord.id, bindings.recordId(squareId))
                assertEquals(wideRecord.id, bindings.recordId(wideId))
                assertTrue(squareView.width < wideView.width)
                assertTrue(squareView.findViewById<View>(R.id.widget_count).height > 0)
                assertTrue(wideView.findViewById<View>(R.id.widget_count).height > 0)
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
            store.save(editedSquare)
            val reloadedStore = DayRecordStore(context)
            assertEquals(editedSquare, reloadedStore.find(squareRecord.id))
            assertEquals(1, reloadedStore.getAll().count { it.id == squareRecord.id })
            assertEquals(wideRecord, reloadedStore.find(wideRecord.id))
            DayWidgetUpdates.update(context, squareId)
            DayWidgetUpdates.update(context, wideId)
            awaitView("Editing a SAGE widget must replace its text and background tint") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                val squareBackground = squareView.findViewById<View>(android.R.id.background)
                val tintUpdated = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    squareBackground?.backgroundTintList?.defaultColor == context.getColor(R.color.widget_clay)
                showsRecord(squareView, editedSquare) && showsRecord(wideView, wideRecord) && tintUpdated
            }
            onMain {
                savePreview(squareView, "widget-square-integration.png")
                savePreview(wideView, "widget-wide-integration.png")
            }

            store.delete(squareRecord.id)
            DayWidgetUpdates.update(context, squareId)
            DayWidgetUpdates.update(context, wideId)
            awaitView("Deleting a record must show selection guidance without changing other widgets") {
                layout(squareView, 180, 180)
                layout(wideView, 270, 180)
                text(squareView, R.id.widget_headline) == "这张卡片已删除" &&
                    text(squareView, R.id.widget_date) == "点击选择桌面卡片" &&
                    squareView.findViewById<View>(R.id.widget_count_row)?.visibility == View.GONE &&
                    showsRecord(wideView, wideRecord)
            }
            assertEquals(null, DayRecordStore(context).find(squareRecord.id))
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

    /** Call only on the main thread, as a launcher would measure its hosted views. */
    private fun layout(view: View, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
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
