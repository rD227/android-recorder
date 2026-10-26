package io.github.xvsu.daymark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.github.xvsu.daymark.data.DayRecord
import io.github.xvsu.daymark.data.DayRecordStore
import io.github.xvsu.daymark.data.PoetryViewModel
import io.github.xvsu.daymark.ui.DaymarkApp
import io.github.xvsu.daymark.ui.DaymarkTheme
import io.github.xvsu.daymark.ui.WidgetPinDialog
import io.github.xvsu.daymark.widget.DayWidgetUpdates
import io.github.xvsu.daymark.widget.WidgetPinRequests
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
        val widgetPins = WidgetPinRequests(this)
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
                    WidgetPinDialog(
                        records = records,
                        requests = widgetPins,
                        onRequest = { recordId, size -> widgetPins.request(this, recordId, size) },
                        onDismiss = { showWidgetPicker = false },
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

}
