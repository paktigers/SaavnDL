package com.saavndl.app.data

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class Kind(val label: String) {
    SONGS("Songs"), ALBUMS("Albums"), PLAYLISTS("Playlists"), ARTISTS("Artists")
}

data class DlUrl(val quality: String, val url: String)

data class Song(
    val id: String,
    val name: String,
    val artists: String,
    val firstArtist: String,
    val album: String,
    val year: String,
    val duration: Int,
    val language: String,
    val label: String,
    val copyright: String,
    val playCount: Long,
    val url: String,
    val image: String,
    val thumb: String,
    val downloads: List<DlUrl>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("artists", artists); put("firstArtist", firstArtist)
        put("album", album); put("year", year); put("duration", duration); put("language", language)
        put("label", label); put("copyright", copyright); put("playCount", playCount); put("url", url)
        put("image", image); put("thumb", thumb)
        val a = JSONArray()
        downloads.forEach { a.put(JSONObject().put("q", it.quality).put("u", it.url)) }
        put("downloads", a)
    }

    companion object {
        fun fromJson(o: JSONObject): Song {
            val a = o.optJSONArray("downloads") ?: JSONArray()
            val dl = (0 until a.length()).map {
                val x = a.getJSONObject(it)
                DlUrl(x.optString("q"), x.optString("u"))
            }
            return Song(
                id = o.optString("id"), name = o.optString("name"), artists = o.optString("artists"),
                firstArtist = o.optString("firstArtist"), album = o.optString("album"),
                year = o.optString("year"), duration = o.optInt("duration"),
                language = o.optString("language"), label = o.optString("label"),
                copyright = o.optString("copyright"), playCount = o.optLong("playCount"),
                url = o.optString("url"), image = o.optString("image"), thumb = o.optString("thumb"),
                downloads = dl
            )
        }
    }
}

data class Coll(val id: String, val kind: Kind, val name: String, val subtitle: String, val image: String)

data class Detail(
    val id: String,
    val kind: Kind,
    val name: String,
    val subtitle: String,
    val image: String,
    val songs: List<Song>,
    val albums: List<Coll> = emptyList()
)

// ---------------------------------------------------------------- JSON helpers
fun JSONObject.str(key: String): String {
    val v = opt(key)
    return if (v == null || v == JSONObject.NULL) "" else v.toString()
}

fun String?.dec(): String =
    if (this.isNullOrEmpty()) "" else Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()

/** returns (thumb, big) image urls from the API "image" array. */
fun JSONArray?.images(): Pair<String, String> {
    if (this == null || length() == 0) return "" to ""
    val big = optJSONObject(length() - 1)?.str("url").orEmpty()
    val thumb = optJSONObject(minOf(1, length() - 1))?.str("url").orEmpty()
    return thumb to big
}

fun JSONArray?.names(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optJSONObject(it)?.str("name").dec() }.filter { it.isNotBlank() }
}

fun JSONArray?.toSongs(): List<Song> {
    if (this == null) return emptyList()
    val l = ArrayList<Song>()
    for (i in 0 until length()) optJSONObject(i)?.let { l.add(songFromApi(it)) }
    return l
}

fun songFromApi(o: JSONObject): Song {
    val names = o.optJSONObject("artists")?.optJSONArray("primary").names()
    val (thumb, big) = o.optJSONArray("image").images()
    val dlArr = o.optJSONArray("downloadUrl")
    val dl = ArrayList<DlUrl>()
    if (dlArr != null) for (i in 0 until dlArr.length()) {
        val x = dlArr.optJSONObject(i) ?: continue
        val u = x.str("url")
        if (u.isNotBlank()) dl.add(DlUrl(x.str("quality"), u))
    }
    return Song(
        id = o.str("id"),
        name = o.str("name").dec(),
        artists = names.joinToString(", "),
        firstArtist = names.firstOrNull().orEmpty(),
        album = o.optJSONObject("album")?.str("name").dec(),
        year = o.str("year"),
        duration = o.str("duration").toDoubleOrNull()?.toInt() ?: 0,
        language = o.str("language"),
        label = o.str("label").dec(),
        copyright = o.str("copyright").dec(),
        playCount = o.str("playCount").toDoubleOrNull()?.toLong() ?: 0L,
        url = o.str("url"),
        image = big,
        thumb = thumb.ifBlank { big },
        downloads = dl
    )
}

fun collFromApi(kind: Kind, o: JSONObject): Coll {
    val (_, big) = o.optJSONArray("image").images()
    val names = o.optJSONObject("artists")?.optJSONArray("primary").names()
    val year = o.str("year")
    val sub = when {
        names.isNotEmpty() -> names.joinToString(", ") + if (year.isNotBlank()) " · $year" else ""
        o.str("songCount").isNotBlank() -> "${o.str("songCount")} songs"
        o.str("role").isNotBlank() -> o.str("role").replaceFirstChar { it.uppercase() }
        year.isNotBlank() -> year
        else -> ""
    }
    return Coll(o.str("id"), kind, o.str("name").dec(), sub, big)
}

// ---------------------------------------------------------------- misc utils
fun fmtDur(sec: Int): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)

fun compactNum(s: String): String {
    val n = s.toDoubleOrNull() ?: return s
    return when {
        n >= 1e9 -> String.format(Locale.US, "%.1fB", n / 1e9)
        n >= 1e6 -> String.format(Locale.US, "%.1fM", n / 1e6)
        n >= 1e3 -> String.format(Locale.US, "%.1fK", n / 1e3)
        else -> String.format(Locale.US, "%.0f", n)
    }
}

fun fmtSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024) else String.format(Locale.US, "%.1f MB", mb)
}

fun linkKind(link: String): Kind? = when {
    "/song/" in link -> Kind.SONGS
    "/album/" in link -> Kind.ALBUMS
    "/featured/" in link || "/playlist/" in link -> Kind.PLAYLISTS
    "/artist/" in link -> Kind.ARTISTS
    else -> null
}

object FileNames {
    fun clean(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), " ").trim().take(120)

    fun style(style: Int, s: Song): String = when (style) {
        1 -> "${s.firstArtist.ifBlank { s.artists }} - ${s.name}"
        2 -> s.name
        else -> if (s.artists.isBlank()) s.name else "${s.name} - ${s.artists}"
    }
}
