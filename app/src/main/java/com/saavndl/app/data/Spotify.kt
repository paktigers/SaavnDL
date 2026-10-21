package com.saavndl.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

data class SpTrack(val title: String, val artist: String, val durationMs: Long)
data class SpList(val name: String, val type: String, val tracks: List<SpTrack>)
data class SpMatch(val track: SpTrack, val song: Song?)

/** Reads a public Spotify playlist/album from its embed page and matches every track on JioSaavn. */
object SpotifyApi {
    private val LINK = Regex("open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?(playlist|album)/([A-Za-z0-9]+)")
    private val URI = Regex("spotify:(playlist|album):([A-Za-z0-9]+)")

    fun parseLink(s: String): Pair<String, String>? {
        val m = LINK.find(s) ?: URI.find(s) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    fun isSpotifyLink(s: String) = parseLink(s) != null

    suspend fun load(type: String, id: String): SpList = withContext(Dispatchers.IO) {
        val (code, html) = httpGetText(
            "https://open.spotify.com/embed/$type/$id", 20000,
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml",
                "Accept-Language" to "en-US,en;q=0.9"
            )
        )
        if (code !in 200..299) throw IOException("Spotify returned HTTP $code. Make sure the $type is public.")
        val m = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: throw IOException("Could not read this Spotify $type. Make sure it is public.")
        val root = JSONObject(m.groupValues[1])
        val entity = root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")
            ?.optJSONObject("data")?.optJSONObject("entity")
        val list = entity?.optJSONArray("trackList") ?: findTrackArray(root)
            ?: throw IOException("No tracks found. The $type may be private or empty.")
        val name = entity?.let { it.str("name").ifBlank { it.str("title") } }.orEmpty().ifBlank { "Spotify $type" }
        val tracks = ArrayList<SpTrack>()
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i) ?: continue
            val title = t.str("title").ifBlank { t.str("name") }
            if (title.isBlank()) continue
            tracks.add(SpTrack(title, t.str("subtitle"), t.str("duration").toDoubleOrNull()?.toLong() ?: 0L))
        }
        if (tracks.isEmpty()) throw IOException("No tracks found in this $type.")
        SpList(name, type, tracks)
    }

    /** Fallback: finds the first array of objects that have both "title" and "subtitle". */
    private fun findTrackArray(node: Any?): JSONArray? {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) findTrackArray(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> {
                val first = node.optJSONObject(0)
                if (first != null && first.has("title") && first.has("subtitle")) return node
                for (i in 0 until node.length()) findTrackArray(node.opt(i))?.let { return it }
            }
        }
        return null
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun cleanTitle(t: String): String =
        t.replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "").replace(Regex("\\s+-\\s+.*$"), "").trim().ifBlank { t }

    private fun score(t: SpTrack, s: Song): Int {
        val a = norm(cleanTitle(t.title)); val b = norm(cleanTitle(s.name))
        var sc = when {
            a.isEmpty() || b.isEmpty() -> 0
            a == b -> 4
            b.contains(a) || a.contains(b) -> 2
            else -> 0
        }
        val artists = t.artist.split(",", "&", "•").map { norm(it) }.filter { it.isNotEmpty() }
        val songArtists = norm(s.artists)
        if (artists.any { songArtists.contains(it) }) sc += 2
        if (t.durationMs > 0 && s.duration > 0 && abs(t.durationMs / 1000 - s.duration) <= 6) sc += 1
        return sc
    }

    suspend fun match(t: SpTrack): Song? {
        val first = t.artist.split(",", "&", "•").firstOrNull()?.trim().orEmpty()
        val queries = listOf("${cleanTitle(t.title)} $first".trim(), cleanTitle(t.title))
        for (q in queries) {
            val res = try { SaavnApi.searchSongs(q, 0, 8).second } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            val best = res.map { it to score(t, it) }.filter { it.second >= 3 }.maxByOrNull { it.second }
            if (best != null) return best.first
        }
        return null
    }

    suspend fun matchAll(list: SpList, onProgress: (done: Int, total: Int) -> Unit): List<SpMatch> = coroutineScope {
        val done = AtomicInteger(0)
        val out = ArrayList<SpMatch>()
        for (chunk in list.tracks.chunked(3)) {
            val part = chunk.map { t ->
                async(Dispatchers.IO) {
                    val s = try { match(t) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                    onProgress(done.incrementAndGet(), list.tracks.size)
                    SpMatch(t, s)
                }
            }.awaitAll()
            out.addAll(part)
        }
        out
    }
}
