package io.github.xvsu.daymark.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One source of truth shared by the app and all home-screen widgets. */
class DayRecordStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("day_records", Context.MODE_PRIVATE)

    fun getAll(): List<DayRecord> {
        val entries = JSONArray(preferences.getString("records", "[]"))
        return List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            DayRecord(
                id = entry.getString("id"),
                title = entry.getString("title"),
                startDate = LocalDate.parse(entry.getString("startDate")),
                tone = CardTone.valueOf(entry.getString("tone")),
            )
        }
    }

    fun find(id: String): DayRecord? = getAll().find { it.id == id }

    fun save(record: DayRecord) {
        val records = getAll().toMutableList()
        val index = records.indexOfFirst { it.id == record.id }
        if (index == -1) records.add(record) else records[index] = record
        write(records)
    }

    fun delete(id: String) {
        write(getAll().filterNot { it.id == id })
    }

    private fun write(records: List<DayRecord>) {
        val entries = JSONArray()
        records.forEach { record ->
            entries.put(JSONObject().apply {
                put("id", record.id)
                put("title", record.title)
                put("startDate", record.startDate.toString())
                put("tone", record.tone.name)
            })
        }
        preferences.edit().putString("records", entries.toString()).apply()
    }
}
