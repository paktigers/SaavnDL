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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.Song
import com.saavndl.app.data.Stores
import com.saavndl.app.data.tr

@Composable
fun TextInputDialog(
    title: String, label: String, initial: String = "", confirm: String = "Create",
    onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true, label = { Text(label) }) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onConfirm(v.trim()) }) { Text(tr(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val lists by Stores.playlists.items.collectAsState()
    val host = LocalHost.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(tr("New playlist"), "Name", onDismiss = { creating = false }) { name ->
            Stores.createPlaylist(name, songs)
            host.snack("Added to \"$name\"")
            onDismiss()
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("Add to playlist")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("New playlist"), color = MaterialTheme.colorScheme.primary)
                    }
                    lists.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Stores.addToPlaylist(p.id, songs)
                                host.snack("Added to \"${p.name}\"")
                                onDismiss()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } }
        )
    }
}
