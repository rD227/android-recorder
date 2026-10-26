package io.github.xvsu.daymark.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetEntryPolicyTest {
    @Test fun enablesOnlyVerifiedColorOsEntry() {
        assertTrue(WidgetEntryPolicy.useColorOsEntry("com.android.launcher", "com.android.launcher.Launcher", 160030016, true))
    }

    @Test fun newerAndOlderColorOsUseTheStandardFlowUntilVerified() {
        listOf(150000000L, 160030015L, 160030017L, 170000000L).forEach { version ->
            assertFalse(WidgetEntryPolicy.useColorOsEntry("com.android.launcher", "com.android.launcher.Launcher", version, true))
        }
    }

    @Test fun aMatchingVersionWithoutTheFeatureUsesTheStandardFlow() {
        assertFalse(WidgetEntryPolicy.useColorOsEntry("com.android.launcher", "com.android.launcher.Launcher", 160030016, false))
    }

    @Test fun otherHomeApplicationsNeverReceiveColorOsExtras() {
        assertFalse(WidgetEntryPolicy.useColorOsEntry("com.android.launcher3", "com.android.launcher3.Launcher", 160030016, true))
        assertFalse(WidgetEntryPolicy.useColorOsEntry("com.android.launcher", "com.example.HomeActivity", 160030016, true))
    }
}
