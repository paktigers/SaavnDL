package com.saavndl.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.saavndl.app.SaavnApp
import com.saavndl.app.download.Downloads
import com.saavndl.app.download.MediaSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DEFAULT_BASE_URL = "https://jiosaavn-api.tigerspak2.workers.dev"
const val OLD_BASE_URL = "https://saavn.sumit.co"
const val DEFAULT_REPO = "paktigers/SaavnDL"

/** Accepts "owner/name" or a full GitHub URL (e.g. https://github.com/owner/name/releases). */
fun normalizeRepo(input: String): String {
    val m = Regex("github\\.com/([^/\\s]+)/([^/\\s#?]+)").find(input)
    if (m != null) return m.groupValues[1] + "/" + m.groupValues[2].removeSuffix(".git")
    return input.trim().trim('/')
}

enum class AudioFormat(val label: String, val desc: String) {
    ORIGINAL("Original M4A", "Exactly as delivered. No tags, no conversion."),
    M4A_TAGS("M4A + metadata", "Same audio quality, with full tags and cover art."),
    MP3("MP3 + metadata", "Converted to MP3, with full tags and cover art.")
}

data class AppSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val quality: String = "320kbps",
    val format: AudioFormat = AudioFormat.M4A_TAGS,
    val saveCover: Boolean = false,
    val limit: Int = 20,
    val theme: String = "system",
    val concurrent: Int = 2,
    val nameStyle: Int = 0,
    val skipExisting: Boolean = true,
    val wifiOnly: Boolean = false,
    val retries: Int = 2,
    val embedLyrics: Boolean = false,
    val autoFavDownload: Boolean = false,
    val lang: String = "en",
    val repo: String = DEFAULT_REPO,
    val autoUpdate: Boolean = true,
    val homeLang: String = "hindi"
)

object SettingsStore {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = AppSettings()
        _state.value = AppSettings(
            baseUrl = (sp.getString("baseUrl", null) ?: d.baseUrl).let { if (it.isBlank() || it.trimEnd('/') == OLD_BASE_URL) d.baseUrl else it },
            quality = sp.getString("quality", d.quality) ?: d.quality,
            format = runCatching { AudioFormat.valueOf(sp.getString("format", d.format.name) ?: d.format.name) }
                .getOrDefault(d.format),
            saveCover = sp.getBoolean("saveCover", d.saveCover),
            limit = sp.getInt("limit", d.limit),
            theme = sp.getString("theme", d.theme) ?: d.theme,
            concurrent = sp.getInt("concurrent", d.concurrent),
            nameStyle = sp.getInt("nameStyle", d.nameStyle),
            skipExisting = sp.getBoolean("skipExisting", d.skipExisting),
            wifiOnly = sp.getBoolean("wifiOnly", d.wifiOnly),
            retries = sp.getInt("retries", d.retries),
            embedLyrics = sp.getBoolean("embedLyrics", d.embedLyrics),
            autoFavDownload = sp.getBoolean("autoFavDownload", d.autoFavDownload),
            lang = sp.getString("lang", d.lang) ?: d.lang,
            repo = (sp.getString("repo", null) ?: d.repo).let { if (it.isBlank()) d.repo else normalizeRepo(it) },
            autoUpdate = sp.getBoolean("autoUpdate", d.autoUpdate),
            homeLang = sp.getString("homeLang", d.homeLang) ?: d.homeLang
        )
        I18n.lang = _state.value.lang
    }

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_state.value)
        _state.value = n
        I18n.lang = n.lang
        sp.edit()
            .putString("baseUrl", n.baseUrl).putString("quality", n.quality)
            .putString("format", n.format.name).putBoolean("saveCover", n.saveCover)
            .putInt("limit", n.limit).putString("theme", n.theme)
            .putInt("concurrent", n.concurrent).putInt("nameStyle", n.nameStyle)
            .putBoolean("skipExisting", n.skipExisting).putBoolean("wifiOnly", n.wifiOnly)
            .putInt("retries", n.retries).putBoolean("embedLyrics", n.embedLyrics)
            .putBoolean("autoFavDownload", n.autoFavDownload).putString("lang", n.lang)
            .putString("repo", n.repo).putBoolean("autoUpdate", n.autoUpdate)
            .putString("homeLang", n.homeLang).apply()
    }

    fun restoreAsked(): Boolean = sp.getBoolean("restoreAsked", false)
    fun markRestoreAsked() { sp.edit().putBoolean("restoreAsked", true).apply() }

    fun lastUpdateCheck(): Long = sp.getLong("lastUpdateCheck", 0L)
    fun markUpdateChecked() { sp.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply() }
}

object Recents {
    private lateinit var sp: SharedPreferences
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("recents", Context.MODE_PRIVATE)
        _items.value = (sp.getString("list", "") ?: "").split("\n").filter { it.isNotBlank() }
    }

    fun add(q: String) {
        val l = (listOf(q) + _items.value.filter { !it.equals(q, true) }).take(12)
        _items.value = l
        sp.edit().putString("list", l.joinToString("\n")).apply()
    }

    fun clear() {
        _items.value = emptyList()
        sp.edit().remove("list").apply()
    }
}

data class LibItem(
    val songId: String, val title: String, val artist: String, val album: String,
    val uri: String, val format: String, val thumb: String, val bytes: Long, val time: Long,
    val image: String = "", val year: String = "", val label: String = "", val copyright: String = "",
    val url: String = "", val language: String = "", val albumArtist: String = "",
    val track: Int = 0, val total: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("songId", songId); put("title", title); put("artist", artist); put("album", album)
        put("uri", uri); put("format", format); put("thumb", thumb); put("bytes", bytes); put("time", time)
        put("image", image); put("year", year); put("label", label); put("copyright", copyright)
        put("url", url); put("language", language); put("albumArtist", albumArtist)
        put("track", track); put("total", total)
    }

    companion object {
        fun fromJson(o: JSONObject) = LibItem(
            o.optString("songId"), o.optString("title"), o.optString("artist"), o.optString("album"),
            o.optString("uri"), o.optString("format"), o.optString("thumb"), o.optLong("bytes"), o.optLong("time"),
            o.optString("image"), o.optString("year"), o.optString("label"), o.optString("copyright"),
            o.optString("url"), o.optString("language"), o.optString("albumArtist"),
            o.optInt("track"), o.optInt("total")
        )
    }
}

data class UserPlaylist(val id: String, val name: String, val songs: List<Song>, val autoDownload: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("autoDownload", autoDownload)
        val a = JSONArray(); songs.forEach { a.put(it.toJson()) }; put("songs", a)
    }

    companion object {
        fun fromJson(o: JSONObject): UserPlaylist {
            val a = o.optJSONArray("songs") ?: JSONArray()
            return UserPlaylist(o.optString("id"), o.optString("name"), (0 until a.length()).map { Song.fromJson(a.getJSONObject(it)) }, o.optBoolean("autoDownload", false))
        }
    }
}

class JsonListStore<T>(
    private val fileName: String,
    private val enc: (T) -> JSONObject,
    private val dec: (JSONObject) -> T
) {
    private lateinit var file: File
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items

    fun init(ctx: Context) {
        file = File(ctx.filesDir, fileName)
        runCatching {
            val a = JSONArray(file.readText())
            _items.value = (0 until a.length()).map { dec(a.getJSONObject(it)) }
        }
    }

    @Synchronized
    fun update(f: (List<T>) -> List<T>) {
        _items.value = f(_items.value)
        runCatching {
            val a = JSONArray()
            _items.value.forEach { a.put(enc(it)) }
            file.writeText(a.toString())
        }
    }
}

object Stores {
    val library = JsonListStore("library.json", { it: LibItem -> it.toJson() }, { LibItem.fromJson(it) })
    val favorites = JsonListStore("favorites.json", { it: Song -> it.toJson() }, { Song.fromJson(it) })
    val playlists = JsonListStore("playlists.json", { it: UserPlaylist -> it.toJson() }, { UserPlaylist.fromJson(it) })

    fun init(ctx: Context) {
        library.init(ctx)
        favorites.init(ctx)
        playlists.init(ctx)
    }

    fun toggleFavorite(s: Song) {
        val adding = favorites.items.value.none { it.id == s.id }
        favorites.update { l -> if (adding) listOf(s) + l else l.filter { it.id != s.id } }
        if (adding && SettingsStore.state.value.autoFavDownload) {
            Downloads.enqueue(SaavnApp.instance, listOf(s))
        }
    }

    fun createPlaylist(name: String, first: List<Song> = emptyList()): String {
        val id = "pl_" + System.currentTimeMillis()
        playlists.update { listOf(UserPlaylist(id, name.trim().ifBlank { "New playlist" }, first)) + it }
        return id
    }

    /** Adds songs to a playlist. Songs that are already in it are skipped. If the playlist is a "downloaded" playlist
     *  (download button was used, or every song is already downloaded) the new songs are downloaded automatically. */
    fun addToPlaylist(id: String, songs: List<Song>): AddOutcome {
        val pl = playlists.items.value.firstOrNull { it.id == id } ?: return AddOutcome(0, 0, false)
        val unique = songs.distinctBy { it.id }
        val have = pl.songs.map { it.id }.toSet()
        val fresh = unique.filter { it.id !in have }
        val skipped = unique.size - fresh.size
        if (fresh.isNotEmpty()) playlists.update { l -> l.map { if (it.id == id) it.copy(songs = it.songs + fresh) else it } }
        val libIds = library.items.value.map { it.songId }.toSet()
        val allDownloaded = pl.songs.isNotEmpty() && pl.songs.all { it.id in libIds }
        val auto = fresh.isNotEmpty() && (pl.autoDownload || allDownloaded)
        if (auto) {
            val todo = fresh.filter { it.id !in libIds }
            if (todo.isNotEmpty()) {
                val positions = todo.map { s -> pl.songs.size + fresh.indexOfFirst { it.id == s.id } + 1 }
                Downloads.enqueue(SaavnApp.instance, todo, FileNames.clean(pl.name), positions, pl.songs.size + fresh.size)
            }
        }
        return AddOutcome(fresh.size, skipped, auto)
    }

    fun setPlaylistAutoDownload(id: String, value: Boolean) =
        playlists.update { l -> l.map { if (it.id == id) it.copy(autoDownload = value) else it } }
}

data class AddOutcome(val added: Int, val skipped: Int, val autoDownload: Boolean)

/** JSON backup of settings, favorites, playlists and recent searches. */
object Backup {
    fun export(ctx: Context): String {
        val s = SettingsStore.state.value
        val root = JSONObject()
        root.put("app", "SaavnDL"); root.put("version", 1)
        root.put("settings", JSONObject().apply {
            put("baseUrl", s.baseUrl); put("quality", s.quality); put("format", s.format.name)
            put("saveCover", s.saveCover); put("limit", s.limit); put("theme", s.theme)
            put("concurrent", s.concurrent); put("nameStyle", s.nameStyle); put("skipExisting", s.skipExisting)
            put("wifiOnly", s.wifiOnly); put("retries", s.retries); put("embedLyrics", s.embedLyrics)
            put("autoFavDownload", s.autoFavDownload); put("lang", s.lang); put("repo", s.repo)
        })
        root.put("favorites", JSONArray().also { a -> Stores.favorites.items.value.forEach { a.put(it.toJson()) } })
        root.put("playlists", JSONArray().also { a -> Stores.playlists.items.value.forEach { a.put(it.toJson()) } })
        root.put("recents", JSONArray().also { a -> Recents.items.value.forEach { a.put(it) } })
        val name = "saavndl-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json"
        MediaSaver.saveToDownloads(ctx, name, "application/json", root.toString(2).toByteArray(Charsets.UTF_8))
        return name
    }

    fun restore(ctx: Context, uri: Uri): String {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Cannot read the file")
        val root = JSONObject(text)
        require(root.optString("app") == "SaavnDL") { "This is not a SaavnDL backup" }
        root.optJSONObject("settings")?.let { o ->
            val d = SettingsStore.state.value
            SettingsStore.update {
                it.copy(
                    baseUrl = o.optString("baseUrl", d.baseUrl), quality = o.optString("quality", d.quality),
                    format = runCatching { AudioFormat.valueOf(o.optString("format")) }.getOrDefault(d.format),
                    saveCover = o.optBoolean("saveCover", d.saveCover), limit = o.optInt("limit", d.limit),
                    theme = o.optString("theme", d.theme), concurrent = o.optInt("concurrent", d.concurrent),
                    nameStyle = o.optInt("nameStyle", d.nameStyle), skipExisting = o.optBoolean("skipExisting", d.skipExisting),
                    wifiOnly = o.optBoolean("wifiOnly", d.wifiOnly), retries = o.optInt("retries", d.retries),
                    embedLyrics = o.optBoolean("embedLyrics", d.embedLyrics),
                    autoFavDownload = o.optBoolean("autoFavDownload", d.autoFavDownload),
                    lang = o.optString("lang", d.lang), repo = o.optString("repo", d.repo)
                )
            }
        }
        val fav = root.optJSONArray("favorites") ?: JSONArray()
        val pls = root.optJSONArray("playlists") ?: JSONArray()
        val favs = (0 until fav.length()).map { Song.fromJson(fav.getJSONObject(it)) }
        val lists = (0 until pls.length()).map { UserPlaylist.fromJson(pls.getJSONObject(it)) }
        Stores.favorites.update { cur -> favs + cur.filter { c -> favs.none { it.id == c.id } } }
        Stores.playlists.update { cur -> lists + cur.filter { c -> lists.none { it.id == c.id } } }
        root.optJSONArray("recents")?.let { a -> for (i in a.length() - 1 downTo 0) Recents.add(a.getString(i)) }
        return "Restored ${favs.size} favorites and ${lists.size} playlists"
    }
}
