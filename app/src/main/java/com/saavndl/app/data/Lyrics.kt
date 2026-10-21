package com.saavndl.app.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LrcLine(val ms: Long, val text: String)
data class Lyrics(val plain: String, val synced: List<LrcLine>, val instrumental: Boolean)

/** Lyrics from https://lrclib.net (free, no key). */
object LyricsApi {
    private const val BASE = "https://lrclib.net/api"

    suspend fun fetch(title: String, artist: String, album: String, durationSec: Int): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").trim().ifBlank { title }
            val firstArtist = artist.split(",", "&").firstOrNull()?.trim().orEmpty().ifBlank { artist }
            val records = ArrayList<JSONObject>()

            fun addAll(body: String) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { records.add(it) }
            }
            fun hasSynced(o: JSONObject) = !o.isNull("syncedLyrics") && o.optString("syncedLyrics").isNotBlank()
            fun closeEnough(o: JSONObject) = durationSec <= 0 || kotlin.math.abs(o.optDouble("duration", 0.0) - durationSec) <= 6

            if (durationSec > 0) {
                val url = "$BASE/get?artist_name=${Uri.encode(firstArtist)}&track_name=${Uri.encode(cleanTitle)}" +
                    "&album_name=${Uri.encode(album)}&duration=$durationSec"
                val (code, body) = httpGetText(url)
                if (code == 200) {
                    val o = JSONObject(body)
                    records.add(o)
                    if (hasSynced(o)) return@withContext parse(o)
                }
            }
            val searches = listOf(
                "$BASE/search?track_name=${Uri.encode(cleanTitle)}&artist_name=${Uri.encode(firstArtist)}",
                "$BASE/search?q=${Uri.encode("$cleanTitle $firstArtist")}",
                "$BASE/search?q=${Uri.encode(cleanTitle)}"
            )
            for (u in searches) {
                val (code, body) = httpGetText(u)
                if (code == 200) addAll(body)
                if (records.any { hasSynced(it) && closeEnough(it) }) break
            }
            val key = { s: String -> s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "") }
            val want = key(cleanTitle)
            val best = records.mapNotNull { o ->
                val hasAny = hasSynced(o) || (!o.isNull("plainLyrics") && o.optString("plainLyrics").isNotBlank())
                if (!hasAny) return@mapNotNull null
                var sc = if (hasSynced(o)) 10 else 3
                if (durationSec > 0) {
                    val diff = kotlin.math.abs(o.optDouble("duration", 0.0) - durationSec)
                    sc += when { diff <= 3 -> 5; diff <= 8 -> 3; diff <= 20 -> 1; else -> -3 }
                }
                val name = key(o.optString("trackName").replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), ""))
                if (name == want) sc += 3 else if (name.contains(want) || want.contains(name)) sc += 1
                o to sc
            }.maxByOrNull { it.second }?.first
            if (best != null) parse(best) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(o: JSONObject): Lyrics? {
        val plain = if (o.isNull("plainLyrics")) "" else o.optString("plainLyrics")
        val synced = if (o.isNull("syncedLyrics")) "" else o.optString("syncedLyrics")
        val instrumental = o.optBoolean("instrumental", false)
        if (plain.isBlank() && synced.isBlank() && !instrumental) return null
        val lines = parseLrc(synced)
        val text = plain.ifBlank { lines.joinToString("\n") { it.text } }
        return Lyrics(text, lines, instrumental)
    }

    fun parseLrc(s: String): List<LrcLine> {
        if (s.isBlank()) return emptyList()
        val rx = Regex("^\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s?(.*)$")
        val out = ArrayList<LrcLine>()
        for (line in s.lines()) {
            val m = rx.find(line.trim()) ?: continue
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toDouble()
            out.add(LrcLine(min * 60_000 + (sec * 1000).toLong(), m.groupValues[3]))
        }
        return out
    }
}
