package com.saavndl.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

private fun dupGroups(items: List<LibItem>): List<List<LibItem>> =
    items.groupBy { norm(it.title) + "|" + norm(it.artist.split(",").first()) }.values.filter { it.size > 1 }


private fun LibItem.track() = Track(songId, title, artist, thumb, uri, album, 0, image)

/** All downloaded songs. Long-press a song to select several (checkboxes), "⋮" opens the menu of a song. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    val items by Stores.library.items.collectAsState()
    val cur by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    var filter by remember { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val sel = remember { mutableStateListOf<String>() }
    var menuItem by remember { mutableStateOf<LibItem?>(null) }
    var toDelete by remember { mutableStateOf<List<LibItem>?>(null) }
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
    fun tracks() = shown.map { it.track() }
    fun toggle(uri: String) { if (!sel.remove(uri)) sel.add(uri) }
    fun deleteFile(it: LibItem) {
        if (!it.songId.startsWith("local_")) {
            try { ctx.contentResolver.delete(Uri.parse(it.uri), null, null) } catch (e: Exception) { }
        }
        if (cur?.source == it.uri) PlayerController.stop()
    }

    Column(Modifier.fillMaxSize()) {
        if (sel.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { sel.clear() }) { Icon(Icons.Filled.Close, "Cancel selection") }
                    Text("${sel.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { sel.clear(); sel.addAll(shown.map { it.uri }) }) { Icon(Icons.Filled.SelectAll, "Select all") }
                    IconButton(onClick = { toDelete = items.filter { it.uri in sel } }) { Icon(Icons.Filled.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Downloaded", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Box {
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

        if (items.isEmpty()) {
            EmptyState(Icons.Filled.LibraryMusic, tr("Nothing here yet"), "Songs you download appear here and in your Music/SaavnDL folder.")
        } else LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it }, singleLine = true,
                        placeholder = { Text(tr("Filter downloads")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { if (shown.isNotEmpty()) PlayerController.playQueue(tracks(), 0) }) {
                        Icon(Icons.Filled.PlayArrow, null); Text(tr("Play"))
                    }
                }
            }
            item {
                LazyRow(Modifier.padding(bottom = 6.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val names = listOf("Newest", "Name", "Artist", "Size")
                    items(names.size) { i -> FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(names[i]) }) }
                }
            }
            itemsIndexed(shown, key = { i, li -> "${li.uri}_$i" }) { i, li ->
                val isCur = cur?.source == li.uri
                val selected = li.uri in sel
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (sel.isNotEmpty()) toggle(li.uri)
                                else if (isCur) PlayerController.toggle() else PlayerController.playQueue(tracks(), i)
                            },
                            onLongClick = { toggle(li.uri) }
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sel.isNotEmpty()) { Checkbox(checked = selected, onCheckedChange = { toggle(li.uri) }); Spacer(Modifier.width(2.dp)) }
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
                    if (sel.isEmpty()) IconButton(onClick = { menuItem = li }) { Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    menuItem?.let { li ->
        val local = li.songId.startsWith("local_")
        MenuSheet(
            li.title, li.artist, li.thumb,
            buildList {
                add(MenuItem(Icons.Filled.SkipNext, tr("Play next")) { menuItem = null; PlayerController.playNext(li.track()); host.snack("Will play next") })
                add(MenuItem(Icons.Filled.QueueMusic, tr("Add to queue")) { menuItem = null; PlayerController.addToQueue(li.track()); host.snack("Added to queue") })
                add(MenuItem(Icons.Filled.FormatListBulleted, "Go to Queue") { menuItem = null; host.push(Route.PlayQueue) })
                if (!local) add(MenuItem(Icons.Filled.Edit, tr("Edit tags")) { menuItem = null; edit = li })
                add(MenuItem(Icons.Filled.Share, "Share") {
                    menuItem = null
                    val send = Intent(Intent.ACTION_SEND).setType(if (li.format == "MP3") "audio/mpeg" else "audio/mp4")
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(li.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                add(MenuItem(Icons.Filled.Delete, if (local) "Remove from library" else "Delete from device", destructive = true) { menuItem = null; toDelete = listOf(li) })
            }
        ) { menuItem = null }
    }

    toDelete?.let { list ->
        val allLocal = list.all { it.songId.startsWith("local_") }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (allLocal) "Remove from library?" else if (list.size == 1) "Delete song?" else "Delete ${list.size} songs?") },
            text = {
                Text(
                    if (allLocal) "${list.size} song(s) will be removed from SaavnDL. The files stay on your phone."
                    else "${list.size} song(s) will be deleted from your phone's storage. Songs imported from your phone are only removed from the library."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    list.forEach { deleteFile(it) }
                    val gone = list.map { it.uri }.toSet()
                    Stores.library.update { l -> l.filter { it.uri !in gone } }
                    sel.clear()
                    host.snack("Deleted ${list.size} song(s)")
                    toDelete = null
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(tr("Cancel")) } }
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
internal fun TagEditorDialog(item: LibItem, onDismiss: () -> Unit) {
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
