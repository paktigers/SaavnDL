package com.saavndl.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.LibrarySync
import com.saavndl.app.data.Stores
import com.saavndl.app.data.fmtSize
import com.saavndl.app.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class LibEntry(
    val key: String, val title: String, val sub: String, val kind: Int, // 0 playlist, 1 liked songs, 2 downloaded
    val covers: List<String>, val onClick: () -> Unit
)

@Composable
private fun EntryCover(e: LibEntry, modifier: Modifier, radius: androidx.compose.ui.unit.Dp = 10.dp) {
    when (e.kind) {
        1 -> HeartCover(modifier, radius, 30.dp)
        2 -> DownloadCover(modifier, radius, 34.dp)
        else -> MosaicCover(e.covers, modifier, radius)
    }
}

/** "Your Library" - looks like the Spotify library: filter pills, sort + grid toggle, and a list of playlists. */
@Composable
fun LibraryScreen() {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by Stores.library.items.collectAsState()
    val favs by Stores.favorites.items.collectAsState()
    val lists by Stores.playlists.items.collectAsState()
    var pill by rememberSaveable { mutableIntStateOf(-1) }      // -1 = everything
    var sort by rememberSaveable { mutableIntStateOf(0) }       // 0 = recents, 1 = A-Z
    var grid by rememberSaveable { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var plus by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    val entries = buildList<LibEntry> {
        if (pill == -1 || pill == 2) add(LibEntry("fav", "Liked Songs", "Playlist • ${favs.size} songs", 1, emptyList()) { host.push(Route.Favorites) })
        if (pill == -1 || pill == 1) add(LibEntry("dl", "Downloaded", "${items.size} songs • ${fmtSize(items.sumOf { it.bytes })}", 2, emptyList()) { host.push(Route.Downloaded) })
        if (pill == -1 || pill == 0) {
            val pls = if (sort == 1) lists.sortedBy { it.name.lowercase() } else lists
            pls.forEach { p ->
                add(LibEntry(p.id, p.name, "Playlist • ${p.songs.size} songs", 0, p.songs.take(4).map { it.thumb }) { host.push(Route.Playlist(p.id)) })
            }
        }
    }.filter { q.isBlank() || it.title.contains(q, true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your Library", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { search = !search; if (!search) q = "" }) { Icon(Icons.Filled.Search, "Search library") }
            Box {
                IconButton(onClick = { plus = true }) { Icon(Icons.Filled.Add, "Create") }
                DropdownMenu(expanded = plus, onDismissRequest = { plus = false }) {
                    DropdownMenuItem(text = { Text(tr("New playlist")) }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) }, onClick = { plus = false; creating = true })
                    DropdownMenuItem(text = { Text("Import Spotify playlist") }, leadingIcon = { Icon(Icons.Filled.Link, null) }, onClick = { plus = false; host.push(Route.SpotifyImport("")) })
                    DropdownMenuItem(text = { Text("Import songs from phone") }, leadingIcon = { Icon(Icons.Filled.LibraryMusic, null) }, onClick = { plus = false; host.push(Route.ImportDevice) })
                    DropdownMenuItem(text = { Text("Restore from Music/SaavnDL") }, leadingIcon = { Icon(Icons.Filled.Restore, null) }, onClick = {
                        plus = false
                        host.withAudioPermission { ok ->
                            if (!ok) host.snack("Allow audio access to scan your Music/SaavnDL folder")
                            else scope.launch {
                                val added = LibrarySync.scanSaavnFolder(ctx)
                                val removed = withContext(Dispatchers.IO) { LibrarySync.removeMissing(ctx) }
                                host.snack("Added $added songs, removed $removed missing entries")
                            }
                        }
                    })
                }
            }
        }
        if (search) OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Find in Your Library") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(24.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("Playlists", "Downloaded", "Favorites")
            items(names.size) { i ->
                FilterChip(
                    selected = pill == i, onClick = { pill = if (pill == i) -1 else i }, label = { Text(tr(names[i])) },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { sortMenu = true }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapVert, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (sort == 0) "Recents" else "Alphabetical", style = MaterialTheme.typography.bodyMedium)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(text = { Text("Recents") }, onClick = { sort = 0; sortMenu = false })
                    DropdownMenuItem(text = { Text("Alphabetical") }, onClick = { sort = 1; sortMenu = false })
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Filled.ViewList else Icons.Filled.GridView, "Change layout") }
        }

        if (entries.isEmpty()) {
            EmptyState(Icons.Filled.LibraryMusic, tr("Nothing here yet"), "Create a playlist, like songs or download some music.")
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(entries, key = { it.key }) { e ->
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { e.onClick() }) {
                        EntryCover(e, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.key }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { e.onClick() }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EntryCover(e, Modifier.size(68.dp), 8.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (e.kind == 1) Icon(Icons.Filled.PushPin, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                if (e.kind == 2) Icon(Icons.Filled.ArrowCircleDown, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                if (e.kind != 0) Spacer(Modifier.width(5.dp))
                                Text(e.sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (creating) TextInputDialog(tr("New playlist"), "Name", onDismiss = { creating = false }) { name ->
        val id = Stores.createPlaylist(name)
        creating = false
        host.push(Route.Playlist(id))
    }
}
