package com.saavndl.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.LibItem
import com.saavndl.app.data.Stores
import com.saavndl.app.data.fmtSize
import com.saavndl.app.data.tr
import com.saavndl.app.download.TagEditor
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items by Stores.library.items.collectAsState()
    val favs by Stores.favorites.items.collectAsState()
    val lists by Stores.playlists.items.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            Text(tr("Library"), style = MaterialTheme.typography.headlineMedium)
            Text(
                "${items.size} downloaded · ${fmtSize(items.sumOf { it.bytes })} · ${favs.size} favorites · ${lists.size} playlists",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("Downloaded")) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("Favorites")) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(tr("Playlists")) })
        }
        when (tab) {
            0 -> Downloaded(items)
            1 -> {
                if (favs.isEmpty()) EmptyState(Icons.Filled.Favorite, tr("No favorites yet"), "Tap the heart on any song to keep it here.")
                else SongsList(favs, Modifier.fillMaxSize())
            }
            else -> PlaylistsTab()
        }
    }
}

private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

private fun dupGroups(items: List<LibItem>): List<List<LibItem>> =
    items.groupBy { norm(it.title) + "|" + norm(it.artist.split(",").first()) }.values.filter { it.size > 1 }

@Composable
private fun Downloaded(items: List<LibItem>) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    val cur by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    var filter by remember { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var delete by remember { mutableStateOf<LibItem?>(null) }
    var edit by remember { mutableStateOf<LibItem?>(null) }
    var tools by remember { mutableStateOf(false) }
    var dupDialog by remember { mutableStateOf<List<List<LibItem>>?>(null) }

    val filtered = items.filter { filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) || it.album.contains(filter, true) }
    val shown = when (sort) {
        1 -> filtered.sortedBy { it.title.lowercase() }
        2 -> filtered.sortedBy { it.artist.lowercase() }
        3 -> filtered.sortedByDescending { it.bytes }
        else -> filtered.sortedByDescending { it.time }
    }

    if (items.isEmpty()) {
        EmptyState(Icons.Filled.LibraryMusic, tr("Nothing here yet"), "Songs you download appear here and in your Music/SaavnDL folder.")
        return
    }
    fun tracks() = shown.map { Track(it.songId, it.title, it.artist, it.thumb, it.uri, it.album, 0, it.image) }
    fun deleteFile(it: LibItem) {
        try { ctx.contentResolver.delete(Uri.parse(it.uri), null, null) } catch (e: Exception) { }
        if (cur?.source == it.uri) PlayerController.stop()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text(tr("Filter downloads")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { if (shown.isNotEmpty()) PlayerController.playQueue(tracks(), 0) }) {
                    Icon(Icons.Filled.PlayArrow, null); Text(tr("Play"))
                }
                Row {
                    IconButton(onClick = { tools = true }) { Icon(Icons.Filled.MoreVert, "Tools") }
                    DropdownMenu(expanded = tools, onDismissRequest = { tools = false }) {
                        DropdownMenuItem(text = { Text("Find duplicates") }, onClick = {
                            tools = false
                            val g = dupGroups(items)
                            if (g.isEmpty()) host.snack("No duplicates found") else dupDialog = g
                        })
                        DropdownMenuItem(text = { Text("Remove missing entries") }, onClick = {
                            tools = false
                            val missing = items.filter { li ->
                                try {
                                    ctx.contentResolver.query(Uri.parse(li.uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count == 0 } ?: true
                                } catch (e: Exception) { true }
                            }
                            if (missing.isEmpty()) host.snack("Everything is in place") else {
                                val uris = missing.map { it.uri }.toSet()
                                Stores.library.update { l -> l.filter { it.uri !in uris } }
                                host.snack("Removed ${missing.size} missing entries")
                            }
                        })
                    }
                }
            }
        }
        item {
            LazyRow(Modifier.padding(bottom = 6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val names = listOf("Newest", "Name", "Artist", "Size")
                items(names.size) { i -> FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(names[i]) }) }
            }
        }
        itemsIndexed(shown, key = { i, li -> "${li.uri}_$i" }) { i, li ->
            val isCur = cur?.source == li.uri
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
                    .clickable { if (isCur) PlayerController.toggle() else PlayerController.playQueue(tracks(), i) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(li.thumb, 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(li.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                        color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(li.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(li.format, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(fmtSize(li.bytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isCur && playing) Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(14.dp))
                    }
                }
                IconButton(onClick = { edit = li }) { Icon(Icons.Filled.Edit, tr("Edit tags"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType(if (li.format == "MP3") "audio/mpeg" else "audio/mp4")
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(li.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { delete = li }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    delete?.let { item ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("Delete song?") },
            text = { Text("\"${item.title}\" will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFile(item)
                    Stores.library.update { l -> l.filter { it.uri != item.uri } }
                    host.snack("Deleted")
                    delete = null
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(tr("Cancel")) } }
        )
    }

    dupDialog?.let { groups ->
        val extras = groups.flatMap { g -> val keep = g.maxByOrNull { it.bytes }; g.filter { it !== keep } }
        AlertDialog(
            onDismissRequest = { dupDialog = null },
            title = { Text("Duplicates found") },
            text = { Text("${groups.size} songs exist more than once. Delete ${extras.size} extra copies (${fmtSize(extras.sumOf { it.bytes })}) and keep the largest file of each?") },
            confirmButton = {
                TextButton(onClick = {
                    extras.forEach { deleteFile(it) }
                    val gone = extras.map { it.uri }.toSet()
                    Stores.library.update { l -> l.filter { it.uri !in gone } }
                    host.snack("Deleted ${extras.size} duplicates")
                    dupDialog = null
                }) { Text("Delete extras", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dupDialog = null }) { Text(tr("Cancel")) } }
        )
    }

    edit?.let { TagEditorDialog(it) { edit = null } }
}

@Composable
private fun TagEditorDialog(item: LibItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(item.title) }
    var artist by remember { mutableStateOf(item.artist) }
    var album by remember { mutableStateOf(item.album) }
    var year by remember { mutableStateOf(item.year) }
    var refresh by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Edit tags")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                OutlinedTextField(year, { year = it }, label = { Text("Year") }, singleLine = true)
                Row(Modifier.clickable { refresh = !refresh }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(refresh, { refresh = it })
                    Text("Re-download cover art", style = MaterialTheme.typography.bodyMedium)
                }
                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && title.isNotBlank(), onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val upd = TagEditor.edit(ctx, item, title.trim(), artist.trim(), album.trim(), year.trim(), refresh)
                        Stores.library.update { l -> l.map { if (it.uri == item.uri) upd else it } }
                        host.snack("Tags updated")
                        onDismiss()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Could not update the file"
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "Saving…" else tr("Save")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}
