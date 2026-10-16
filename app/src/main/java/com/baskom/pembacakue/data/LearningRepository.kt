package com.baskom.pembacakue.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class LearningTopic(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val targetHtml: String
)

/**
 * Daftar materi edukasi dari Firebase Hosting.
 *
 * Dulu daftar ini diunduh ulang setiap kali layar tempel kartu muncul — termasuk
 * setiap selesai scan — tanpa timeout (`URL.readText()` bisa menunggu bermenit-menit
 * di jaringan buruk, skeleton loading tidak pernah hilang) dan tanpa cache, jadi
 * saat offline bagian materi lenyap begitu saja.
 */
class LearningRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val BASE_URL = "https://pembacakue-mvp-2026.web.app"
        const val TOPICS_URL = "$BASE_URL/topics.json"
        private const val TAG = "LearningRepository"
        private const val PREFS = "pembacakue_learning"
        private const val KEY_JSON = "topics_json"
        private const val KEY_FETCHED_AT = "topics_fetched_at"
        private const val TIMEOUT_MS = 5_000
        private const val FRESH_MS = 6L * 60 * 60 * 1000

        @Volatile private var memory: List<LearningTopic>? = null

        internal fun parse(json: String): List<LearningTopic> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                try {
                    LearningTopic(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.getString("description"),
                        icon = obj.getString("icon"),
                        targetHtml = obj.getString("targetHtml")
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /** Salinan yang sudah ada tanpa jaringan — untuk tampil seketika. */
    fun cachedTopics(): List<LearningTopic> {
        memory?.let { return it }
        val json = prefs.getString(KEY_JSON, null) ?: return emptyList()
        return try { parse(json).also { memory = it } } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchTopics(forceRefresh: Boolean = false): List<LearningTopic> = withContext(Dispatchers.IO) {
        val cached = cachedTopics()
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_FETCHED_AT, 0L) < FRESH_MS
        if (!forceRefresh && cached.isNotEmpty() && fresh) return@withContext cached

        try {
            val conn = URL(TOPICS_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val response = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val topics = parse(response)
            if (topics.isNotEmpty()) {
                memory = topics
                prefs.edit()
                    .putString(KEY_JSON, response)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()
                return@withContext topics
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching topics: ${e.message}")
        }
        cached
    }
}
