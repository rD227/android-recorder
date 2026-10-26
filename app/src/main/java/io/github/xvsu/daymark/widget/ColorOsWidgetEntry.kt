package io.github.xvsu.daymark.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

/** Opens the native widgets sheet in the supported Oplus home application, without Shelf or su. */
internal object ColorOsWidgetEntry {
    private val launcher = ComponentName(WidgetEntryPolicy.COLOR_OS_PACKAGE, WidgetEntryPolicy.COLOR_OS_ACTIVITY)
    private const val WIDGET_ENTRY_FEATURE = "com.android.launcher.support.card_widget_entry_integration"

    fun intent(context: Context): Intent? {
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_META_DATA,
        )?.activityInfo ?: return null
        if (ComponentName(home.packageName, home.name) != launcher) return null
        val versionCode = try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(home.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info)
        } catch (_: PackageManager.NameNotFoundException) {
            // The default home was removed after resolving it; retain standard widget requests.
            return null
        }
        if (!WidgetEntryPolicy.useColorOsEntry(
                home.packageName, home.name, versionCode,
                home.applicationInfo.metaData?.getBoolean(WIDGET_ENTRY_FEATURE) == true,
            )
        ) return null
        // MAIN/HOME closes floating views in the superclass after the widget sheet opens.
        // An explicit VIEW intent keeps this navigation separate from the Home-key handler.
        return Intent(Intent.ACTION_VIEW)
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("state", "ToggleBar_widget")
    }
}
