package com.saavndl.app.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object SaavnApi {
    const val UA = "Mozilla/5.0 (Linux; Android 14) SaavnDL/1.0"

    private fun enc(s: String) = Uri.encode(s)

    private suspend fun get(path: String): Any = withContext(Dispatchers.IO) {
        val base = SettingsStore.state.value.baseUrl.trim().trimEnd('/')
        var last: Exception? = null
        for (attempt in 0 until 3) {
            try {
                val c = URL(base + path).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", "application/json")
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()
                if (code !in 200..299) throw IOException("Server returned HTTP $code")
                val root = JSONObject(text)
                if (!root.optBoolean("success", true)) throw IOException(root.optString("message", "API error"))
                return@withContext root.opt("data") ?: JSONObject()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (attempt < 2) delay(1200)
            }
        }
        throw last ?: IOException("Unknown error")
    }

    private fun Any.asSongs(): List<Song> = when (this) {
        is JSONArray -> this.toSongs()
        is JSONObject -> (optJSONArray("songs") ?: optJSONArray("results")).toSongs()
        else -> emptyList()
    }

    suspend fun ping(): Boolean {
        get("/api/search/songs?query=test&limit=1")
        return true
    }

    suspend fun searchSongs(q: String, page: Int, limit: Int): Pair<Int, List<Song>> {
        val d = get("/api/search/songs?query=${enc(q)}&page=$page&limit=$limit") as JSONObject
        return d.optInt("total") to d.optJSONArray("results").toSongs()
    }

    suspend fun searchColl(kind: Kind, q: String, limit: Int): List<Coll> {
        val seg = when (kind) {
            Kind.ALBUMS -> "albums"; Kind.PLAYLISTS -> "playlists"; Kind.ARTISTS -> "artists"; else -> "songs"
        }
        val d = get("/api/search/$seg?query=${enc(q)}&limit=$limit") as JSONObject
        val a = d.optJSONArray("results") ?: return emptyList()
        val l = ArrayList<Coll>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { l.add(collFromApi(kind, it)) }
        return l
    }

    suspend fun songById(id: String): Song =
        get("/api/songs/${enc(id)}").asSongs().firstOrNull() ?: throw IOException("Song not found")

    suspend fun similar(id: String): List<Song> = get("/api/songs/${enc(id)}/suggestions?limit=25").asSongs()

    suspend fun artistSongs(id: String, page: Int): List<Song> {
        val d = get("/api/artists/${enc(id)}/songs?page=$page&sortBy=popularity&sortOrder=desc")
        return d.asSongs()
    }

    suspend fun loadDetail(kind: Kind, id: String?, link: String?): Detail {
        val q = if (id != null) "id=${enc(id)}" else "link=${enc(link ?: "")}"
        return when (kind) {
            Kind.ALBUMS -> collection(kind, get("/api/albums?$q") as JSONObject)
            Kind.PLAYLISTS -> collection(kind, get("/api/playlists?$q&limit=200") as JSONObject)
            Kind.ARTISTS -> {
                val d = get("/api/artists?$q") as JSONObject
                val (_, big) = d.optJSONArray("image").images()
                val albums = ArrayList<Coll>()
                val ta = d.optJSONArray("topAlbums")
                if (ta != null) for (i in 0 until ta.length()) ta.optJSONObject(i)?.let { albums.add(collFromApi(Kind.ALBUMS, it)) }
                val fc = d.str("followerCount")
                Detail(
                    d.str("id"), kind, d.str("name").dec(),
                    if (fc.isNotBlank()) "${compactNum(fc)} followers" else "",
                    big, d.optJSONArray("topSongs").toSongs(), albums
                )
            }
            Kind.SONGS -> {
                val path = if (id != null) "/api/songs/${enc(id)}" else "/api/songs?link=${enc(link ?: "")}"
                val songs = get(path).asSongs()
                val s = songs.firstOrNull() ?: throw IOException("Song not found")
                Detail(s.id, kind, s.name, s.artists, s.image, songs)
            }
        }
    }

    private fun collection(kind: Kind, d: JSONObject): Detail {
        val (_, big) = d.optJSONArray("image").images()
        val names = d.optJSONObject("artists")?.optJSONArray("primary").names()
        val parts = listOf(
            names.joinToString(", "),
            d.str("year"),
            d.str("songCount").let { if (it.isNotBlank()) "$it songs" else "" }
        ).filter { it.isNotBlank() }
        return Detail(d.str("id"), kind, d.str("name").dec(), parts.joinToString(" · "), big, d.optJSONArray("songs").toSongs())
    }
}
