package com.saavndl.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.DeviceAudio
import com.saavndl.app.data.FileNames
import com.saavndl.app.data.LibrarySync
import com.saavndl.app.data.fmtDur
import com.saavndl.app.data.fmtSize
import com.saavndl.app.data.tr
import com.saavndl.app.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ====================================================================== Spotify
@Composable
fun SpotifyImportScreen(initial: String, vm: AppVM, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    var link by remember { mutableStateOf(initial) }
    var showMissing by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        if (initial.isNotBlank() && vm.spList == null && !vm.spBusy) vm.importSpotify(initial)
    }
    val found = vm.spMatches.mapNotNull { it.song }.distinctBy { it.id }
    val missing = vm.spMatches.filter { it.song == null }
    val total = vm.spList?.tracks?.size ?: 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Import from Spotify", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Spotify playlist or album link") }, shape = RoundedCornerShape(16.dp)
            )
            Button(enabled = !vm.spBusy && link.isNotBlank(), onClick = { vm.importSpotify(link.trim()) }) { Text("Import") }
            if (vm.spStatus.isNotBlank()) Text(vm.spStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (vm.spBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!vm.spBusy && vm.spList == null && vm.spStatus.isBlank()) {
                Text(
                    "Paste the link of a public Spotify playlist (Share → Copy link). Songs are matched on JioSaavn, saved as a playlist " +
                        "in your Library, and can be downloaded. Spotify only shows the first part of very large playlists to apps without login.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (found.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    Downloads.enqueue(ctx, found, FileNames.clean(vm.spList?.name ?: "Spotify"), found.indices.map { it + 1 }, found.size)
                    host.snack("Added ${found.size} songs to the queue")
                }) { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Download all")) }
                vm.spPlaylistId?.let { id ->
                    OutlinedButton(onClick = { host.push(Route.Playlist(id)) }) {
                        Icon(Icons.Filled.PlaylistPlay, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open playlist")
                    }
                }
            }
        }
        if (missing.isNotEmpty() && !vm.spBusy) {
            TextButton(onClick = { showMissing = !showMissing }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(if (showMissing) "Hide ${missing.size} songs not found" else "${missing.size} songs were not found on JioSaavn")
            }
        }
        if (showMissing) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                missing.take(30).forEach { Text("• ${it.track.title} — ${it.track.artist}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (found.isNotEmpty()) SongsList(found, Modifier.fillMaxSize(), sub = FileNames.clean(vm.spList?.name ?: "Spotify"))
        else if (!vm.spBusy && total > 0) EmptyState(Icons.Filled.Search, "Nothing matched", "None of these songs could be found on JioSaavn.")
    }
}

// ====================================================================== Songs already on the phone
@Composable
fun ImportDeviceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(LibrarySync.hasPermission(ctx)) }
    var loading by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<DeviceAudio>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    fun load() {
        scope.launch {
            loading = true
            items = withContext(Dispatchers.IO) { LibrarySync.queryAudio(ctx, false) }
            loading = false
        }
    }
    LaunchedEffect(granted) { if (granted) load() }

    val shown = items.filter { filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Import songs from your phone", style = MaterialTheme.typography.titleMedium)
        }
        if (!granted) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow access to the music on your phone so SaavnDL can list it. Files stay where they are; they are only added to your library.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { host.withAudioPermission { ok -> granted = ok; if (!ok) host.snack("Permission denied") } }) { Text("Allow access") }
            }
            return@Column
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text("Filter") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(24.dp)
            )
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { selected.clear(); selected.addAll(shown.map { it.id }) }) { Text("Select all") }
            TextButton(onClick = { selected.clear() }) { Text("None") }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(enabled = selected.isNotEmpty() && !busy, onClick = {
                val chosen = items.filter { it.id in selected }
                scope.launch {
                    busy = true; progress = 0
                    val n = LibrarySync.importDevice(ctx, chosen) { progress = it }
                    busy = false
                    host.snack("Imported $n songs into your library")
                    onBack()
                }
            }) { Text(if (busy) "Importing $progress/${selected.size}" else "Import (${selected.size})") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        if (!loading && items.isEmpty()) EmptyState(Icons.Filled.LibraryMusic, "No music found", "No audio files were found on this phone.")
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(shown, key = { i, a -> "${a.id}_$i" }) { _, a ->
                val on = a.id in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (on) selected.remove(a.id) else selected.add(a.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(a.id) else selected.add(a.id) })
                    Column(Modifier.weight(1f)) {
                        Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(a.artist, a.album).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmtDur((a.durationMs / 1000).toInt()) + " · " + fmtSize(a.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
