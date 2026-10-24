package com.saavndl.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.player.PlayerController
import com.saavndl.app.player.Track

private data class QItem(val key: String, val track: Track)

/**
 * The play queue: what plays now and what comes next. "Next in queue" can be re-ordered with the handle,
 * songs can be removed, or tapped to jump straight to them.
 */
@Composable
fun PlayQueueScreen(onBack: () -> Unit) {
    val queue by PlayerController.queueList.collectAsState()
    val idx by PlayerController.queueIndex.collectAsState()
    val cur by PlayerController.current.collectAsState()
    val playing by PlayerController.playing.collectAsState()
    val upNext = remember(queue, idx) { if (idx + 1 <= queue.size) queue.drop(idx + 1) else emptyList() }
    val source = remember(upNext) { upNext.mapIndexed { i, t -> QItem("${t.id}#${upNext.take(i).count { it.id == t.id }}", t) } }
    val listState = rememberLazyListState()
    val rs = rememberReorderState(source, listState) { it.key }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Queue", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }
        val now = cur
        if (now == null) {
            EmptyState(Icons.Filled.QueueMusic, "Nothing is playing", "Play a song, then use \"Play next\" or \"Add to queue\" to line up more songs.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "now-h") { Text("Now playing", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item(key = "now") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(now.image, 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(now.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { PlayerController.toggle() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item(key = "next-h") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Next in queue", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (source.isNotEmpty()) TextButton(onClick = { PlayerController.clearUpNext() }) { Text("Clear queue") }
                }
            }
            if (source.isEmpty()) item(key = "empty") {
                Text(
                    "Nothing is lined up. Use \"Play next\" or \"Add to queue\" in the ⋮ menu of a song.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rs.items, key = { it.key }) { q ->
                val dragging = rs.dragKey == q.key
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .then(rs.visual(q.key))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .clickable { PlayerController.playAt(idx + 1 + rs.items.indexOf(q)) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(q.track.image, 50.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(q.track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { PlayerController.removeUpNext(rs.items.indexOf(q)) }) { Icon(Icons.Filled.Close, "Remove from queue") }
                    Box(Modifier.size(44.dp).then(rs.handle(q.key) { list -> PlayerController.setUpNext(list.map { it.track }) }), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DragHandle, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "space") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
