package io.github.xvsu.daymark.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.data.PoetryUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
private val TodayFormat = DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)

@Composable
fun DaymarkApp(
    records: List<DayRecord>,
    today: LocalDate,
    onSave: (DayRecord) -> Unit,
    onDelete: (DayRecord) -> Unit,
    onAddWidget: () -> Unit,
    initialRecordId: String? = null,
    initialCreateRecord: Boolean = false,
    poetryState: PoetryUiState = PoetryUiState(),
    onRefreshPoem: () -> Unit = {},
) {
    var editorOpen by rememberSaveable(initialRecordId, initialCreateRecord) {
        mutableStateOf(initialRecordId != null || initialCreateRecord)
    }
    var editingId by rememberSaveable(initialRecordId) { mutableStateOf(initialRecordId) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingRecord = records.firstOrNull { it.id == editingId }
    val deletingRecord = records.firstOrNull { it.id == deletingId }

    fun openEditor(record: DayRecord?) {
        editingId = record?.id
        editorOpen = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openEditor(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一日", fontWeight = FontWeight.Medium) },
                shape = RoundedCornerShape(24.dp),
            )
        },
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") { HomeHeader(today, onAddWidget, poetryState, onRefreshPoem) }
                if (records.isEmpty()) {
                    item(key = "empty") { EmptyState() }
                } else {
                    item(key = "section") {
                        Row(
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "我的记日",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape,
                            ) {
                                Text(
                                    records.size.toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                        DayRecordCard(
                            record = record,
                            today = today,
                            index = index,
                            onEdit = { openEditor(record) },
                        )
                    }
                }
                item(key = "widget") { WidgetInvitation(onAddWidget) }
            }
        }
    }

    if (editorOpen) {
        RecordEditor(
            record = editingRecord,
            today = today,
            onDismiss = { editorOpen = false },
            onSave = {
                onSave(it)
                editorOpen = false
            },
            onDelete = {
                deletingId = it.id
            },
        )
    }
    if (deletingRecord != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("删除这张记日？") },
            text = { Text("“${deletingRecord.title}”将被删除，桌面上的对应小组件也会更新。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deletingRecord)
                    deletingId = null
                    editorOpen = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("保留") }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    today: LocalDate,
    onAddWidget: () -> Unit,
    poetryState: PoetryUiState,
    onRefreshPoem: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "记日",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
            )
            FilledTonalIconButton(
                onClick = onAddWidget,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
            ) {
                WidgetIcon(Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = today.format(TodayFormat),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        PoetryHeader(poetryState, onRefreshPoem)
    }
}

@Composable
private fun PoetryHeader(state: PoetryUiState, onRefresh: () -> Unit) {
    val poem = state.poem
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = poem?.content ?: if (state.isLoading) "诗词加载中…" else "诗词暂未加载",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (poem == null) "NULL at DaymarkApp.kt" else "${poem.author}《${poem.title}》",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = if (poem == null || state.error != null) "重试加载诗词" else "换一句诗词",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!state.isLoading && (state.error != null || poem == null)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (poem != null) {
                    Text(
                        "更新失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("点按重试", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DayRecordCard(
    record: DayRecord,
    today: LocalDate,
    index: Int,
    onEdit: () -> Unit,
) {
    val colors = cardColors(record.tone, record.customColor)
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 296.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "记日 · ${(index + 1).toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content.copy(alpha = 0.72f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑${record.title}",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = record.headline(today),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                DayCount(record.dayCount(today))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.content.copy(alpha = 0.66f),
                )
                Text(
                    text = record.startDate.format(ShortDateFormat),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun DayCount(days: Long) {
    val text = days.toString()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Fit long counts and large system fonts within the card without truncating a digit.
        val numberSize = minOf(72f, (maxWidth.value - 8f) / fontScale / (text.length * 0.6f + 0.4f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(
                text = text,
                fontSize = numberSize.sp,
                lineHeight = (numberSize * 1.15f).sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                "天",
                fontSize = minOf(22f, numberSize * 0.4f).sp,
                modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "每个开始\n都值得记住",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "一场相遇、一个新习惯\n或是属于你的重要日子。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun WidgetInvitation(onAddWidget: () -> Unit) {
    Surface(
        onClick = onAddWidget,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { WidgetIcon(Modifier.size(22.dp), contentDescription = null) }
            Column(modifier = Modifier.weight(1f)) {
                Text("把日子放在桌面", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "2 × 1 / 2 × 2 / 3 × 2 小组件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun WidgetIcon(modifier: Modifier = Modifier, contentDescription: String? = "添加桌面小组件") {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(
        modifier = modifier.then(
            if (contentDescription == null) Modifier else Modifier
                .semantics { this.contentDescription = contentDescription },
        ),
    ) {
        val gap = size.width * 0.18f
        val side = (size.width - gap) / 2
        val stroke = Stroke(width = 1.7.dp.toPx())
        for (row in 0..1) {
            for (column in 0..1) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(column * (side + gap) + stroke.width / 2, row * (side + gap) + stroke.width / 2),
                    size = Size(side - stroke.width, side - stroke.width),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = stroke,
                )
            }
        }
    }
}
