package io.github.xvsu.daymark.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xvsu.daymark.ui.customCardColor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DayRecordStoreColorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("day_records", Context.MODE_PRIVATE)
    private var originalRecords: String? = null

    @Before fun rememberUserRecords() {
        originalRecords = preferences.getString("records", null)
    }

    @After fun restoreUserRecords() {
        if (originalRecords == null) preferences.edit().remove("records").commit()
        else preferences.edit().putString("records", originalRecords).commit()
    }

    @Test fun oldRecordWithoutColorKeepsItsPreset() {
        writeFixture(recordJson())
        val record = DayRecordStore(context).getAll().single()
        assertNull(record.customColor)
        assertEquals(CardTone.LAVENDER, record.tone)
    }

    @Test fun explicitNullAlsoMeansPreset() {
        writeFixture(recordJson().put("customColor", JSONObject.NULL))
        assertNull(DayRecordStore(context).getAll().single().customColor)
    }

    @Test fun customColorSurvivesSaveAndPresetSelectionClearsIt() {
        writeFixture(recordJson())
        val store = DayRecordStore(context)
        val record = DayRecord("fixture", "纪念日", LocalDate.of(2026, 10, 26), CardTone.LAVENDER, 0xFFFFD1E2.toInt())
        store.save(record)
        assertEquals(record, DayRecordStore(context).getAll().single())
        store.save(record.copy(customColor = null))
        assertNull(DayRecordStore(context).getAll().single().customColor)
    }

    @Test fun invalidStoredColorsAreRejectedInsteadOfCoerced() {
        listOf("-1", 1.5, true, 4294967295L, 0x00FFFFFF, JSONArray()).forEach { invalid ->
            writeFixture(recordJson().put("customColor", invalid))
            try {
                DayRecordStore(context).getAll()
                fail("Malformed custom color must be rejected: $invalid")
            } catch (_: IllegalArgumentException) {
                // Storage is internal state. Corruption must not silently change the user's card.
            }
        }
    }

    @Test fun addingWhiteCreatesPastelsAndDoesNotChangeTheChosenHue() {
        assertEquals(0xFFFF0000.toInt(), customCardColor(0f, 0f))
        assertEquals(0xFFFF8080.toInt(), customCardColor(0f, 0.5f))
        assertEquals(0xFFFFFFFF.toInt(), customCardColor(240f, 1f))
        assertEquals(0xFF8080FF.toInt(), customCardColor(240f, 0.5f))
    }

    private fun recordJson() = JSONObject().apply {
        put("id", "fixture")
        put("title", "纪念日")
        put("startDate", "2026-10-26")
        put("tone", "LAVENDER")
    }

    private fun writeFixture(record: JSONObject) {
        preferences.edit().putString("records", JSONArray().put(record).toString()).commit()
    }
}
