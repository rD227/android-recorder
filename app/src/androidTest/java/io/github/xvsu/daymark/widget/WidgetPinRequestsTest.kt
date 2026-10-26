package io.github.xvsu.daymark.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xvsu.daymark.R
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.data.DayRecordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses system PendingIntent delivery and a private launcher host, without opening pin dialogs. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class WidgetPinRequestsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun mutableOneShotCallbacksBindTheirOwnWidgetsAndRejectProviderMismatch() {
        val manager = AppWidgetManager.getInstance(context)
        val store = DayRecordStore(context)
        val bindings = WidgetBindings(context)
        val today = LocalDate.now()
        val compactRecord = DayRecord(UUID.randomUUID().toString(), "申请组件 · 紧凑", today.minusDays(321))
        val squareRecord = DayRecord(UUID.randomUUID().toString(), "申请组件 · 方形", today.plusDays(12))
        val host = onMain { AppWidgetHost(context, UUID.randomUUID().hashCode() and Int.MAX_VALUE) }
        val widgetIds = mutableListOf<Int>()
        val callbacks = mutableListOf<PendingIntent>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            store.save(compactRecord)
            store.save(squareRecord)
            onMain { host.startListening() }
            val compactId = host.allocateAppWidgetId().also(widgetIds::add)
            val squareId = host.allocateAppWidgetId().also(widgetIds::add)
            assertTrue(manager.bindAppWidgetIdIfAllowed(compactId, ComponentName(context, DayWidgetSize.COMPACT.provider)))
            assertTrue(manager.bindAppWidgetIdIfAllowed(squareId, ComponentName(context, DayWidgetSize.SQUARE.provider)))
            manager.updateAppWidgetOptions(compactId, widgetOptions(180, 72))
            manager.updateAppWidgetOptions(squareId, widgetOptions(180, 180))
            val compactView = onMain { host.createView(context, compactId, manager.getAppWidgetInfo(compactId)) }
            val squareView = onMain { host.createView(context, squareId, manager.getAppWidgetInfo(squareId)) }
            awaitViews("Both fresh widgets must initially await record selection") {
                text(compactView, R.id.widget_headline) == "选择一个日子" &&
                    text(squareView, R.id.widget_headline) == "选择一个日子"
            }

            // A valid ID from another provider must not be accepted as this compact request.
            val mismatched = callback(compactRecord.id, DayWidgetSize.COMPACT).also(callbacks::add)
            sendAndAwaitReceiver(mismatched, squareId)
            assertNull(bindings.recordId(squareId))
            assertNull(bindings.recordId(compactId))
            onMain { assertEquals("选择一个日子", text(squareView, R.id.widget_headline)) }

            // Create both before sending either: their distinct request URIs must preserve identity.
            val compactCallback = callback(compactRecord.id, DayWidgetSize.COMPACT).also(callbacks::add)
            val squareCallback = callback(squareRecord.id, DayWidgetSize.SQUARE).also(callbacks::add)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                assertFalse("The launcher must be able to fill in its allocated widget ID", compactCallback.isImmutable)
                assertFalse("Each request needs its own mutable callback", squareCallback.isImmutable)
            }

            // The ID exists only in the fill-in Intent. Binding/rendering proves it survived
            // real PendingIntent delivery to the manifest receiver; no receiver method is called here.
            sendAndAwaitReceiver(compactCallback, compactId)
            assertEquals(compactRecord.id, bindings.recordId(compactId))
            assertNull(bindings.recordId(squareId))
            awaitViews("The compact callback must refresh its card without binding the square card") {
                showsRecord(compactView, compactRecord) &&
                    text(squareView, R.id.widget_headline) == "选择一个日子"
            }

            sendAndAwaitReceiver(squareCallback, squareId)
            assertEquals(squareRecord.id, bindings.recordId(squareId))
            assertEquals(compactRecord.id, bindings.recordId(compactId))
            awaitViews("Both callback-created bindings must render their own date and count") {
                showsRecord(compactView, compactRecord) && showsRecord(squareView, squareRecord)
            }

            assertOneShot(compactCallback, squareId)
            assertOneShot(squareCallback, compactId)
            assertEquals(compactRecord.id, bindings.recordId(compactId))
            assertEquals(squareRecord.id, bindings.recordId(squareId))
        } finally {
            try {
                callbacks.forEach(PendingIntent::cancel)
                onMain {
                    widgetIds.forEach { id ->
                        host.deleteAppWidgetId(id)
                        bindings.remove(id)
                    }
                    host.stopListening()
                    host.deleteHost()
                }
            } finally {
                try {
                    store.delete(compactRecord.id)
                    store.delete(squareRecord.id)
                    // Only our IDs and UUID records are removed. Drain apply() before process exit.
                    assertTrue(context.getSharedPreferences("day_records", 0).edit().commit())
                    assertTrue(context.getSharedPreferences("day_widget_bindings", 0).edit().commit())
                    DayWidgetUpdates.scheduleNextRefresh(context)
                } finally {
                    instrumentation.uiAutomation.dropShellPermissionIdentity()
                }
            }
        }
    }

    private fun callback(recordId: String, size: DayWidgetSize): PendingIntent =
        WidgetPinRequests.createCallback(context, UUID.randomUUID().toString(), recordId, size)

    private fun sendAndAwaitReceiver(callback: PendingIntent, widgetId: Int) {
        val finished = CountDownLatch(1)
        callback.send(
            context,
            0,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            { _, _, _, _, _ -> finished.countDown() },
            Handler(Looper.getMainLooper()),
        )
        assertTrue("The system must finish delivering the pin callback", finished.await(8, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun assertOneShot(callback: PendingIntent, widgetId: Int) {
        try {
            callback.send(context, 0, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            fail("A delivered pin callback must not be reusable")
        } catch (_: PendingIntent.CanceledException) {
            // FLAG_ONE_SHOT consumes the capability on its first delivery.
        }
    }

    private fun widgetOptions(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun showsRecord(view: AppWidgetHostView, record: DayRecord): Boolean =
        text(view, R.id.widget_headline) == record.headline(LocalDate.now()) &&
            text(view, R.id.widget_count) == record.dayCount(LocalDate.now()).toString() &&
            view.findViewById<View>(R.id.widget_count_row)?.visibility == View.VISIBLE

    private fun text(view: View, id: Int): String? = view.findViewById<TextView>(id)?.text?.toString()

    private fun awaitViews(message: String, condition: () -> Boolean) {
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
}
