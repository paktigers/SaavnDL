package com.saavndl.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.LaunchedEffect
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
import com.saavndl.app.data.Card
import com.saavndl.app.data.Coll
import com.saavndl.app.data.Kind
import com.saavndl.app.data.Recents
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.SpotifyApi
import com.saavndl.app.data.Stores
import com.saavndl.app.data.linkKind
import com.saavndl.app.data.tr

fun kindIcon(k: Kind) = when (k) {
    Kind.ALBUMS -> Icons.Filled.Album
    Kind.PLAYLISTS -> Icons.Filled.PlaylistPlay
    Kind.ARTISTS -> Icons.Filled.Person
    else -> Icons.Filled.Search
}

fun openLink(host: Host, link: String): Boolean {
    if (SpotifyApi.isSpotifyLink(link)) { host.push(Route.SpotifyImport(link)); return true }
    val k = linkKind(link)
    if (k == null) { host.snack("This doesn't look like a JioSaavn or Spotify playlist/album/song link"); return false }
    host.push(Route.Detail(k, null, link, "Link", ""))
    return true
}

private fun isLink(q: String) = q.contains("jiosaavn.com") || SpotifyApi.isSpotifyLink(q)

private val HOME_LANGS = listOf(
    "Hindi" to "hindi", "English" to "english", "Punjabi" to "punjabi", "Tamil" to "tamil",
    "Telugu" to "telugu", "Bengali" to "bengali", "Marathi" to "marathi", "Bhojpuri" to "bhojpuri"
)

@Composable
fun SearchScreen(vm: AppVM) {
    val host = LocalHost.current
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val recents by Recents.items.collectAsState()
    val favs by Stores.favorites.items.collectAsState()
    val settings by SettingsStore.state.collectAsState()

    LaunchedEffect(favs.size) { if (favs.isNotEmpty()) vm.loadForYou() }
    LaunchedEffect(Unit) { vm.loadLatest() }

    fun submit() {
        focus.clearFocus()
        val q = vm.query.trim()
        if (isLink(q)) openLink(host, q) else vm.search()
    }
    fun clipboardText(): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    }

    val typing = vm.query.isNotBlank() && vm.query.trim() != vm.submitted && !isLink(vm.query.trim())
    val showSuggestions = typing && (vm.sugSongs.isNotEmpty() || vm.sugColls.isNotEmpty() || vm.sugLoading)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .18f), Color.Transparent)))
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
        ) {
            Text(tr("Discover"), style = MaterialTheme.typography.headlineMedium)
            Text(tr("Search, preview and download — with full tags"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.onQueryChange(it) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(tr("Songs, albums, artists or paste a link")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Clear, "Clear") }
                    else IconButton(onClick = {
                        val t = clipboardText()
                        if (t.isNotBlank()) vm.onQueryChange(t) else host.snack("Clipboard is empty")
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
                        onClick = { vm.kind = k; if (vm.query.isNotBlank() && !isLink(vm.query)) vm.search() },
                        label = { Text(tr(k.label)) },
                        leadingIcon = { Icon(kindIcon(k), null, Modifier.size(18.dp)) }
                    )
                }
            }
        }

        val langs = vm.songs.map { it.language.replaceFirstChar { c -> c.uppercase() } }.filter { it.isNotBlank() }.distinct()
        val shownSongs = if (vm.langFilter == "All") vm.songs else vm.songs.filter { it.language.equals(vm.langFilter, true) }

        if (showSuggestions) {
            // live results while typing: posters, scrollable, tap to preview / open
            SongsList(
                songs = vm.sugSongs, modifier = Modifier.weight(1f),
                header = {
                    Column {
                        if (vm.sugLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { submit() }.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("See all results for \"${vm.query.trim()}\"", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        if (vm.sugSongs.isNotEmpty()) Text(tr("Songs"), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                footer = {
                    Column {
                        if (vm.sugColls.isNotEmpty()) Text("Albums and playlists", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        vm.sugColls.forEach { c -> CollRow(c) { host.push(Route.Detail(c.kind, c.id, null, c.name, c.image)) } }
                    }
                }
            )
        } else if (vm.loading && vm.songs.isEmpty() && vm.colls.isEmpty()) {
            SkeletonList()
        } else if (vm.error != null && vm.songs.isEmpty() && vm.colls.isEmpty()) {
            ErrorState(vm.error ?: "") { vm.search() }
        } else if (vm.kind == Kind.SONGS && vm.songs.isNotEmpty() && vm.submitted.isNotBlank()) {
            SongsList(
                songs = shownSongs, modifier = Modifier.weight(1f),
                header = if (langs.size > 1) ({
                    LazyRow(Modifier.padding(bottom = 6.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((listOf("All") + langs)) { l -> FilterChip(selected = vm.langFilter == l, onClick = { vm.langFilter = l }, label = { Text(l) }) }
                    }
                }) else null,
                footer = { if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) },
                onEnd = { vm.loadMore() }
            )
        } else if (vm.kind != Kind.SONGS && vm.colls.isNotEmpty() && vm.submitted.isNotBlank()) {
            LazyColumn(Modifier.weight(1f)) {
                items(vm.colls) { c -> CollRow(c) { host.push(Route.Detail(c.kind, c.id, null, c.name, c.image)) } }
            }
        } else if (vm.searched && vm.submitted.isNotBlank() && vm.query.trim() == vm.submitted) {
            EmptyState(Icons.Filled.Search, tr("No results"), "Try another spelling or a different category.")
        } else {
            val homeHeader: @Composable () -> Unit = {
                Column {
                    Home(
                        recents, onPick = { vm.query = it; vm.search() },
                        onPasteLink = {
                            val t = clipboardText()
                            if (isLink(t)) { vm.query = t; openLink(host, t) } else host.snack("Copy a JioSaavn or Spotify link first")
                        },
                        onSpotify = { host.push(Route.SpotifyImport("")) }
                    )
                    LatestSection(vm, settings.homeLang)
                    if (vm.forYou.isNotEmpty()) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp)) {
                            Text(tr("For you"), style = MaterialTheme.typography.titleMedium)
                            Text("Because you liked ${vm.forYouFrom}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SongsList(songs = vm.forYou, modifier = Modifier.weight(1f), header = homeHeader)
        }
    }
}

@Composable
private fun LatestSection(vm: AppVM, lang: String) {
    val host = LocalHost.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Latest releases", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.loadLatest(true) }) { Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HOME_LANGS) { (label, v) -> FilterChip(selected = lang == v, onClick = { vm.setHomeLang(v) }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            vm.latestLoading && vm.latest.isEmpty() -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(5) { Column { Skeleton(Modifier.size(130.dp)); Spacer(Modifier.height(6.dp)); Skeleton(Modifier.width(100.dp).height(12.dp)) } }
            }
            vm.latest.isEmpty() -> Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Could not load the latest releases.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.loadLatest(true) }) { Text("Retry") }
            }
            else -> vm.latest.forEach { sec ->
                Text(sec.title, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sec.cards) { c -> CardItem(c) { host.push(Route.Detail(c.kind, c.id, c.link.ifBlank { null }, c.name, c.image)) } }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CardItem(c: Card, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(bottom = 4.dp)) {
        Cover(c.image, 132.dp, radius = 14.dp)
        Spacer(Modifier.height(6.dp))
        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        if (c.subtitle.isNotBlank()) Text(c.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Home(recents: List<String>, onPick: (String) -> Unit, onPasteLink: () -> Unit, onSpotify: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (recents.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Recent searches"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { Recents.clear() }) { Text(tr("Clear")) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recents) { r -> AssistChip(onClick = { onPick(r) }, label = { Text(r, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
            }
            Spacer(Modifier.height(14.dp))
        }
        LinkCard("Open a JioSaavn link", "Tap to paste a song, album, playlist or artist link", onPasteLink)
        Spacer(Modifier.height(8.dp))
        LinkCard("Import a Spotify playlist", "Match its songs on JioSaavn, then download them", onSpotify)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LinkCard(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val sub = listOf(c.kind.label.removeSuffix("s"), c.subtitle).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
