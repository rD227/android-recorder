package io.github.xvsu.daymark.data

import android.content.Context
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Uses the documented client token flow: https://www.jinrishici.com/doc/ */
class PoetryRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("poetry", Context.MODE_PRIVATE)

    fun cachedPoem(): Poem? {
        val cached = preferences.getString("poem", null) ?: return null
        return try {
            parsePoem(JSONObject(cached))
        } catch (_: JSONException) {
            discardCachedPoem()
        } catch (_: IOException) {
            discardCachedPoem()
        }
    }

    // The last verse is disposable: a malformed or older cache can be fetched again.
    private fun discardCachedPoem(): Poem? {
        preferences.edit().remove("poem").apply()
        return null
    }

    /** Blocking I/O; the caller owns the background executor. */
    fun fetchPoem(): Poem {
        val token = preferences.getString("token", null) ?: request("token")
            .let { requiredString(it, "data") }.also {
                if (it.isBlank()) throw IOException("Poetry API returned an empty token")
                preferences.edit().putString("token", it).apply()
            }
        val poem = parsePoem(request("sentence", token).getJSONObject("data"))
        val cached = JSONObject().apply {
            put("content", poem.content)
            put("origin", JSONObject().apply {
                put("title", poem.title)
                put("author", poem.author)
                put("dynasty", poem.dynasty)
            })
        }
        preferences.edit().putString("poem", cached.toString()).apply()
        return poem
    }

    private fun request(path: String, token: String? = null): JSONObject {
        val connection = URL("https://v2.jinrishici.com/$path").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("X-User-Token", token)
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                throw IOException("Poetry API HTTP ${connection.responseCode}")
            }
            val result = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                JSONObject(it.readText())
            }
            if (requiredString(result, "status") != "success") {
                throw IOException("Poetry API did not return a successful response")
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private fun requiredString(data: JSONObject, key: String): String =
            data.get(key) as? String ?: throw JSONException("Poetry field $key must be a string")

        internal fun parsePoem(data: JSONObject): Poem {
            val content = requiredString(data, "content").trim()
            if (content.isEmpty()) throw IOException("Poetry API returned an empty verse")
            val origin = data.getJSONObject("origin")
            return Poem(
                content = content,
                title = requiredString(origin, "title").trim(),
                author = requiredString(origin, "author").trim(),
                dynasty = requiredString(origin, "dynasty").trim(),
            )
        }
    }
}
