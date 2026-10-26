package com.saavndl.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.saavndl.app.data.Lyrics
import com.saavndl.app.data.LyricsApi
import com.saavndl.app.data.Stores
import com.saavndl.app.data.fmtDur
import com.saavndl.app.data.tr
import com.saavndl.app.download.Downloads
import com.saavndl.app.player.PlayerController
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(onBack: () -> Unit) {
    val track by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    val loading by PlayerController.loading.collectAsState()
    val pos by PlayerController.position.collectAsState()
    val dur by PlayerController.duration.collectAsState()
    val shuffle by PlayerController.shuffle.collectAsState()
    val repeat by PlayerController.repeat.collectAsState()
    val sleepEnd by PlayerController.sleepEnd.collectAsState()
    val afterTrack by PlayerController.sleepAfterTrack.collectAsState()
    val qSize by PlayerController.queueSize.collectAsState()
    val favs by Stores.favorites.items.collectAsState()
    val host = LocalHost.current
    val ctx = LocalContext.current

    val tr0 = track
    if (tr0 == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val song = tr0.song
    var showLyrics by remember { mutableStateOf(false) }
    var lyrics by remember(tr0.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsBusy by remember(tr0.id) { mutableStateOf(false) }
    var lyricsMissing by remember(tr0.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<List<com.saavndl.app.data.Song>?>(null) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var lyricsOffset by remember(tr0.id) { mutableIntStateOf(0) }

    LaunchedEffect(tr0.id, showLyrics) {
        if (showLyrics && lyrics == null && !lyricsMissing) {
            lyricsBusy = true
            val secs = if (dur > 0) dur / 1000 else tr0.duration
            lyrics = LyricsApi.fetch(tr0.title, tr0.artist, tr0.album, secs)
            lyricsMissing = lyrics == null
            lyricsBusy = false
        }
    }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "Close", Modifier.size(30.dp)) }
            Text(tr("Now playing"), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.Bedtime, tr("Sleep timer"), tint = if (sleepEnd != null || afterTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(15, 30, 45, 60).forEach { m ->
                        DropdownMenuItem(text = { Text("$m min") }, onClick = { PlayerController.setSleep(m); menu = false })
                    }
                    DropdownMenuItem(text = { Text("End of track") }, onClick = { PlayerController.setSleep(null, true); menu = false })
                    DropdownMenuItem(text = { Text("Off") }, onClick = { PlayerController.setSleep(null); menu = false })
                }
            }
        }
        val remaining = sleepEnd?.let { ((it - now) / 60000).coerceAtLeast(0) + 1 }
        if (remaining != null) Text("${tr("Sleep timer")}: $remaining min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        else if (afterTrack) Text("${tr("Sleep timer")}: end of track", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            if (!showLyrics) {
                Box(Modifier.fillMaxWidth(0.88f).aspectRatio(1f).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val url = tr0.art.ifBlank { tr0.image }
                    if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            } else {
                LyricsPanel(lyrics, lyricsBusy, lyricsMissing, pos, lyricsOffset) { lyricsOffset += it }
            }
        }

        Text(tr0.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(tr0.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

        val maxV = maxOf(dur, 1).toFloat()
        Slider(
            value = drag ?: pos.toFloat().coerceIn(0f, maxV),
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { PlayerController.seekTo(it.toInt()) }; drag = null },
            valueRange = 0f..maxV
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtDur(((drag ?: pos.toFloat()) / 1000).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmtDur(dur / 1000), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { PlayerController.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { PlayerController.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(34.dp)) }
            FilledIconButton(onClick = { PlayerController.toggle() }, modifier = Modifier.size(68.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(38.dp))
            }
            IconButton(onClick = { PlayerController.next() }) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(34.dp)) }
            IconButton(onClick = { PlayerController.cycleRepeat() }) {
                Icon(if (repeat == PlayerController.REPEAT_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                    tint = if (repeat != PlayerController.REPEAT_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (song != null) {
                val fav = favs.any { it.id == song.id }
                IconButton(onClick = { Stores.toggleFavorite(song) }) {
                    Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", tint = if (fav) androidx.compose.ui.graphics.Color(0xFFFF5C8A) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Downloads.enqueue(ctx, listOf(song)); host.snack("Added to queue") }) {
                    Icon(Icons.Filled.Download, "Download", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { addTo = listOf(song) }) { Icon(Icons.Filled.PlaylistAdd, "Add to playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = { host.push(Route.PlayQueue) }) { Icon(Icons.Filled.QueueMusic, "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = { showLyrics = !showLyrics }) {
                Icon(Icons.Filled.Notes, tr("Lyrics"), tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("$qSize in queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
    }
    addTo?.let { AddToPlaylistDialog(it) { addTo = null } }
}

@Composable
private fun LyricsPanel(lyrics: Lyrics?, busy: Boolean, missing: Boolean, pos: Int, offset: Int, onShift: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        val synced = lyrics != null && lyrics.synced.isNotEmpty()
        if (lyrics != null && !busy) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (synced) "Time-synced lyrics" else "Plain lyrics (no timing available)",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (synced) {
                    TextButton(onClick = { onShift(-500) }) { Text("−0.5s") }
                    Text("${offset / 1000.0}s", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { onShift(500) }) { Text("+0.5s") }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator()
                missing || lyrics == null -> Text("No lyrics found for this song", color = MaterialTheme.colorScheme.onSurfaceVariant)
                lyrics.instrumental && lyrics.plain.isBlank() -> Text("Instrumental", color = MaterialTheme.colorScheme.onSurfaceVariant)
                synced -> {
                    val idx = lyrics!!.synced.indexOfLast { it.ms + offset <= pos }
                    val state = rememberLazyListState()
                    LaunchedEffect(idx) { if (idx >= 0) state.animateScrollToItem((idx - 2).coerceAtLeast(0)) }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = state) {
                        itemsIndexed(lyrics!!.synced) { i, l ->
                            Text(
                                l.text.ifBlank { "♪" },
                                Modifier.fillMaxWidth().clickable { PlayerController.seekTo((l.ms + offset).toInt().coerceAtLeast(0)) }.padding(vertical = 8.dp),
                                fontSize = if (i == idx) 20.sp else 16.sp,
                                fontWeight = if (i == idx) FontWeight.Bold else FontWeight.Normal,
                                color = if (i == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> Text(
                    lyrics.plain,
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
