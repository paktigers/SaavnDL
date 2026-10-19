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
import com.saavndl.app.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var page = 0
    private var job: Job? = null

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        job?.cancel()
        page = 0
        songs = emptyList(); colls = emptyList(); error = null; total = 0
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

    /** Batch: each line of a text file is searched and the top result is queued. */
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
                    } catch (e: Exception) { }
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
