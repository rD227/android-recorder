package io.github.xvsu.daymark.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xvsu.daymark.MainActivity
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.data.DayRecordStore
import io.github.xvsu.daymark.ui.DaymarkTheme
import io.github.xvsu.daymark.ui.cardColors
import java.time.LocalDate

class WidgetConfigureActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var records by mutableStateOf(emptyList<DayRecord>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        // This activity is exported for launchers; reject IDs owned by other apps or no widget.
        if (!DayWidgetUpdates.isOurWidget(this, widgetId)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            DaymarkTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("桌面小组件", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { finish() }) { Text("取消") }
                        }
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                            Text(
                                "把在意的日子\n放在桌面上",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "选择一张卡片。每个小组件都可以记录不同的日子。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (records.isEmpty()) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(88.dp),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text("日", style = MaterialTheme.typography.headlineLarge)
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                Text("还没有日子卡片", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "先创建一个日子，再返回这里选择。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = ::createRecord) { Text("创建第一个日子") }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(records, key = { it.id }) { record ->
                                    val colors = cardColors(record.tone)
                                    val today = LocalDate.now()
                                    Card(
                                        onClick = { selectRecord(record.id) },
                                        shape = RoundedCornerShape(28.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = colors.container,
                                            contentColor = colors.content,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                record.headline(today),
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center,
                                            )
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    record.dayCount(today).toString(),
                                                    fontSize = 48.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    lineHeight = 56.sp,
                                                )
                                                Text(" 天", modifier = Modifier.padding(bottom = 9.dp))
                                            }
                                            Text("始于 ${record.startDate}", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                item {
                                    TextButton(onClick = ::createRecord, modifier = Modifier.fillMaxWidth()) {
                                        Text("新建日子")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        records = DayRecordStore(this).getAll()
    }

    private fun createRecord() {
        startActivity(Intent(this, MainActivity::class.java).putExtra("create_record", true))
    }

    private fun selectRecord(recordId: String) {
        if (!DayWidgetUpdates.isOurWidget(this, widgetId)) {
            finish()
            return
        }
        // The list may have changed while this activity was in the background.
        if (DayRecordStore(this).find(recordId) == null) {
            records = DayRecordStore(this).getAll()
            return
        }
        WidgetBindings(this).bind(widgetId, recordId)
        DayWidgetUpdates.update(this, widgetId)
        DayWidgetUpdates.scheduleNextRefresh(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
