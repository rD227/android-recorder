package io.github.xvsu.daymark.widget

/** Unverified launcher versions retain Android's standard widget request flow. */
internal object WidgetEntryPolicy {
    const val COLOR_OS_PACKAGE = "com.android.launcher"
    const val COLOR_OS_ACTIVITY = "com.android.launcher.Launcher"
    private val verifiedVersions = setOf(160030016L) // Oplus Launcher 16.3.16, PLK110.

    fun useColorOsEntry(
        packageName: String,
        activityName: String,
        versionCode: Long,
        hasWidgetEntry: Boolean,
    ): Boolean = packageName == COLOR_OS_PACKAGE && activityName == COLOR_OS_ACTIVITY &&
        versionCode in verifiedVersions && hasWidgetEntry
}
