package com.saavndl.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.Coll
import com.saavndl.app.data.Kind
import com.saavndl.app.data.Recents
import com.saavndl.app.data.linkKind

fun kindIcon(k: Kind) = when (k) {
    Kind.ALBUMS -> Icons.Filled.Album
    Kind.PLAYLISTS -> Icons.Filled.PlaylistPlay
    Kind.ARTISTS -> Icons.Filled.Person
    else -> Icons.Filled.Search
}

fun openLink(host: Host, link: String): Boolean {
    val k = linkKind(link)
    if (k == null) { host.snack("This doesn't look like a JioSaavn song, album, playlist or artist link"); return false }
    host.push(Route.Detail(k, null, link, "Link", ""))
    return true
}

@Composable
fun SearchScreen(vm: AppVM) {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val recents by Recents.items.collectAsState()

    fun submit() {
        focus.clearFocus()
        val q = vm.query.trim()
        if (q.contains("jiosaavn.com")) openLink(host, q) else vm.search()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .18f), Color.Transparent)))
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
        ) {
            Text("Discover", style = MaterialTheme.typography.headlineMedium)
            Text("Search, preview and download — with full tags", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Songs, albums, artists or paste a link") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                    else IconButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (t.isNotBlank()) vm.query = t else host.snack("Clipboard is empty")
                    }) { Icon(Icons.Filled.ContentPaste, "Paste") }
                },
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            LazyRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Kind.entries.toList()) { k ->
                    FilterChip(
                        selected = vm.kind == k,
                        onClick = { vm.kind = k; if (vm.query.isNotBlank() && !vm.query.contains("jiosaavn.com")) vm.search() },
                        label = { Text(k.label) },
                        leadingIcon = { Icon(kindIcon(k), null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
        if (vm.loading && vm.songs.isEmpty() && vm.colls.isEmpty()) {
            SkeletonList()
        } else if (vm.error != null && vm.songs.isEmpty() && vm.colls.isEmpty()) {
            ErrorState(vm.error ?: "") { vm.search() }
        } else if (vm.kind == Kind.SONGS && vm.songs.isNotEmpty()) {
            SongsList(
                songs = vm.songs, modifier = Modifier.weight(1f),
                footer = { if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) },
                onEnd = { vm.loadMore() }
            )
        } else if (vm.kind != Kind.SONGS && vm.colls.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f)) {
                items(vm.colls) { c -> CollRow(c) { host.push(Route.Detail(c.kind, c.id, null, c.name, c.image)) } }
            }
        } else if (vm.searched) {
            EmptyState(Icons.Filled.Search, "No results", "Try another spelling or a different category.")
        } else {
            Home(recents, onPick = { vm.query = it; vm.search() }, onPaste = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                if (t.contains("jiosaavn.com")) { vm.query = t; openLink(host, t) } else host.snack("Copy a JioSaavn link first")
            })
        }
    }
}

@Composable
private fun Home(recents: List<String>, onPick: (String) -> Unit, onPaste: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (recents.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { Recents.clear() }) { Text("Clear") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recents) { r -> AssistChip(onClick = { onPick(r) }, label = { Text(r, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
            }
            Spacer(Modifier.height(18.dp))
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { onPaste() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Open a JioSaavn link", style = MaterialTheme.typography.titleSmall)
                Text("Tap to paste a song, album, playlist or artist link from your clipboard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Tips: long-press a song to select several · tap a song to preview · use \"Share\" from the JioSaavn app to open links here.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CollRow(c: Coll, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(c.image, 58.dp, radius = if (c.kind == Kind.ARTISTS) 29.dp else 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (c.subtitle.isNotBlank()) Text(c.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
