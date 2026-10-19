package com.saavndl.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.download.DlStatus
import com.saavndl.app.download.DlTask
import com.saavndl.app.download.Downloads

@Composable
fun QueueScreen() {
    val ctx = LocalContext.current
    val tasks by Downloads.tasks.collectAsState()
    val active = tasks.count { it.active }
    val done = tasks.count { it.status == DlStatus.DONE || it.status == DlStatus.SKIPPED }
    val failed = tasks.count { it.status == DlStatus.FAILED || it.status == DlStatus.CANCELLED }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            Text("Downloads", style = MaterialTheme.typography.headlineMedium)
            Text("$active active · $done finished · $failed failed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tasks.isEmpty()) {
            EmptyState(Icons.Filled.CloudDownload, "Queue is empty", "Tap the download icon on any song to add it here.")
            return@Column
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active > 0) AssistChip(onClick = { Downloads.cancelAll() }, label = { Text("Cancel all") })
            if (failed > 0) AssistChip(onClick = { Downloads.retryFailed(ctx) }, label = { Text("Retry failed") })
            if (done + failed > 0) AssistChip(onClick = { Downloads.clearFinished() }, label = { Text("Clear finished") })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks.reversed(), key = { it.id }) { TaskRow(it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TaskRow(t: DlTask) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.song.thumb, 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            val color = when (t.status) {
                DlStatus.DONE -> MaterialTheme.colorScheme.primary
                DlStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val label = when (t.status) {
                DlStatus.QUEUED -> "Waiting in queue"
                else -> t.message.ifBlank { t.status.name.lowercase().replaceFirstChar { it.uppercase() } }
            }
            Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = color)
            if (t.status == DlStatus.DOWNLOADING || t.status == DlStatus.PROCESSING) {
                Spacer(Modifier.height(6.dp))
                if (t.status == DlStatus.PROCESSING) LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
                else LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = Color.Transparent.copy(alpha = .15f))
            }
        }
        when (t.status) {
            DlStatus.DONE, DlStatus.SKIPPED -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(24.dp))
            DlStatus.FAILED, DlStatus.CANCELLED -> {
                if (t.status == DlStatus.FAILED) Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                IconButton(onClick = { Downloads.retry(ctx, t.id) }) { Icon(Icons.Filled.Refresh, "Retry") }
            }
            else -> IconButton(onClick = { Downloads.cancel(t.id) }) { Icon(Icons.Filled.Cancel, "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
