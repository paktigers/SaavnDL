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
            if (durationSec > 0) {
                val url = "$BASE/get?artist_name=${Uri.encode(firstArtist)}&track_name=${Uri.encode(cleanTitle)}" +
                    "&album_name=${Uri.encode(album)}&duration=$durationSec"
                val (code, body) = httpGetText(url)
                if (code == 200) parse(JSONObject(body))?.let { return@withContext it }
            }
            val sUrl = "$BASE/search?track_name=${Uri.encode(cleanTitle)}&artist_name=${Uri.encode(firstArtist)}"
            val (code2, body2) = httpGetText(sUrl)
            if (code2 == 200) {
                val arr = JSONArray(body2)
                for (i in 0 until arr.length()) {
                    parse(arr.getJSONObject(i))?.let { return@withContext it }
                }
            }
            null
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
