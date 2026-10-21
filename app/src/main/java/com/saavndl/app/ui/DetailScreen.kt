package com.saavndl.app.ui

import com.saavndl.app.data.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.Detail
import com.saavndl.app.data.FileNames
import com.saavndl.app.data.Kind
import com.saavndl.app.data.SaavnApi
import com.saavndl.app.data.Song
import com.saavndl.app.download.Downloads
import com.saavndl.app.download.MediaSaver
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.toTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed class DState {
    object Loading : DState()
    data class Ok(val d: Detail) : DState()
    data class Err(val msg: String) : DState()
}

@Composable
fun DetailScreen(route: Route, onBack: () -> Unit) {
    var state by remember(route) { mutableStateOf<DState>(DState.Loading) }
    var reload by remember(route) { mutableIntStateOf(0) }
    LaunchedEffect(route, reload) {
        state = DState.Loading
        state = try {
            DState.Ok(
                when (route) {
                    is Route.Detail -> SaavnApi.loadDetail(route.kind, route.id, route.link)
                    is Route.Similar -> Detail(
                        route.song.id, Kind.SONGS, "Similar to ${route.song.name}", route.song.artists,
                        route.song.image, SaavnApi.similar(route.song)
                    )
                    else -> throw IllegalStateException("Unsupported route")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DState.Err(e.message ?: "Failed to load")
        }
    }
    val title = when (route) { is Route.Detail -> route.title; is Route.Similar -> "Similar songs"; else -> "" }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (val s = state) {
            is DState.Loading -> { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)); Spacer(Modifier.height(12.dp)); SkeletonList(6) }
            is DState.Err -> ErrorState(s.msg) { reload++ }
            is DState.Ok -> DetailBody(s.d)
        }
    }
}

@Composable
private fun DetailBody(d: Detail) {
    val scope = rememberCoroutineScope()
    val host = LocalHost.current
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf<List<Song>>(emptyList()) }
    var moreLoading by remember { mutableStateOf(false) }
    var moreEnded by remember { mutableStateOf(false) }
    var morePage by remember { mutableIntStateOf(0) }
    val isArtist = d.kind == Kind.ARTISTS
    val numbered = d.kind == Kind.ALBUMS || d.kind == Kind.PLAYLISTS
    val sub = if (d.kind == Kind.ALBUMS || d.kind == Kind.PLAYLISTS || isArtist) FileNames.clean(d.name) else ""

    fun loadMore() {
        if (moreLoading || moreEnded) return
        moreLoading = true
        scope.launch {
            try {
                val l = SaavnApi.artistSongs(d.id, morePage)
                if (l.isEmpty()) moreEnded = true else { more = more + l; morePage++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                moreEnded = true
                host.snack(e.message ?: "Could not load more")
            } finally {
                moreLoading = false
            }
        }
    }
    LaunchedEffect(tab) { if (isArtist && tab == 2 && more.isEmpty()) loadMore() }

    fun downloadAllAlbums() {
        scope.launch {
            host.snack("Fetching ${d.albums.size} albums…")
            var n = 0
            for (a in d.albums) {
                try {
                    val det = SaavnApi.loadDetail(Kind.ALBUMS, a.id, null)
                    Downloads.enqueue(ctx, det.songs, FileNames.clean(det.name), det.songs.indices.map { it + 1 }, det.songs.size)
                    n += det.songs.size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                }
            }
            host.snack("Added $n songs to the queue")
        }
    }

    val songs = if (isArtist && tab == 2) more else d.songs
    val header: @Composable () -> Unit = {
        Column {
            Header(d, songs, numbered, sub)
            if (isArtist) ArtistTabs(tab, d.albums.isNotEmpty()) { tab = it }
        }
    }

    if (isArtist && tab == 1) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { header() }
            if (d.albums.isNotEmpty()) item {
                FilledTonalButton(onClick = { downloadAllAlbums() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download all albums (${d.albums.size})")
                }
            }
            items(d.albums) { c -> CollRow(c) { host.push(Route.Detail(Kind.ALBUMS, c.id, null, c.name, c.image)) } }
        }
    } else {
        SongsList(
            songs = songs, sub = sub, numbered = numbered, header = header,
            footer = { if (moreLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) },
            onEnd = if (isArtist && tab == 2) ({ loadMore() }) else null
        )
    }
}

@Composable
private fun ArtistTabs(tab: Int, hasAlbums: Boolean, onTab: (Int) -> Unit) {
    TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
        Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text("Top songs") })
        Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text(if (hasAlbums) "Albums" else "Albums (none)") })
        Tab(selected = tab == 2, onClick = { onTab(2) }, text = { Text("All songs") })
    }
}

@Composable
private fun Header(d: Detail, songs: List<Song>, numbered: Boolean, sub: String) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(d.image, 170.dp, radius = if (d.kind == Kind.ARTISTS) 85.dp else 22.dp)
        Spacer(Modifier.height(14.dp))
        Text(d.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (d.subtitle.isNotBlank()) Text(d.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("${songs.size} songs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (songs.isEmpty()) return@Button
                Downloads.enqueue(ctx, songs, sub, if (numbered) songs.indices.map { it + 1 } else null, if (numbered) songs.size else 0)
                host.snack("Added ${songs.size} songs to queue")
            }) { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Download all")) }
            FilledTonalButton(onClick = { if (songs.isNotEmpty()) PlayerController.playQueue(songs.map { it.toTrack() }, 0) }) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Play"))
            }
            IconButton(onClick = {
                try {
                    val n = MediaSaver.exportCsv(ctx, songs)
                    host.snack("CSV saved to Download/SaavnDL/$n")
                } catch (e: Exception) { host.snack("Export failed: ${e.message}") }
            }) { Icon(Icons.Filled.FileUpload, "Export CSV") }
        }
    }
}
