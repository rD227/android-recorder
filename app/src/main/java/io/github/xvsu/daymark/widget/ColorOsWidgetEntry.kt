package io.github.xvsu.daymark.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Opens the native widgets sheet in the supported Oplus home application, without Shelf or su. */
internal object ColorOsWidgetEntry {
    private val launcher = ComponentName("com.android.launcher", "com.android.launcher.Launcher")
    private const val WIDGET_ENTRY_FEATURE = "com.android.launcher.support.card_widget_entry_integration"

    fun intent(context: Context): Intent? {
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_META_DATA,
        )?.activityInfo ?: return null
        if (ComponentName(home.packageName, home.name) != launcher) return null
        if (home.applicationInfo.metaData?.getBoolean(WIDGET_ENTRY_FEATURE) != true) return null
        // MAIN/HOME closes floating views in the superclass after the widget sheet opens.
        // An explicit VIEW intent keeps this navigation separate from the Home-key handler.
        return Intent(Intent.ACTION_VIEW)
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("state", "ToggleBar_widget")
    }
}
