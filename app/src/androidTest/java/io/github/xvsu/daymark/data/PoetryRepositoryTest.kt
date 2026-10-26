package io.github.xvsu.daymark.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Uses Android's real JSON implementation; no request to an external service is needed. */
@RunWith(AndroidJUnit4::class)
class PoetryRepositoryTest {
    @Test
    fun parsesVerseAndItsOriginWhileTrimmingSurroundingWhitespace() {
        val poem = PoetryRepository.parsePoem(validData())

        assertEquals("行到水穷处，坐看云起时。", poem.content)
        assertEquals("终南别业", poem.title)
        assertEquals("王维", poem.author)
        assertEquals("唐代", poem.dynasty)
    }

    @Test
    fun rejectsEmptyOrWhitespaceOnlyVerse() {
        listOf("", " ", "\t\r\n", "　").forEach { verse ->
            assertRejected(validData().put("content", verse))
        }
    }

    @Test
    fun rejectsMissingOrNonStringVerseInsteadOfDisplayingCoercedJson() {
        assertRejected(validData().apply { remove("content") })
        listOf(JSONObject.NULL, 42, true, JSONArray(), JSONObject()).forEach { invalidVerse ->
            assertRejected(validData().put("content", invalidVerse))
        }
    }

    @Test
    fun rejectsMissingOrMalformedOrigin() {
        assertRejected(validData().apply { remove("origin") })
        listOf(JSONObject.NULL, "王维", JSONArray()).forEach { invalidOrigin ->
            assertRejected(validData().put("origin", invalidOrigin))
        }
    }

    @Test
    fun rejectsMissingOrNonStringOriginMetadata() {
        listOf("title", "author", "dynasty").forEach { field ->
            assertRejected(validData().apply { getJSONObject("origin").remove(field) })
            listOf(JSONObject.NULL, 42, JSONArray()).forEach { invalidValue ->
                assertRejected(validData().apply { getJSONObject("origin").put(field, invalidValue) })
            }
        }
    }

    private fun validData() = JSONObject().apply {
        put("id", "fixture-poem")
        put("content", " \n行到水穷处，坐看云起时。\t ")
        put("origin", JSONObject().apply {
            put("title", " 终南别业 ")
            put("author", " 王维 ")
            put("dynasty", " 唐代 ")
        })
        put("popularity", 999)
    }

    private fun assertRejected(data: JSONObject) {
        try {
            PoetryRepository.parsePoem(data)
        } catch (_: IOException) {
            return
        } catch (_: JSONException) {
            return
        }
        fail("Malformed poetry must be rejected: $data")
    }
}
