package io.github.xvsu.daymark.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import io.github.xvsu.daymark.data.DayRecordStore
import java.util.UUID

enum class DayWidgetSize(val label: String, val provider: Class<out DayWidgetProvider>) {
    COMPACT("2 × 1 · 轻巧", CompactDayWidgetProvider::class.java),
    SQUARE("2 × 2 · 方形", SquareDayWidgetProvider::class.java),
    WIDE("3 × 2 · 横向", WideDayWidgetProvider::class.java),
}

enum class WidgetPinStatus { REQUESTED, ADDED, MANUAL, RECORD_REMOVED }

/** The latest request is persisted so a launcher callback can outlive the foreground activity. */
class WidgetPinRequests(context: Context) {
    private val appContext = context.applicationContext
    val preferences: SharedPreferences = appContext.getSharedPreferences("widget_pin_request", Context.MODE_PRIVATE)

    fun status(): WidgetPinStatus? = preferences.getString("status", null)?.let(WidgetPinStatus::valueOf)

    fun request(activity: Activity, recordId: String, size: DayWidgetSize) {
        val id = UUID.randomUUID().toString()
        preferences.edit().putString("request_id", id)
            .putString("status", WidgetPinStatus.REQUESTED.name).apply()
        if (DayRecordStore(appContext).find(recordId) == null) {
            setStatus(id, WidgetPinStatus.RECORD_REMOVED)
            return
        }
        val manager = AppWidgetManager.getInstance(activity)
        if (!manager.isRequestPinAppWidgetSupported) {
            setStatus(id, WidgetPinStatus.MANUAL)
            return
        }
        val callback = createCallback(appContext, id, recordId, size)
        try {
            // A true return value means the launcher accepted the request, not that it added a widget.
            if (!manager.requestPinAppWidget(ComponentName(activity, size.provider), null, callback)) {
                callback.cancel()
                setStatus(id, WidgetPinStatus.MANUAL)
            }
        } catch (_: SecurityException) {
            // Some launchers reject pinning by policy. Manual desktop addition remains available.
            callback.cancel()
            setStatus(id, WidgetPinStatus.MANUAL)
        } catch (_: ActivityNotFoundException) {
            // The default launcher may be replaced or disabled between checking and requesting.
            callback.cancel()
            setStatus(id, WidgetPinStatus.MANUAL)
        }
    }

    fun clearStatus() { preferences.edit().clear().apply() }

    internal fun setStatus(requestId: String, status: WidgetPinStatus) {
        // Older requests still bind their own widget; only the most recent one updates this dialog.
        if (preferences.getString("request_id", null) == requestId) {
            preferences.edit().putString("status", status.name).apply()
        }
    }

    companion object {
        internal const val ACTION_PINNED = "io.github.xvsu.daymark.action.WIDGET_PINNED"
        internal const val EXTRA_REQUEST = "pin_request_id"
        internal const val EXTRA_RECORD = "pin_record_id"
        internal const val EXTRA_PROVIDER = "pin_provider"

        internal fun createCallback(context: Context, requestId: String, recordId: String, size: DayWidgetSize): PendingIntent {
            val intent = Intent(context, WidgetPinReceiver::class.java)
                .setAction(ACTION_PINNED)
                .setPackage(context.packageName)
                .setData(Uri.parse("daymark://pin/$requestId"))
                .putExtra(EXTRA_REQUEST, requestId)
                .putExtra(EXTRA_RECORD, recordId)
                .putExtra(EXTRA_PROVIDER, size.provider.name)
            // The system fills in EXTRA_APPWIDGET_ID when the user confirms. Immutable would discard it.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT or mutable)
        }
    }
}

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetPinRequests.ACTION_PINNED) return
        val requestId = intent.getStringExtra(WidgetPinRequests.EXTRA_REQUEST) ?: return
        val recordId = intent.getStringExtra(WidgetPinRequests.EXTRA_RECORD) ?: return
        val providerName = intent.getStringExtra(WidgetPinRequests.EXTRA_PROVIDER) ?: return
        val size = DayWidgetSize.entries.find { it.provider.name == providerName } ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return
        if (info.provider != ComponentName(context, size.provider)) return

        WidgetBindings(context).bind(widgetId, recordId)
        DayWidgetUpdates.updateAll(context)
        val status = if (DayRecordStore(context).find(recordId) != null) WidgetPinStatus.ADDED else WidgetPinStatus.RECORD_REMOVED
        WidgetPinRequests(context).setStatus(requestId, status)
    }
}
