package com.saavndl.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.Kind
import com.saavndl.app.data.SaavnApi
import com.saavndl.app.data.Song
import com.saavndl.app.data.Stores
import com.saavndl.app.data.appScope
import com.saavndl.app.data.tr
import com.saavndl.app.download.Downloads
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.toTrack
import kotlinx.coroutines.launch

data class MenuItem(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val chevron: Boolean = false,
    val onClick: () -> Unit
)

/** Spotify-style bottom sheet: header with cover + title, then a list of actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(title: String, subtitle: String, image: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(image, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            items.forEach { m ->
                val color = if (m.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier.fillMaxWidth().clickable { m.onClick() }.padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(m.icon, null, tint = if (m.destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(18.dp))
                    Text(m.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = color)
                    if (m.chevron) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The "⋮" menu of a song (share, add to playlist, queue, album, artists ...). [extra] adds context specific items. */
@Composable
fun SongMenu(song: Song, extra: List<MenuItem> = emptyList(), onDismiss: () -> Unit) {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val favs by Stores.favorites.items.collectAsState()
    val lib by Stores.library.items.collectAsState()
    val tasks by Downloads.tasks.collectAsState()
    var sheet by remember { mutableStateOf(true) }
    var addPl by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var pickArtist by remember { mutableStateOf(false) }
    val fav = favs.any { it.id == song.id }
    val downloaded = lib.any { it.songId == song.id }
    val busy = tasks.any { it.song.id == song.id && it.active }

    fun goAlbum() {
        if (song.albumId.isNotBlank()) {
            host.push(Route.Detail(Kind.ALBUMS, song.albumId, null, song.album, song.image))
        } else appScope.launch {
            try {
                val c = SaavnApi.searchColl(Kind.ALBUMS, "${song.album} ${song.firstArtist}".trim(), 5).firstOrNull()
                if (c != null) host.push(Route.Detail(Kind.ALBUMS, c.id, null, c.name, c.image)) else host.snack("Album not found")
            } catch (e: Exception) { host.snack("Album not found") }
        }
    }
    fun goArtist(id: String?, name: String) {
        if (id != null) {
            host.push(Route.Detail(Kind.ARTISTS, id, null, name, ""))
        } else appScope.launch {
            try {
                val c = SaavnApi.searchColl(Kind.ARTISTS, name, 3).firstOrNull()
                if (c != null) host.push(Route.Detail(Kind.ARTISTS, c.id, null, c.name, c.image)) else host.snack("Artist not found")
            } catch (e: Exception) { host.snack("Artist not found") }
        }
    }

    val items = buildList {
        add(MenuItem(Icons.Filled.Share, "Share") { shareText(ctx, "${song.name} - ${song.artists}\n${song.url}"); onDismiss() })
        add(MenuItem(Icons.Filled.PlaylistAdd, tr("Add to playlist")) { sheet = false; addPl = true })
        extra.forEach { e -> add(e.copy(onClick = { e.onClick(); onDismiss() })) }
        add(MenuItem(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) "Remove from Liked Songs" else "Add to Liked Songs") {
            Stores.toggleFavorite(song); onDismiss()
        })
        add(MenuItem(Icons.Filled.Download, if (downloaded) "Downloaded" else if (busy) "Downloading…" else "Download") {
            if (downloaded) host.snack("Already in your library")
            else if (!busy) { Downloads.enqueue(ctx, listOf(song)); host.snack("Added to queue") }
            onDismiss()
        })
        add(MenuItem(Icons.Filled.QueueMusic, tr("Add to queue")) { PlayerController.addToQueue(song.toTrack()); host.snack("Added to queue"); onDismiss() })
        add(MenuItem(Icons.Filled.SkipNext, tr("Play next")) { PlayerController.playNext(song.toTrack()); host.snack("Will play next"); onDismiss() })
        add(MenuItem(Icons.Filled.FormatListBulleted, "Go to Queue") { onDismiss(); host.push(Route.PlayQueue) })
        add(MenuItem(Icons.Filled.Layers, tr("Similar songs")) { onDismiss(); host.push(Route.Similar(song)) })
        add(MenuItem(Icons.Filled.Album, "Go to album") { onDismiss(); goAlbum() })
        add(MenuItem(Icons.Filled.Person, "Go to artists", chevron = song.artistRefs.size > 1) {
            if (song.artistRefs.size > 1) { sheet = false; pickArtist = true }
            else { onDismiss(); goArtist(song.artistRefs.firstOrNull()?.id, song.artistRefs.firstOrNull()?.name ?: song.firstArtist.ifBlank { song.artists }) }
        })
        add(MenuItem(Icons.Filled.Info, tr("Song info")) { sheet = false; info = true })
        add(MenuItem(Icons.Filled.ContentCopy, tr("Copy link")) {
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", song.url)); host.snack("Link copied"); onDismiss()
        })
    }

    if (sheet) MenuSheet(song.name, song.artists, song.thumb, items, onDismiss)
    if (addPl) AddToPlaylistDialog(listOf(song)) { onDismiss() }
    if (info) SongInfoDialog(song) { onDismiss() }
    if (pickArtist) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to artist") },
        text = {
            Column {
                song.artistRefs.forEach { a ->
                    Text(a.name, Modifier.fillMaxWidth().clickable { onDismiss(); goArtist(a.id, a.name) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } }
    )
}
