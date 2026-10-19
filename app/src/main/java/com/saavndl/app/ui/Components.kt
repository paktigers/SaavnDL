package com.saavndl.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saavndl.app.data.Kind
import com.saavndl.app.data.Song
import com.saavndl.app.data.Stores
import com.saavndl.app.data.compactNum
import com.saavndl.app.data.fmtDur
import com.saavndl.app.download.Downloads
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.toTrack

// ------------------------------------------------------------------ host / routes
sealed class Route {
    data class Detail(val kind: Kind, val id: String?, val link: String?, val title: String, val image: String) : Route()
    data class Similar(val song: Song) : Route()
}

class Host(val snack: (String) -> Unit, val push: (Route) -> Unit)

val LocalHost = staticCompositionLocalOf<Host> { error("Host not provided") }

class SelectionState {
    val ids = mutableStateListOf<Int>()
    val active get() = ids.isNotEmpty()
    fun toggle(i: Int) { if (!ids.remove(i)) ids.add(i) }
    fun clear() = ids.clear()
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

// ------------------------------------------------------------------ small pieces
@Composable
fun Cover(url: String, size: Dp, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), modifier = Modifier.size(size / 2.2f))
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(
        .35f, .85f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a"
    )
    Box(modifier.alpha(a).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun SkeletonList(count: Int = 8) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skeleton(Modifier.size(54.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.width(190.dp).height(14.dp))
                    Skeleton(Modifier.width(120.dp).height(11.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

// ------------------------------------------------------------------ song row
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song, selected: Boolean, isCurrent: Boolean, playing: Boolean, fav: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, onDownload: () -> Unit, onFav: () -> Unit,
    onInfo: () -> Unit, onSimilar: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Cover(song.thumb, 54.dp)
            if (selected) Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            } else if (isCurrent) Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(.45f)), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Filled.GraphicEq else Icons.Filled.Pause, null, tint = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            val sub = listOf(song.artists, song.album).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmtDur(song.duration) + if (song.year.isNotBlank()) " · ${song.year}" else "", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
        }
        IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "Download", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onFav) {
            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                tint = if (fav) Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Song info") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { menu = false; onInfo() })
                DropdownMenuItem(text = { Text("Similar songs") }, leadingIcon = { Icon(Icons.Filled.Layers, null) }, onClick = { menu = false; onSimilar() })
                DropdownMenuItem(text = { Text("Share link") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onCopy() })
            }
        }
    }
}

@Composable
fun SongInfoDialog(s: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(s.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fun row(k: String, v: String) = if (v.isNotBlank()) k to v else null
                listOfNotNull(
                    row("Artists", s.artists), row("Album", s.album), row("Year", s.year),
                    row("Language", s.language.replaceFirstChar { it.uppercase() }), row("Duration", fmtDur(s.duration)),
                    row("Label", s.label), row("Plays", if (s.playCount > 0) compactNum(s.playCount.toString()) else ""),
                    row("Qualities", s.downloads.joinToString(", ") { it.quality })
                ).forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    )
}

// ------------------------------------------------------------------ list with selection + paging
@Composable
fun SongsList(
    songs: List<Song>,
    modifier: Modifier = Modifier,
    sub: String = "",
    numbered: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onEnd: (() -> Unit)? = null
) {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val sel = remember(songs.size) { SelectionState() }
    val favs by Stores.favorites.items.collectAsState()
    val cur by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    var info by remember { mutableStateOf<Song?>(null) }
    val state = rememberLazyListState()
    val lastVisible by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(lastVisible, songs.size) {
        if (onEnd != null && songs.isNotEmpty() && lastVisible >= songs.size - 2) onEnd()
    }

    fun download(idx: List<Int>) {
        val list = idx.map { songs[it] }
        val pos = if (numbered) idx.map { it + 1 } else null
        Downloads.enqueue(ctx, list, sub, pos, if (numbered) songs.size else 0)
        host.snack(if (list.size == 1) "Added to queue" else "Added ${list.size} songs to queue")
    }

    Column(modifier) {
        AnimatedVisibility(sel.active) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { sel.clear() }) { Icon(Icons.Filled.Close, "Cancel selection") }
                    Text("${sel.ids.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { sel.ids.clear(); sel.ids.addAll(songs.indices.toList()) }) { Icon(Icons.Filled.SelectAll, "Select all") }
                    IconButton(onClick = { download(sel.ids.sorted()); sel.clear() }) { Icon(Icons.Filled.Download, "Download selected") }
                }
            }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            if (header != null) item(key = "header") { header() }
            itemsIndexed(songs, key = { i, s -> "${s.id}_$i" }) { i, s ->
                SongRow(
                    song = s, selected = i in sel.ids, isCurrent = cur?.id == s.id, playing = playing,
                    fav = favs.any { it.id == s.id },
                    onClick = {
                        if (sel.active) sel.toggle(i)
                        else if (cur?.id == s.id) PlayerController.toggle()
                        else PlayerController.playQueue(songs.map { it.toTrack() }, i)
                    },
                    onLong = { sel.toggle(i) },
                    onDownload = { download(listOf(i)) },
                    onFav = { Stores.toggleFavorite(s) },
                    onInfo = { info = s },
                    onSimilar = { host.push(Route.Similar(s)) },
                    onShare = { shareText(ctx, "${s.name} - ${s.artists}\n${s.url}") },
                    onCopy = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("link", s.url)); host.snack("Link copied")
                    }
                )
            }
            if (footer != null) item(key = "footer") { footer() }
            item(key = "space") { Spacer(Modifier.height(24.dp)) }
        }
    }
    info?.let { SongInfoDialog(it) { info = null } }
}

// ------------------------------------------------------------------ mini player
@Composable
fun MiniPlayer() {
    val t by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    val loading by PlayerController.loading.collectAsState()
    val pos by PlayerController.position.collectAsState()
    val dur by PlayerController.duration.collectAsState()
    AnimatedVisibility(t != null) {
        val tr = t
        if (tr != null) Surface(
            tonalElevation = 6.dp, shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(tr.image, 42.dp, radius = 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(tr.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { PlayerController.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
                    if (loading) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) }
                    else IconButton(onClick = { PlayerController.toggle() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = { PlayerController.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
                    IconButton(onClick = { PlayerController.stop() }) { Icon(Icons.Filled.Close, "Close") }
                }
                LinearProgressIndicator(
                    progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
