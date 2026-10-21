package com.saavndl.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saavndl.app.data.Coll
import com.saavndl.app.data.Kind
import com.saavndl.app.data.LatestApi
import com.saavndl.app.data.Recents
import com.saavndl.app.data.SaavnApi
import com.saavndl.app.data.Section
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.Song
import com.saavndl.app.data.SpList
import com.saavndl.app.data.SpMatch
import com.saavndl.app.data.SpotifyApi
import com.saavndl.app.data.Stores
import com.saavndl.app.data.UpdateInfo
import com.saavndl.app.data.Updater
import com.saavndl.app.download.Downloads
import com.saavndl.app.download.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppVM : ViewModel() {
    var query by mutableStateOf("")
    var submitted by mutableStateOf("")
    var kind by mutableStateOf(Kind.SONGS)
    var songs by mutableStateOf<List<Song>>(emptyList())
    var colls by mutableStateOf<List<Coll>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    var total by mutableIntStateOf(0)
    var batchStatus by mutableStateOf("")
    var langFilter by mutableStateOf("All")
    var forYou by mutableStateOf<List<Song>>(emptyList())
    var forYouFrom by mutableStateOf("")
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateStatus by mutableStateOf("")
    var updateBusy by mutableStateOf(false)
    var updateProgress by mutableFloatStateOf(0f)
    var updateFile by mutableStateOf<File?>(null)
    var updateError by mutableStateOf("")
    private var updateJob: Job? = null

    // live suggestions while typing
    var sugSongs by mutableStateOf<List<Song>>(emptyList())
    var sugColls by mutableStateOf<List<Coll>>(emptyList())
    var sugLoading by mutableStateOf(false)

    // latest releases
    var latest by mutableStateOf<List<Section>>(emptyList())
    var latestLoading by mutableStateOf(false)
    var latestError by mutableStateOf(false)

    // spotify import
    var spBusy by mutableStateOf(false)
    var spStatus by mutableStateOf("")
    var spList by mutableStateOf<SpList?>(null)
    var spMatches by mutableStateOf<List<SpMatch>>(emptyList())
    var spPlaylistId by mutableStateOf<String?>(null)

    private var page = 0
    private var job: Job? = null
    private var sugJob: Job? = null
    private var spJob: Job? = null
    private var latestJob: Job? = null

    // ------------------------------------------------------------------ search + live suggestions
    fun onQueryChange(q: String) {
        query = q
        sugJob?.cancel()
        val t = q.trim()
        if (t.length < 2 || t.contains("jiosaavn.com") || SpotifyApi.isSpotifyLink(t)) {
            sugSongs = emptyList(); sugColls = emptyList(); sugLoading = false
            return
        }
        sugJob = viewModelScope.launch {
            delay(300)
            sugLoading = true
            try {
                coroutineScope {
                    val songsD = async { runCatching { SaavnApi.searchSongs(t, 0, 8).second }.getOrDefault(emptyList()) }
                    val albumsD = async { runCatching { SaavnApi.searchColl(Kind.ALBUMS, t, 4) }.getOrDefault(emptyList()) }
                    val listsD = async { runCatching { SaavnApi.searchColl(Kind.PLAYLISTS, t, 4) }.getOrDefault(emptyList()) }
                    val s = songsD.await(); val a = albumsD.await(); val p = listsD.await()
                    sugSongs = s
                    sugColls = a + p
                }
            } finally {
                sugLoading = false
            }
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        sugJob?.cancel()
        sugSongs = emptyList(); sugColls = emptyList(); sugLoading = false
        job?.cancel()
        page = 0
        songs = emptyList(); colls = emptyList(); error = null; total = 0; langFilter = "All"
        submitted = q
        Recents.add(q)
        load(q)
    }

    private fun load(q: String) {
        job = viewModelScope.launch {
            loading = true
            try {
                val lim = SettingsStore.state.value.limit
                if (kind == Kind.SONGS) {
                    val (t, l) = SaavnApi.searchSongs(q, page, lim)
                    total = t
                    songs = songs + l
                } else {
                    colls = SaavnApi.searchColl(kind, q, lim)
                }
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
            } finally {
                loading = false
                searched = true
            }
        }
    }

    fun loadMore() {
        if (kind == Kind.SONGS && !loading && songs.isNotEmpty() && songs.size < total) {
            page++
            load(query.trim())
        }
    }

    // ------------------------------------------------------------------ latest releases
    fun loadLatest(force: Boolean = false) {
        if (!force && (latest.isNotEmpty() || latestLoading)) return
        latestJob?.cancel()
        latestJob = viewModelScope.launch {
            latestLoading = true
            latestError = false
            try {
                latest = LatestApi.load(SettingsStore.state.value.homeLang)
                latestError = latest.isEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                latestError = true
            } finally {
                latestLoading = false
            }
        }
    }

    fun setHomeLang(l: String) {
        SettingsStore.update { it.copy(homeLang = l) }
        latest = emptyList()
        loadLatest(true)
    }

    // ------------------------------------------------------------------ "For you"
    fun loadForYou(force: Boolean = false) {
        if (!force && forYou.isNotEmpty()) return
        val favs = Stores.favorites.items.value
        if (favs.isEmpty()) return
        viewModelScope.launch {
            try {
                val picks = favs.shuffled().take(3)
                val out = LinkedHashMap<String, Song>()
                for (f in picks) {
                    try {
                        for (s in SaavnApi.similar(f)) if (favs.none { it.id == s.id }) out.putIfAbsent(s.id, s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                    }
                }
                forYou = out.values.take(30)
                forYouFrom = picks.first().name
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    // ------------------------------------------------------------------ Spotify import
    fun importSpotify(link: String) {
        val p = SpotifyApi.parseLink(link)
        if (p == null) { spStatus = "That is not a Spotify playlist or album link"; return }
        spJob?.cancel()
        spJob = viewModelScope.launch {
            spBusy = true; spList = null; spMatches = emptyList(); spPlaylistId = null
            spStatus = "Reading the Spotify ${p.first}…"
            try {
                val list = SpotifyApi.load(p.first, p.second)
                spList = list
                spStatus = "Matching 0/${list.tracks.size}"
                val m = SpotifyApi.matchAll(list) { d, t -> spStatus = "Matching $d/$t" }
                spMatches = m
                val found = m.mapNotNull { it.song }.distinctBy { it.id }
                if (found.isNotEmpty()) spPlaylistId = Stores.createPlaylist(list.name, found)
                spStatus = "Matched ${found.size} of ${list.tracks.size} songs"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                spStatus = e.message ?: "Import failed"
            } finally {
                spBusy = false
            }
        }
    }

    // ------------------------------------------------------------------ updates
    fun checkUpdate(ctx: Context, manual: Boolean) {
        val s = SettingsStore.state.value
        if (!manual) {
            if (!s.autoUpdate || !s.repo.contains("/")) return
            if (System.currentTimeMillis() - SettingsStore.lastUpdateCheck() < 24L * 3600 * 1000) return
        }
        viewModelScope.launch {
            if (manual) updateStatus = "Checking…"
            try {
                val info = Updater.check(ctx, s.repo)
                SettingsStore.markUpdateChecked()
                if (info != null) { update = info; if (manual) updateStatus = "Update ${info.tag} available" }
                else if (manual) updateStatus = "You are on the latest version"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) updateStatus = e.message ?: "Could not check for updates"
            }
        }
    }

    /** Downloads the update APK inside the app; the dialog then offers "Install". */
    fun downloadUpdate(ctx: Context) {
        val u = update ?: return
        updateJob?.cancel()
        updateBusy = true; updateProgress = 0f; updateError = ""; updateFile = null
        updateJob = viewModelScope.launch {
            try {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val f = File(dir, "SaavnDL-${u.tag}.apk")
                withContext(Dispatchers.IO) { Http.download(u.url, f, { p -> updateProgress = p }) }
                updateFile = f
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateError = e.message ?: "Download failed"
            } finally {
                updateBusy = false
            }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateBusy = false; updateProgress = 0f
    }

    fun dismissUpdate() {
        cancelUpdate()
        update = null; updateFile = null; updateError = ""
    }

    // ------------------------------------------------------------------ batch import
    fun batch(ctx: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val lines = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                    ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                if (lines.isEmpty()) { batchStatus = "File is empty"; return@launch }
                var found = 0
                lines.forEachIndexed { i, line ->
                    batchStatus = "Searching ${i + 1}/${lines.size}: $line"
                    try {
                        val (_, res) = SaavnApi.searchSongs(line, 0, 1)
                        res.firstOrNull()?.let { Downloads.enqueue(ctx, listOf(it)); found++ }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                    }
                }
                batchStatus = "Done: queued $found of ${lines.size} songs"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                batchStatus = "Failed: ${e.message}"
            }
        }
    }
}
