package io.github.xvsu.daymark.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.xvsu.daymark.data.CardTone
import io.github.xvsu.daymark.data.DayRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private val EditorDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordEditor(
    record: DayRecord?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (DayRecord) -> Unit,
    onDelete: (DayRecord) -> Unit,
) {
    var title by rememberSaveable(record?.id) { mutableStateOf(record?.title.orEmpty()) }
    var dateEpochDay by rememberSaveable(record?.id) { mutableStateOf((record?.startDate ?: today).toEpochDay()) }
    var toneName by rememberSaveable(record?.id) { mutableStateOf((record?.tone ?: CardTone.SAGE).name) }
    var customColor by rememberSaveable(record?.id) { mutableStateOf(record?.customColor) }
    val initialHsv = remember(record?.id) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(record?.customColor ?: customCardColor(210f, 0.72f), it)
        }
    }
    var hue by rememberSaveable(record?.id) { mutableStateOf(initialHsv[0]) }
    var whiteness by rememberSaveable(record?.id) { mutableStateOf(1f - initialHsv[1]) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val tone = CardTone.valueOf(toneName)
    val trimmedTitle = title.trim()
    val titleTooLong = trimmedTitle.length > 40
    val canSave = trimmedTitle.isNotEmpty() && !titleTooLong
    val keyboard = LocalSoftwareKeyboardController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (record == null) "新的开始" else "编辑记日",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "给这一天，留一个位置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("日子的名字") },
                placeholder = { Text("例如：第一次相遇") },
                singleLine = true,
                isError = titleTooLong,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (titleTooLong) "名字最多 40 个字" else "记下一个对你重要的开始",
                            modifier = Modifier.weight(1f),
                        )
                        Text("${trimmedTitle.length}/40", modifier = Modifier.padding(start = 8.dp))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = {
                    keyboard?.hide()
                    datePickerOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开始日期", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(date.format(EditorDateFormat), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("更改", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (date > today) "未来的日子，会显示距离开始还有多少天。" else "从这一天开始，记录已经走过的每一天。",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(26.dp))
            Text("卡片色彩", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardTone.entries.forEach { option ->
                    ToneOption(
                        tone = option,
                        selected = customColor == null && tone == option,
                        onClick = {
                            toneName = option.name
                            customColor = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { customColor = customCardColor(hue, whiteness) },
                modifier = Modifier.fillMaxWidth().semantics { selected = customColor != null },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (customColor != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                if (customColor != null) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text("自定义配色")
            }
            customColor?.let { selectedColor ->
                Spacer(Modifier.height(16.dp))
                CardColorPicker(
                    hue = hue,
                    whiteness = whiteness,
                    color = selectedColor,
                    previewDays = abs(java.time.temporal.ChronoUnit.DAYS.between(date, today)),
                    onHueChange = {
                        hue = it
                        customColor = customCardColor(hue, whiteness)
                    },
                    onWhitenessChange = {
                        whiteness = it
                        customColor = customCardColor(hue, whiteness)
                    },
                )
            }
            Spacer(Modifier.height(30.dp))
            Button(
                enabled = canSave,
                onClick = {
                    keyboard?.hide()
                    onSave(DayRecord(
                        id = record?.id ?: UUID.randomUUID().toString(),
                        title = trimmedTitle,
                        startDate = date,
                        tone = tone,
                        customColor = customColor,
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("保存这一天", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
            if (record != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onDelete(record) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("删除这张记日", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
    if (datePickerOpen) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    enabled = picker.selectedDateMillis != null,
                    onClick = {
                        picker.selectedDateMillis?.let {
                            dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        datePickerOpen = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = picker, showModeToggle = true)
        }
    }
}

@Composable
private fun ToneOption(
    tone: CardTone,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = cardColors(tone)
    Column(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = colors.container,
            contentColor = colors.content,
            border = if (selected) BorderStroke(2.dp, colors.content) else null,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(12.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tone.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
