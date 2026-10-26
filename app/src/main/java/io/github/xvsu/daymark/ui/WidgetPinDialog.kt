package io.github.xvsu.daymark.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.widget.DayWidgetSize
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
                        else -> "当前桌面未接受自动添加，请按下方步骤手动添加。"
                    })
                }
                if (status != WidgetPinStatus.ADDED) {
                    Text(
                        "没有出现弹窗，或更习惯手动添加？\n长按桌面空白处 → 小组件 / 插件 → 正数倒数日，选择尺寸后再选日子。ColorOS 的入口名称会随桌面版本变化。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { openHome(context) }) { Text("前往桌面") }
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

private fun openHome(context: Context) {
    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
