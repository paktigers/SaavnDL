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

    private fun artistList(s: String): List<String> =
        s.split(",", "&", "•", ";", " x ", " feat. ", " ft. ", " with ").map { it.trim() }.filter { it.isNotBlank() }

    private fun titleScore(a: String, b: String): Int = when {
        a.isEmpty() || b.isEmpty() -> 0
        a == b -> 4
        a.startsWith(b) || b.startsWith(a) -> 3
        a.contains(b) || b.contains(a) -> 2
        else -> 0
    }

    /** true when at least one Spotify artist is also an artist of the JioSaavn song. */
    private fun artistMatches(t: SpTrack, s: Song): Boolean {
        val want = artistList(t.artist).map { norm(it) }.filter { it.length >= 3 }
        if (want.isEmpty()) return true // Spotify gave no artist, nothing to verify
        val have = artistList(s.artists).map { norm(it) }.filter { it.length >= 3 }
        val haveAll = norm(s.artists)
        return want.any { w -> haveAll.contains(w) || have.any { h -> h.contains(w) || w.contains(h) } }
    }

    private val VERSION_WORDS = listOf("karaoke", "instrumental", "cover", "lofi", "slowed", "reverb", "8d", "remix", "mashup", "unplugged", "acoustic", "sped", "reprise")

    private class Cand(val song: Song, val total: Int)

    /** Accepts a JioSaavn song only if the title AND the singer agree (or the title is identical and the length is almost the same). */
    private fun evaluate(t: SpTrack, s: Song): Cand? {
        val ts = titleScore(norm(cleanTitle(t.title)), norm(cleanTitle(s.name)))
        if (ts < 2) return null
        val am = artistMatches(t, s)
        val diff = if (t.durationMs > 0 && s.duration > 0) abs(t.durationMs / 1000 - s.duration) else -1L
        if (!am && !(ts == 4 && diff in 0L..2L)) return null
        var total = ts * 2 + (if (am) 8 else 0)
        total += when {
            diff in 0L..3L -> 4
            diff in 4L..8L -> 1
            diff > 20 -> -3
            else -> 0
        }
        val want = (t.title).lowercase()
        val have = (s.name + " " + s.album).lowercase()
        for (w in VERSION_WORDS) if (have.contains(w) && !want.contains(w)) total -= 4
        return Cand(s, total)
    }

    suspend fun match(t: SpTrack): Song? {
        val artists = artistList(t.artist)
        val first = artists.firstOrNull().orEmpty()
        val title = cleanTitle(t.title)
        val queries = listOf(
            "$title $first".trim(),
            "$first $title".trim(),
            "$title ${artists.joinToString(" ")}".trim(),
            title
        ).distinct()
        val seen = HashMap<String, Cand>()
        for (q in queries) {
            val res = try { SaavnApi.searchSongs(q, 0, 10).second } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            for (song in res) {
                if (seen.containsKey(song.id)) continue
                evaluate(t, song)?.let { seen[song.id] = it }
            }
            // stop searching as soon as we have a strong match (same title + same singer)
            if (seen.values.any { it.total >= 16 }) break
        }
        return seen.values.maxByOrNull { it.total }?.song
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
