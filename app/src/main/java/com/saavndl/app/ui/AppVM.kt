package com.saavndl.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saavndl.app.data.Coll
import com.saavndl.app.data.Kind
import com.saavndl.app.data.Recents
import com.saavndl.app.data.SaavnApi
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.Song
import com.saavndl.app.data.Stores
import com.saavndl.app.data.UpdateInfo
import com.saavndl.app.data.Updater
import com.saavndl.app.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppVM : ViewModel() {
    var query by mutableStateOf("")
    var kind by mutableStateOf(Kind.SONGS)
    var songs by mutableStateOf<List<Song>>(emptyList())
    var colls by mutableStateOf<List<Coll>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    var total by mutableIntStateOf(0)
    var batchStatus by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var langFilter by mutableStateOf("All")
    var forYou by mutableStateOf<List<Song>>(emptyList())
    var forYouFrom by mutableStateOf("")
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateStatus by mutableStateOf("")
    private var page = 0
    private var job: Job? = null
    private var sugJob: Job? = null

    // ------------------------------------------------------------------ search
    fun onQueryChange(q: String) {
        query = q
        sugJob?.cancel()
        if (q.trim().length < 2 || q.contains("jiosaavn.com")) { suggestions = emptyList(); return }
        sugJob = viewModelScope.launch {
            delay(350)
            try {
                val (_, l) = SaavnApi.searchSongs(q.trim(), 0, 6)
                suggestions = l.map { it.name }.distinct().take(5)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                suggestions = emptyList()
            }
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        sugJob?.cancel()
        suggestions = emptyList()
        job?.cancel()
        page = 0
        songs = emptyList(); colls = emptyList(); error = null; total = 0; langFilter = "All"
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
                    for (s in SaavnApi.similar(f.id)) {
                        if (favs.none { it.id == s.id }) out.putIfAbsent(s.id, s)
                    }
                }
                forYou = out.values.take(30)
                forYouFrom = picks.first().name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
