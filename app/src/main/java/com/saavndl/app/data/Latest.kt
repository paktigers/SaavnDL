package com.saavndl.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Card(val id: String, val kind: Kind, val name: String, val subtitle: String, val image: String, val link: String)
data class Section(val title: String, val cards: List<Card>)

/** "Latest releases" for the Discover screen: JioSaavn's launch data, with a search-based fallback. */
object LatestApi {
    private const val LAUNCH =
        "https://www.jiosaavn.com/api.php?__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=web6dot0"

    suspend fun load(lang: String): List<Section> {
        val direct = try { fromLaunchData(lang) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        if (direct.isNotEmpty()) return direct
        return fromSearch(lang)
    }

    private suspend fun fromLaunchData(lang: String): List<Section> = withContext(Dispatchers.IO) {
        val (code, body) = httpGetText(
            LAUNCH, 15000,
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
                "Referer" to "https://www.jiosaavn.com/",
                "Cookie" to "L=$lang; gdpr_acceptance=true"
            )
        )
        if (code !in 200..299) return@withContext emptyList()
        val root = JSONObject(body)
        val sections = ArrayList<Section>()
        fun add(key: String, title: String) {
            val arr = root.optJSONArray(key) ?: return
            val cards = cardsFrom(arr)
            if (cards.isNotEmpty()) sections.add(Section(title, cards))
        }
        add("new_albums", "New albums")
        add("new_trending", "Trending now")
        add("top_playlists", "Featured playlists")
        add("charts", "Charts")
        sections
    }

    private fun cardsFrom(arr: JSONArray): List<Card> {
        val out = ArrayList<Card>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kind = when (o.str("type")) {
                "song" -> Kind.SONGS
                "album" -> Kind.ALBUMS
                "playlist" -> Kind.PLAYLISTS
                else -> continue
            }
            val id = o.str("id")
            if (id.isBlank()) continue
            val img = o.str("image").replace("150x150", "500x500").replace("50x50", "500x500")
            val title = o.str("title").ifBlank { o.str("name") }.dec()
            if (title.isBlank()) continue
            out.add(Card(id, kind, title, o.str("subtitle").dec(), img, o.str("perma_url")))
        }
        return out
    }

    private suspend fun fromSearch(lang: String): List<Section> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val l = lang.replaceFirstChar { it.uppercase() }
        val sections = ArrayList<Section>()
        suspend fun colls(kind: Kind, q: String, title: String) {
            try {
                val cards = SaavnApi.searchColl(kind, q, 15).map { Card(it.id, it.kind, it.name, it.subtitle, it.image, "") }
                if (cards.isNotEmpty()) sections.add(Section(title, cards))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        colls(Kind.ALBUMS, "$l new releases $year", "New albums")
        colls(Kind.PLAYLISTS, "$l latest songs", "Latest playlists")
        colls(Kind.PLAYLISTS, "$l trending", "Trending playlists")
        return sections
    }
}
