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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.FileNames
import com.saavndl.app.data.Stores
import com.saavndl.app.data.tr
import com.saavndl.app.download.Downloads
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.toTrack

private fun <T> List<T>.swap(a: Int, b: Int): List<T> =
    toMutableList().also { val x = it[a]; it[a] = it[b]; it[b] = x }

@Composable
fun PlaylistsTab() {
    val lists by Stores.playlists.items.collectAsState()
    val host = LocalHost.current
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            FilledTonalButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("New playlist"))
            }
        }
        if (lists.isEmpty()) {
            EmptyState(Icons.Filled.PlaylistPlay, "No playlists yet", "Create one, then add songs from the menu of any song.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(lists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
                            .clickable { host.push(Route.Playlist(p.id)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(p.songs.firstOrNull()?.thumb.orEmpty(), 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text("${p.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (creating) TextInputDialog(tr("New playlist"), "Name", onDismiss = { creating = false }) {
        val id = Stores.createPlaylist(it)
        creating = false
        host.push(Route.Playlist(id))
    }
}

@Composable
fun PlaylistScreen(id: String, onBack: () -> Unit) {
    val lists by Stores.playlists.items.collectAsState()
    val p = lists.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    val host = LocalHost.current
    if (p == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { rename = true }) { Icon(Icons.Filled.Edit, "Rename") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete playlist") }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                if (p.songs.isEmpty()) return@Button
                Downloads.enqueue(ctx, p.songs, FileNames.clean(p.name), p.songs.indices.map { it + 1 }, p.songs.size)
                host.snack("Added ${p.songs.size} songs to queue")
            }) { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Download all")) }
            FilledTonalButton(onClick = { if (p.songs.isNotEmpty()) PlayerController.playQueue(p.songs.map { it.toTrack() }, 0) }) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Play"))
            }
        }
        if (p.songs.isEmpty()) {
            EmptyState(Icons.Filled.PlaylistPlay, "Empty playlist", "Open the menu of any song and choose \"Add to playlist\".")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(p.songs, key = { i, s -> "${s.id}_$i" }) { i, s ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
                            .clickable { PlayerController.playQueue(p.songs.map { it.toTrack() }, i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(s.thumb, 50.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(s.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (i > 0) Stores.playlists.update { l -> l.map { if (it.id == id) it.copy(songs = it.songs.swap(i, i - 1)) else it } } }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, "Move up")
                        }
                        IconButton(onClick = { if (i < p.songs.size - 1) Stores.playlists.update { l -> l.map { if (it.id == id) it.copy(songs = it.songs.swap(i, i + 1)) else it } } }, enabled = i < p.songs.size - 1) {
                            Icon(Icons.Filled.ArrowDownward, "Move down")
                        }
                        IconButton(onClick = { Stores.playlists.update { l -> l.map { if (it.id == id) it.copy(songs = it.songs.filterIndexed { j, _ -> j != i }) else it } } }) {
                            Icon(Icons.Filled.Close, "Remove")
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (rename) TextInputDialog("Rename playlist", "Name", p.name, "Save", onDismiss = { rename = false }) { n ->
        Stores.playlists.update { l -> l.map { if (it.id == id) it.copy(name = n) else it } }
        rename = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete playlist?") },
        text = { Text("\"${p.name}\" will be removed. Your songs are not deleted.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                Stores.playlists.update { l -> l.filter { it.id != id } }
                onBack()
            }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Cancel")) } }
    )
}
