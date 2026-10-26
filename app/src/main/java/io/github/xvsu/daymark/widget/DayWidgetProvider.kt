package io.github.xvsu.daymark.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.github.xvsu.daymark.MainActivity
import io.github.xvsu.daymark.R
import io.github.xvsu.daymark.data.CardTone
import io.github.xvsu.daymark.data.CustomCardPalette
import io.github.xvsu.daymark.data.DayRecordStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** All launcher sizes share the same persisted record binding and rendering rules. */
abstract class DayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { DayWidgetUpdates.update(context, it) }
        DayWidgetUpdates.scheduleNextRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        DayWidgetUpdates.update(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val bindings = WidgetBindings(context)
        appWidgetIds.forEach(bindings::remove)
        DayWidgetUpdates.scheduleNextRefresh(context)
    }

    override fun onDisabled(context: Context) {
        DayWidgetUpdates.scheduleNextRefresh(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val bindings = WidgetBindings(context)
        // Snapshot first: old and new IDs can overlap during launcher backup restoration.
        val restored = oldWidgetIds.zip(newWidgetIds).map { (oldId, newId) ->
            newId to bindings.recordId(oldId)
        }
        oldWidgetIds.forEach(bindings::remove)
        restored.forEach { (newId, recordId) ->
            if (recordId != null) bindings.bind(newId, recordId)
        }
        DayWidgetUpdates.updateAll(context)
    }
}

class SquareDayWidgetProvider : DayWidgetProvider()

class WideDayWidgetProvider : DayWidgetProvider()

class CompactDayWidgetProvider : DayWidgetProvider()

internal class WidgetBindings(context: Context) {
    private val preferences = context.getSharedPreferences("day_widget_bindings", Context.MODE_PRIVATE)

    fun recordId(widgetId: Int): String? = preferences.getString("record_$widgetId", null)

    fun bind(widgetId: Int, recordId: String) {
        preferences.edit().putString("record_$widgetId", recordId).apply()
    }

    fun remove(widgetId: Int) {
        preferences.edit().remove("record_$widgetId").apply()
    }
}

object DayWidgetUpdates {
    private const val REFRESH_ACTION = "io.github.xvsu.daymark.action.REFRESH_WIDGETS"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT)

    private fun providers(context: Context) = listOf(
        ComponentName(context, SquareDayWidgetProvider::class.java),
        ComponentName(context, WideDayWidgetProvider::class.java),
        ComponentName(context, CompactDayWidgetProvider::class.java),
    )

    internal fun isOurWidget(context: Context, widgetId: Int): Boolean {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider
        return provider in providers(context)
    }

    /** Call after a record changes, and when the app returns to the foreground. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers(context).forEach { component ->
            manager.getAppWidgetIds(component).forEach { update(context, it) }
        }
        scheduleNextRefresh(context)
    }

    internal fun update(context: Context, widgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(widgetId) ?: return
        if (info.provider !in providers(context)) return
        val wide = info.provider.className ==
            WideDayWidgetProvider::class.java.name
        val compact = info.provider.className == CompactDayWidgetProvider::class.java.name
        val views = RemoteViews(
            context.packageName,
            when {
                compact -> R.layout.widget_day_compact
                wide -> R.layout.widget_day_wide
                else -> R.layout.widget_day_square
            },
        )
        val binding = WidgetBindings(context).recordId(widgetId)
        val record = binding?.let { DayRecordStore(context).find(it) }
        val today = LocalDate.now()
        val options = manager.getAppWidgetOptions(widgetId)
        // A pin callback can arrive before the launcher measures the card, with explicit zero
        // options. Start at the provider's declared minimum; optionsChanged supplies the real size.
        // AppWidgetManager resolves the provider's XML dimensions to pixels, while options use dp.
        val density = context.resources.displayMetrics.density
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }
            ?: (info.minWidth / density).roundToInt()
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
            ?: (info.minHeight / density).roundToInt()
        val colors = widgetColors(record?.tone ?: CardTone.SAGE)
        val customColor = record?.customColor
        views.setInt(android.R.id.background, "setBackgroundResource", colors.backgroundResource)
        if (customColor != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Older RemoteViews cannot set a literal ColorStateList. A background-only bitmap
            // preserves the same 28 dp corners without turning the card into a rectangle.
            views.setInt(android.R.id.background, "setBackgroundResource", 0)
            views.setImageViewBitmap(
                R.id.widget_custom_background,
                customWidgetBackground(context, customColor, width, height),
            )
            views.setViewVisibility(R.id.widget_custom_background, View.VISIBLE)
        } else {
            // RemoteViews may reapply to an existing card, so discard the old bitmap as well.
            views.setViewVisibility(R.id.widget_custom_background, View.GONE)
            views.setImageViewResource(R.id.widget_custom_background, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && customColor != null) {
            views.setColorStateList(
                android.R.id.background,
                "setBackgroundTintList",
                ColorStateList.valueOf(customColor),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Resource IDs resolve when the launcher applies the views, including night mode
            // and wallpaper colors. Tint every tone so switching away from SAGE resets it.
            views.setColorStateList(
                android.R.id.background,
                "setBackgroundTintList",
                colors.background,
            )
        }
        listOf(R.id.widget_headline, R.id.widget_count, R.id.widget_unit, R.id.widget_date)
            .forEach {
                if (customColor != null) {
                    views.setTextColor(it, CustomCardPalette.textColor(customColor))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColor(it, "setTextColor", colors.foreground)
                } else {
                    views.setTextColor(it, context.getColor(colors.foreground))
                }
            }

        if (record != null) {
            views.setTextViewText(R.id.widget_headline, record.headline(today))
            val count = record.dayCount(today).toString()
            views.setTextViewText(R.id.widget_count, count)
            views.setViewVisibility(R.id.widget_count_row, View.VISIBLE)
            views.setTextViewText(R.id.widget_date, "始于 ${record.startDate.format(dateFormat)}")
            val fontScale = context.resources.configuration.fontScale
            val horizontalPadding = if (compact) 16f else if (wide) 32f else 24f
            val verticalPadding = if (compact) 4f else horizontalPadding
            val rowSpacing = if (compact) 1f else 5f
            val headlineLines = if (compact) 1 else 2
            // A one-row launcher cell leaves less vertical room than the other two sizes.
            // Scale both lines together at large system font sizes before fitting the count.
            val compactScale = if (compact) {
                minOf(1f, (height - verticalPadding) / ((10f + 18f) * 1.3f * fontScale + rowSpacing))
            } else 1f
            val headlineSize = (if (compact) 10f else if (wide) 14f else 13f) * compactScale
            val unitSize = (if (compact) 12f else if (wide) 16f else 14f) * compactScale
            val showDate = !compact && height >= 150 * fontScale
            views.setViewVisibility(R.id.widget_date, if (showDate) View.VISIBLE else View.GONE)
            views.setTextViewTextSize(R.id.widget_headline, TypedValue.COMPLEX_UNIT_SP, headlineSize)
            views.setTextViewTextSize(R.id.widget_unit, TypedValue.COMPLEX_UNIT_SP, unitSize)
            val availableWidth = width - horizontalPadding - unitSize * fontScale - 5f
            val availableHeight = height - verticalPadding - headlineLines * headlineSize * fontScale * 1.3f - rowSpacing -
                if (showDate) 23 * fontScale else 0f
            val widthLimit = availableWidth / (count.length * 0.62f * fontScale)
            val heightLimit = availableHeight / (1.3f * fontScale)
            val fittedCountSize = minOf(widthLimit, heightLimit, if (compact) 30f else if (wide) 56f else 48f)
            val countSize = if (compact) fittedCountSize else fittedCountSize.coerceAtLeast(18f)
            views.setTextViewTextSize(R.id.widget_count, TypedValue.COMPLEX_UNIT_SP, countSize)
            views.setContentDescription(
                android.R.id.background,
                "${record.headline(today)}，${record.dayCount(today)} 天，始于 ${record.startDate}",
            )
        } else {
            val title = if (binding == null) "选择一个日子" else "这张卡片已删除"
            views.setTextViewText(R.id.widget_headline, title)
            views.setViewVisibility(R.id.widget_count_row, View.GONE)
            views.setViewVisibility(R.id.widget_date, if (compact) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_date, "点击选择桌面卡片")
            views.setContentDescription(android.R.id.background, "$title，点击选择桌面卡片")
        }
        val clickIntent = if (record != null) {
            Intent(context, MainActivity::class.java)
                .putExtra("record_id", record.id)
        } else {
            Intent(context, WidgetConfigureActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        clickIntent.data = Uri.parse("daymark://widget/$widgetId")
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        views.setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(
                context,
                widgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        manager.updateAppWidget(widgetId, views)
    }

    internal fun scheduleNextRefresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val hasWidgets = providers(context).any { manager.getAppWidgetIds(it).isNotEmpty() }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, WidgetRefreshReceiver::class.java).setAction(REFRESH_ACTION)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!hasWidgets) {
            alarms.cancel(pending)
            return
        }
        // A local date, rather than a 24-hour interval, also handles daylight-saving changes.
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        // No wakeup or exact-alarm permission: a sleeping device refreshes when it wakes.
        alarms.setWindow(AlarmManager.RTC, nextMidnight, 60 * 60 * 1000L, pending)
    }

    internal fun handlesRefresh(action: String?) = action in setOf(
        REFRESH_ACTION,
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
    )
}

private fun customWidgetBackground(context: Context, color: Int, widthDp: Int, heightDp: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (widthDp * density).roundToInt()
    val height = (heightDp * density).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val radius = 28 * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    Canvas(bitmap).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
    return bitmap
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DayWidgetUpdates.handlesRefresh(intent.action)) DayWidgetUpdates.updateAll(context)
    }
}

private data class WidgetColors(val backgroundResource: Int, val background: Int, val foreground: Int)

private fun widgetColors(tone: CardTone): WidgetColors {
    val (background, foreground, drawable) = when (tone) {
        CardTone.SAGE -> Triple(R.color.widget_sage, R.color.widget_on_sage, R.drawable.widget_background_sage)
        CardTone.CLAY -> Triple(R.color.widget_clay, R.color.widget_on_clay, R.drawable.widget_background_clay)
        CardTone.LAVENDER -> Triple(R.color.widget_lavender, R.color.widget_on_lavender, R.drawable.widget_background_lavender)
        CardTone.SAND -> Triple(R.color.widget_sand, R.color.widget_on_sand, R.drawable.widget_background_sand)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tone == CardTone.SAGE) {
        return WidgetColors(
            drawable,
            R.color.widget_dynamic_container,
            R.color.widget_dynamic_content,
        )
    }
    return WidgetColors(drawable, background, foreground)
}
