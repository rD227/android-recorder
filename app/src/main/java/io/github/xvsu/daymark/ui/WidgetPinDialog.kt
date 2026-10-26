package io.github.xvsu.daymark.ui

import android.content.ActivityNotFoundException
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.xvsu.daymark.R
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.widget.DayWidgetSize
import io.github.xvsu.daymark.widget.ColorOsWidgetEntry
import io.github.xvsu.daymark.widget.WidgetPinRequests
import io.github.xvsu.daymark.widget.WidgetPinStatus

@Composable
internal fun WidgetPinDialog(
    records: List<DayRecord>,
    requests: WidgetPinRequests,
    onRequest: (String, DayWidgetSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val desktopEntry = remember(context) { ColorOsWidgetEntry.intent(context) }
    var useSystemFlow by rememberSaveable { mutableStateOf(false) }
    if (desktopEntry != null && !useSystemFlow) {
        val appName = stringResource(R.string.app_name)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("ColorOS 桌面组件") },
            text = {
                Text("打开系统的「插件」面板，找到「$appName」，选择组件尺寸后，再选择要显示的日期。")
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        context.startActivity(desktopEntry)
                        requests.clearStatus()
                        onDismiss()
                    } catch (_: ActivityNotFoundException) {
                        // The default home can be replaced while this dialog is open.
                        requests.clearStatus()
                        useSystemFlow = true
                    } catch (_: SecurityException) {
                        // A launcher update or policy may revoke access to its private entry.
                        requests.clearStatus()
                        useSystemFlow = true
                    }
                }) { Text("打开桌面组件") }
            },
            dismissButton = {
                TextButton(onClick = {
                    requests.clearStatus()
                    useSystemFlow = true
                }) { Text("系统标准添加") }
            },
        )
        return
    }
    var recordId by rememberSaveable { mutableStateOf(records.firstOrNull()?.id) }
    var sizeName by rememberSaveable { mutableStateOf(DayWidgetSize.COMPACT.name) }
    var status by remember { mutableStateOf(requests.status()) }
    DisposableEffect(requests) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> status = requests.status() }
        requests.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { requests.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("把日子放在桌面") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (status == null) {
                    if (records.isEmpty()) {
                        Text("先记下一个日子，再来选择桌面卡片。")
                    } else {
                        Text("选择日子", style = MaterialTheme.typography.titleSmall)
                        Column(Modifier.selectableGroup()) {
                            records.forEach { record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = record.id == recordId,
                                        role = Role.RadioButton,
                                        onClick = { recordId = record.id },
                                    ).padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = record.id == recordId, onClick = null)
                                    Text(record.title, Modifier.padding(start = 8.dp).weight(1f))
                                }
                            }
                        }
                        Text("组件尺寸（宽 × 高）", style = MaterialTheme.typography.titleSmall)
                        Column(Modifier.selectableGroup()) {
                            DayWidgetSize.entries.forEach { size ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = size.name == sizeName,
                                        role = Role.RadioButton,
                                        onClick = { sizeName = size.name },
                                    ).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = size.name == sizeName, onClick = null)
                                    Text(size.label, Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                } else {
                    Text(when (status) {
                        WidgetPinStatus.ADDED -> "已添加到桌面，卡片已经关联所选日子。"
                        WidgetPinStatus.REQUESTED -> "已向桌面发出申请，请在系统弹窗中确认添加。"
                        WidgetPinStatus.RECORD_REMOVED -> "所选日子已被删除。若组件已经添加，请点按组件重新选择。"
                        else -> "在桌面组件中选择尺寸，再选择要显示的日子。"
                    })
                }
            }
        },
        confirmButton = {
            if (status == null && records.any { it.id == recordId }) {
                Button(onClick = { onRequest(requireNotNull(recordId), DayWidgetSize.valueOf(sizeName)) }) { Text("申请添加") }
            } else {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (status != null) {
                TextButton(onClick = { requests.clearStatus(); status = null }) { Text("重新选择") }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
