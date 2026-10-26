package io.github.xvsu.daymark

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.data.DayRecordStore
import io.github.xvsu.daymark.data.PoetryViewModel
import io.github.xvsu.daymark.ui.DaymarkApp
import io.github.xvsu.daymark.ui.DaymarkTheme
import io.github.xvsu.daymark.widget.DayWidgetUpdates
import io.github.xvsu.daymark.widget.SquareDayWidgetProvider
import io.github.xvsu.daymark.widget.WideDayWidgetProvider
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private lateinit var store: DayRecordStore
    private var records by mutableStateOf<List<DayRecord>>(emptyList())
    private var today by mutableStateOf(LocalDate.now())
    private var intentRevision by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    private val midnightRefresh = Runnable { refreshCalendar() }
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshCalendar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = DayRecordStore(this)
        records = store.getAll()
        val poetry = ViewModelProvider(this)[PoetryViewModel::class.java]
        setContent {
            DaymarkTheme {
                var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
                key(intentRevision) {
                    DaymarkApp(
                        records = records,
                        today = today,
                        onSave = { record ->
                            store.save(record)
                            records = store.getAll()
                            DayWidgetUpdates.updateAll(this)
                        },
                        onDelete = { record ->
                            store.delete(record.id)
                            records = store.getAll()
                            DayWidgetUpdates.updateAll(this)
                        },
                        onAddWidget = { showWidgetPicker = true },
                        initialRecordId = intent.getStringExtra("record_id"),
                        initialCreateRecord = intent.getBooleanExtra("create_record", false),
                        poetryState = poetry.state,
                        onRefreshPoem = poetry::refresh,
                    )
                }
                if (showWidgetPicker) {
                    AlertDialog(
                        onDismissRequest = { showWidgetPicker = false },
                        title = { Text("把日子放在桌面") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("选择组件尺寸（宽 × 高），添加后点按组件选择事项。也可以长按桌面，在小组件列表中找到「记日」。")
                                Button(
                                    onClick = {
                                        showWidgetPicker = false
                                        pinWidget(SquareDayWidgetProvider::class.java)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("2 × 2 · 方形卡片") }
                                Button(
                                    onClick = {
                                        showWidgetPicker = false
                                        pinWidget(WideDayWidgetProvider::class.java)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("3 × 2 · 横向卡片") }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showWidgetPicker = false }) { Text("完成") }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            dateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refreshCalendar()
    }

    override fun onResume() {
        super.onResume()
        records = store.getAll()
    }

    override fun onStop() {
        handler.removeCallbacks(midnightRefresh)
        unregisterReceiver(dateReceiver)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentRevision++
    }

    private fun refreshCalendar() {
        today = LocalDate.now()
        DayWidgetUpdates.updateAll(this)
        handler.removeCallbacks(midnightRefresh)
        val nextMidnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        handler.postDelayed(midnightRefresh, nextMidnight.toEpochMilli() - System.currentTimeMillis())
    }

    private fun pinWidget(provider: Class<*>) {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported ||
            !manager.requestPinAppWidget(ComponentName(this, provider), null, null)
        ) {
            Toast.makeText(this, "请长按桌面 → 小组件 → 记日，选择所需尺寸", Toast.LENGTH_LONG).show()
        }
    }
}
