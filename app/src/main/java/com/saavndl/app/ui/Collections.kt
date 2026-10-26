package com.saavndl.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saavndl.app.data.FileNames
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.Song
import com.saavndl.app.data.Stores
import com.saavndl.app.data.fmtDur
import com.saavndl.app.data.tr
import com.saavndl.app.download.DlStatus
import com.saavndl.app.download.Downloads
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.toTrack

fun fmtTotal(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "$m min"
}

// ------------------------------------------------------------------ covers
@Composable
fun ImageTile(url: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** 2x2 collage of up to four covers (like a Spotify playlist), a single cover when there are fewer. */
@Composable
fun MosaicCover(urls: List<String>, modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    val u = urls.filter { it.isNotBlank() }.distinct()
    Box(modifier.clip(RoundedCornerShape(radius))) {
        if (u.size >= 4) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ImageTile(u[0], Modifier.weight(1f).fillMaxHeight()); ImageTile(u[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ImageTile(u[2], Modifier.weight(1f).fillMaxHeight()); ImageTile(u[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else ImageTile(u.firstOrNull().orEmpty(), Modifier.fillMaxSize())
    }
}

@Composable
fun HeartCover(modifier: Modifier = Modifier, radius: Dp = 10.dp, iconSize: Dp = 30.dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Brush.linearGradient(listOf(Color(0xFF4B3AE8), Color(0xFF7FE3C9)))),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
fun DownloadCover(modifier: Modifier = Modifier, radius: Dp = 10.dp, iconSize: Dp = 30.dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Brush.linearGradient(listOf(Color(0xFF0B3B3A), Color(0xFF2DD4BF)))),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.ArrowCircleDown, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

/** Spotify-style download button: outline = not downloaded, ring = downloading, filled green = everything downloaded. */
@Composable
fun DownloadStateButton(state: Int, progress: Float, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        when (state) {
            2 -> DownloadedBadge(30.dp)
            1 -> {
                CircularProgressIndicator(progress = { progress.coerceIn(0.04f, 1f) }, modifier = Modifier.size(30.dp), strokeWidth = 2.5.dp)
                Icon(Icons.Filled.Stop, "Cancel download", Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
            }
            else -> Box(Modifier.size(30.dp).border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowDownward, "Download all", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ hero (like a Spotify playlist page)
@Composable
fun CollectionHero(
    title: String, meta: String, covers: List<String>, heart: Boolean,
    onBack: () -> Unit, onDownload: () -> Unit, onShare: () -> Unit, onMore: () -> Unit,
    onShuffle: () -> Unit, onPlay: () -> Unit,
    downloadState: Int = 0, downloadProgress: Float = 0f,
    chips: @Composable () -> Unit = {}
) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .28f), Color.Transparent)))
    ) {
        Box(
            Modifier.padding(start = 12.dp, top = 8.dp).size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            val m = Modifier.size(210.dp)
            if (heart) HeartCover(m, 12.dp, 72.dp) else MosaicCover(covers, m, 12.dp)
        }
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(8.dp))
            Text("You", style = MaterialTheme.typography.titleSmall)
        }
        Text(meta, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DownloadStateButton(downloadState, downloadProgress, onDownload)
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle") }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(4.dp))
        }
        chips()
    }
}

// ------------------------------------------------------------------ playlist
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(id: String, onBack: () -> Unit) {
    val lists by Stores.playlists.items.collectAsState()
    val p = lists.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    val host = LocalHost.current
    val cur by PlayerController.current.collectAsState()
    if (p == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    val songs = p.songs.distinctBy { it.id }
    val listState = rememberLazyListState()
    val rs = rememberReorderState(songs, listState) { it.id }
    fun commit(l: List<Song>) { Stores.playlists.update { all -> all.map { if (it.id == id) it.copy(songs = l) else it } } }
    fun toggle(sid: String) { if (!sel.remove(sid)) sel.add(sid) }
    val sub = FileNames.clean(p.name)

    // download state of the whole playlist (like Spotify: outline -> ring -> filled)
    val tasks by Downloads.tasks.collectAsState()
    val lib by Stores.library.items.collectAsState()
    val libIds = remember(lib) { lib.map { it.songId }.toSet() }
    val taskBy = remember(tasks) { tasks.groupBy { it.song.id }.mapValues { it.value.last() } }
    val dlDone = songs.count { it.id in libIds }
    val dlActive = songs.mapNotNull { taskBy[it.id] }.filter { it.active }
    val dlState = when {
        songs.isNotEmpty() && dlDone == songs.size -> 2
        dlActive.isNotEmpty() -> 1
        else -> 0
    }
    val dlProgress = if (songs.isEmpty()) 0f else (dlDone + dlActive.fold(0f) { a, t -> a + t.progress }) / songs.size

    fun downloadAll() {
        val todo = songs.filter { it.id !in libIds && taskBy[it.id]?.active != true }
        if (todo.isEmpty()) { host.snack("All songs are already downloaded"); return }
        Stores.setPlaylistAutoDownload(id, true)   // songs added later are downloaded automatically
        val positions = todo.map { s -> songs.indexOfFirst { it.id == s.id } + 1 }
        Downloads.enqueue(ctx, todo, sub, positions, songs.size)
        host.snack("Downloading ${todo.size} songs")
    }
    fun onDownloadClick() {
        when (dlState) {
            2 -> host.snack("All songs in this playlist are downloaded")
            1 -> {
                dlActive.forEach { Downloads.cancel(it.id) }
                Stores.setPlaylistAutoDownload(id, false)
                host.snack("Download cancelled")
            }
            else -> downloadAll()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(sel.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { sel.clear() }) { Icon(Icons.Filled.Close, "Cancel selection") }
                    Text("${sel.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { sel.clear(); sel.addAll(songs.map { it.id }) }) { Icon(Icons.Filled.SelectAll, "Select all") }
                    IconButton(onClick = {
                        val keep = songs.filter { it.id !in sel }
                        sel.clear(); commit(keep)
                    }) { Icon(Icons.Filled.Delete, "Remove from playlist", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "hero") {
                CollectionHero(
                    title = p.name,
                    meta = "${songs.size} songs" + if (songs.isNotEmpty()) " • ${fmtTotal(songs.sumOf { it.duration })}" else "",
                    covers = songs.take(4).map { it.thumb }, heart = false,
                    onBack = onBack, onDownload = { onDownloadClick() },
                    downloadState = dlState, downloadProgress = dlProgress,
                    onShare = { shareText(ctx, p.name + "\n" + songs.take(60).joinToString("\n") { "${it.name} - ${it.artists}" }) },
                    onMore = { more = true },
                    onShuffle = { if (songs.isNotEmpty()) PlayerController.playShuffled(songs.map { it.toTrack() }) },
                    onPlay = { if (songs.isNotEmpty()) PlayerController.playQueue(songs.map { it.toTrack() }, 0) }
                ) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            AssistChip(
                                onClick = { editing = !editing; sel.clear() },
                                label = { Text(if (editing) "Done" else "Edit") },
                                leadingIcon = { Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, null, Modifier.size(18.dp)) }
                            )
                        }
                        item {
                            Box {
                                AssistChip(
                                    onClick = { sortMenu = true }, label = { Text("Sort") },
                                    leadingIcon = { Icon(Icons.Filled.Sort, null, Modifier.size(18.dp)) }
                                )
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    DropdownMenuItem(text = { Text("Title A–Z") }, onClick = { sortMenu = false; commit(songs.sortedBy { it.name.lowercase() }) })
                                    DropdownMenuItem(text = { Text("Artist A–Z") }, onClick = { sortMenu = false; commit(songs.sortedBy { it.artists.lowercase() }) })
                                    DropdownMenuItem(text = { Text("Reverse order") }, onClick = { sortMenu = false; commit(songs.reversed()) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (songs.isEmpty()) item(key = "empty") {
                EmptyState(Icons.Filled.PlaylistPlay, "Empty playlist", "Open the ⋮ menu of any song and choose \"Add to playlist\".")
            }
            items(rs.items, key = { it.id }) { s ->
                val dragging = rs.dragKey == s.id
                val selected = s.id in sel
                val isCur = cur?.id == s.id
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .then(rs.visual(s.id))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
                            else if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else Color.Transparent
                        )
                        .combinedClickable(
                            onClick = {
                                if (editing) return@combinedClickable
                                if (sel.isNotEmpty()) toggle(s.id)
                                else PlayerController.playQueue(songs.map { it.toTrack() }, songs.indexOfFirst { x -> x.id == s.id }.coerceAtLeast(0))
                            },
                            onLongClick = { if (!editing) toggle(s.id) }
                        )
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sel.isNotEmpty()) { Checkbox(checked = selected, onCheckedChange = { toggle(s.id) }); Spacer(Modifier.width(2.dp)) }
                    if (editing) IconButton(onClick = { commit(songs.filter { x -> x.id != s.id }) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                    Cover(s.thumb, 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                            color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(s.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (editing) {
                        Box(Modifier.size(44.dp).then(rs.handle(s.id) { commit(it) }), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DragHandle, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (sel.isEmpty()) {
                        val t = taskBy[s.id]
                        val inLib = s.id in libIds
                        if (inLib || (t != null && (t.active || t.status == DlStatus.FAILED))) {
                            IconButton(onClick = {
                                if (t != null && t.active) Downloads.cancel(t.id)
                                else if (t != null && t.status == DlStatus.FAILED) Downloads.retry(ctx, t.id)
                            }) { DownloadStatusIcon(t, inLib, idle = false) }
                        }
                        IconButton(onClick = { menuSong = s }) { Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item(key = "space") { Spacer(Modifier.height(24.dp)) }
        }
    }

    menuSong?.let { s ->
        SongMenu(s, listOf(MenuItem(Icons.Filled.RemoveCircleOutline, "Remove from this playlist", destructive = true) { commit(songs.filter { x -> x.id != s.id }) })) { menuSong = null }
    }
    if (more) MenuSheet(
        p.name, "${songs.size} songs", songs.firstOrNull()?.thumb.orEmpty(),
        listOf(
            MenuItem(Icons.Filled.Edit, "Rename") { more = false; rename = true },
            MenuItem(Icons.Filled.Edit, "Edit order and remove songs") { more = false; editing = true },
            MenuItem(Icons.Filled.ArrowCircleDown, "Download all") { more = false; downloadAll() },
            MenuItem(Icons.Filled.Delete, "Delete playlist", destructive = true) { more = false; confirmDelete = true }
        )
    ) { more = false }
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

// ------------------------------------------------------------------ liked songs
@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    val favs by Stores.favorites.items.collectAsState()
    val ctx = LocalContext.current
    val host = LocalHost.current
    var more by remember { mutableStateOf(false) }
    val tasks by Downloads.tasks.collectAsState()
    val lib by Stores.library.items.collectAsState()
    val libIds = remember(lib) { lib.map { it.songId }.toSet() }
    val taskBy = remember(tasks) { tasks.groupBy { it.song.id }.mapValues { it.value.last() } }
    val dlDone = favs.count { it.id in libIds }
    val dlActive = favs.mapNotNull { taskBy[it.id] }.filter { it.active }
    val dlState = when {
        favs.isNotEmpty() && dlDone == favs.size -> 2
        dlActive.isNotEmpty() -> 1
        else -> 0
    }
    val dlProgress = if (favs.isEmpty()) 0f else (dlDone + dlActive.fold(0f) { a, t -> a + t.progress }) / favs.size
    fun downloadAll() {
        val todo = favs.filter { it.id !in libIds && taskBy[it.id]?.active != true }
        if (todo.isEmpty()) { host.snack("All songs are already downloaded"); return }
        SettingsStore.update { it.copy(autoFavDownload = true) }   // newly liked songs are downloaded automatically
        Downloads.enqueue(ctx, todo, "Liked Songs")
        host.snack("Downloading ${todo.size} songs")
    }
    fun onDownloadClick() {
        when (dlState) {
            2 -> host.snack("All liked songs are downloaded")
            1 -> {
                dlActive.forEach { Downloads.cancel(it.id) }
                SettingsStore.update { it.copy(autoFavDownload = false) }
                host.snack("Download cancelled")
            }
            else -> downloadAll()
        }
    }
    SongsList(
        songs = favs, modifier = Modifier.fillMaxSize(), sub = "Liked Songs",
        header = {
            CollectionHero(
                title = "Liked Songs",
                meta = "${favs.size} songs" + if (favs.isNotEmpty()) " • ${fmtTotal(favs.sumOf { it.duration })}" else "",
                covers = favs.take(4).map { it.thumb }, heart = true,
                onBack = onBack, onDownload = { onDownloadClick() },
                downloadState = dlState, downloadProgress = dlProgress,
                onShare = { shareText(ctx, "Liked Songs\n" + favs.take(60).joinToString("\n") { "${it.name} - ${it.artists}" }) },
                onMore = { more = true },
                onShuffle = { if (favs.isNotEmpty()) PlayerController.playShuffled(favs.map { it.toTrack() }) },
                onPlay = { if (favs.isNotEmpty()) PlayerController.playQueue(favs.map { it.toTrack() }, 0) }
            )
        },
        footer = { if (favs.isEmpty()) EmptyState(Icons.Filled.Favorite, tr("No favorites yet"), "Tap the heart on any song to keep it here.") },
        onRemoveSelected = { list -> list.forEach { Stores.toggleFavorite(it) } }
    )
    if (more) MenuSheet(
        "Liked Songs", "${favs.size} songs", favs.firstOrNull()?.thumb.orEmpty(),
        listOf(MenuItem(Icons.Filled.ArrowCircleDown, "Download all") { more = false; downloadAll() })
    ) { more = false }
}
