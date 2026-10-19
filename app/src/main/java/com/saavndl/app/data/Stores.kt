package com.saavndl.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AudioFormat(val label: String, val desc: String) {
    ORIGINAL("Original M4A", "Exactly as delivered. No tags, no conversion."),
    M4A_TAGS("M4A + metadata", "Same audio quality, with full tags and cover art."),
    MP3("MP3 + metadata", "Converted to MP3, with full tags and cover art.")
}

data class AppSettings(
    val baseUrl: String = "https://saavn.sumit.co",
    val quality: String = "320kbps",
    val format: AudioFormat = AudioFormat.M4A_TAGS,
    val saveCover: Boolean = false,
    val limit: Int = 20,
    val theme: String = "system",
    val concurrent: Int = 2,
    val nameStyle: Int = 0,
    val skipExisting: Boolean = true
)

object SettingsStore {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val d = AppSettings()
        _state.value = AppSettings(
            baseUrl = sp.getString("baseUrl", d.baseUrl) ?: d.baseUrl,
            quality = sp.getString("quality", d.quality) ?: d.quality,
            format = runCatching { AudioFormat.valueOf(sp.getString("format", d.format.name) ?: d.format.name) }
                .getOrDefault(d.format),
            saveCover = sp.getBoolean("saveCover", d.saveCover),
            limit = sp.getInt("limit", d.limit),
            theme = sp.getString("theme", d.theme) ?: d.theme,
            concurrent = sp.getInt("concurrent", d.concurrent),
            nameStyle = sp.getInt("nameStyle", d.nameStyle),
            skipExisting = sp.getBoolean("skipExisting", d.skipExisting)
        )
    }

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_state.value)
        _state.value = n
        sp.edit()
            .putString("baseUrl", n.baseUrl).putString("quality", n.quality)
            .putString("format", n.format.name).putBoolean("saveCover", n.saveCover)
            .putInt("limit", n.limit).putString("theme", n.theme)
            .putInt("concurrent", n.concurrent).putInt("nameStyle", n.nameStyle)
            .putBoolean("skipExisting", n.skipExisting).apply()
    }
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
    val uri: String, val format: String, val thumb: String, val bytes: Long, val time: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("songId", songId); put("title", title); put("artist", artist); put("album", album)
        put("uri", uri); put("format", format); put("thumb", thumb); put("bytes", bytes); put("time", time)
    }

    companion object {
        fun fromJson(o: JSONObject) = LibItem(
            o.optString("songId"), o.optString("title"), o.optString("artist"), o.optString("album"),
            o.optString("uri"), o.optString("format"), o.optString("thumb"), o.optLong("bytes"), o.optLong("time")
        )
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

    fun init(ctx: Context) {
        library.init(ctx)
        favorites.init(ctx)
    }

    fun toggleFavorite(s: Song) = favorites.update { l ->
        if (l.any { it.id == s.id }) l.filter { it.id != s.id } else listOf(s) + l
    }
}
