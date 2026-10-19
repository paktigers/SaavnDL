package com.saavndl.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.Track

@Composable
fun LibraryScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items by Stores.library.items.collectAsState()
    val favs by Stores.favorites.items.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${items.size} downloaded · ${fmtSize(items.sumOf { it.bytes })} · ${favs.size} favorites",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Downloaded") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Favorites") })
        }
        if (tab == 0) Downloaded(items)
        else if (favs.isEmpty()) EmptyState(Icons.Filled.Favorite, "No favorites yet", "Tap the heart on any song to keep it here.")
        else SongsList(favs, Modifier.fillMaxSize())
    }
}

@Composable
private fun Downloaded(items: List<LibItem>) {
    val ctx = LocalContext.current
    val host = LocalHost.current
    val cur by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    var filter by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<LibItem?>(null) }
    val shown = items.filter { filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) || it.album.contains(filter, true) }

    if (items.isEmpty()) {
        EmptyState(Icons.Filled.LibraryMusic, "Nothing here yet", "Songs you download appear here and in your Music/SaavnDL folder.")
        return
    }
    fun tracks() = shown.map { Track(it.songId, it.title, it.artist, it.thumb, it.uri) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, singleLine = true,
                    placeholder = { Text("Filter downloads") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(onClick = { if (shown.isNotEmpty()) PlayerController.playQueue(tracks(), 0) }) {
                    Icon(Icons.Filled.PlayArrow, null); Text("Play")
                }
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
                    try { ctx.contentResolver.delete(Uri.parse(item.uri), null, null) } catch (e: Exception) { }
                    if (cur?.source == item.uri) PlayerController.stop()
                    Stores.library.update { l -> l.filter { it.uri != item.uri } }
                    host.snack("Deleted")
                    delete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }
        )
    }
}
