package com.saavndl.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.saavndl.app.data.LibrarySync
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.data.Stores
import com.saavndl.app.data.tr
import com.saavndl.app.download.Downloads
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppVM, incoming: String?, onConsumed: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingAudio by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val cb = pendingAudio
        pendingAudio = null
        cb?.invoke(ok)
    }
    val host = remember {
        Host(
            snack = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } },
            push = { r -> if (stack.lastOrNull() != r) stack.add(r) },
            withAudioPermission = { cb ->
                if (LibrarySync.hasPermission(ctx)) cb(true)
                else { pendingAudio = cb; audioLauncher.launch(LibrarySync.permissionName()) }
            }
        )
    }
    var askRestore by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // After a reinstall the downloaded files are still on the phone but invisible to the new install until audio access is granted.
        if (!LibrarySync.hasPermission(ctx) && !SettingsStore.restoreAsked() && Stores.library.items.value.isEmpty()) askRestore = true
    }
    val tasks by Downloads.tasks.collectAsState()
    val activeCount = tasks.count { it.active }

    LaunchedEffect(incoming) {
        if (incoming != null) { openLink(host, incoming); onConsumed() }
    }
    LaunchedEffect(Unit) { vm.checkUpdate(ctx, false) }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    CompositionLocalProvider(LocalHost provides host) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    MiniPlayer()
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        NavigationBarItem(selected = tab == 0, onClick = { stack.clear(); tab = 0 },
                            icon = { Icon(Icons.Filled.Search, null) }, label = { Text(tr("Discover")) })
                        NavigationBarItem(selected = tab == 1, onClick = { stack.clear(); tab = 1 },
                            icon = { Icon(Icons.Filled.LibraryMusic, null) }, label = { Text(tr("Library")) })
                        NavigationBarItem(selected = tab == 2, onClick = { stack.clear(); tab = 2 },
                            icon = {
                                BadgedBox(badge = { if (activeCount > 0) Badge { Text("$activeCount") } }) {
                                    Icon(Icons.Filled.CloudDownload, null)
                                }
                            }, label = { Text(tr("Downloads")) })
                        NavigationBarItem(selected = tab == 3, onClick = { stack.clear(); tab = 3 },
                            icon = { Icon(Icons.Filled.Settings, null) }, label = { Text(tr("Settings")) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (stack.isNotEmpty()) {
                    val r = stack.last()
                    val back = { stack.removeAt(stack.lastIndex); Unit }
                    key(r) {
                        when (r) {
                            is Route.NowPlaying -> NowPlayingScreen(back)
                            is Route.Playlist -> PlaylistScreen(r.id, back)
                            is Route.SpotifyImport -> SpotifyImportScreen(r.link, vm, back)
                            is Route.ImportDevice -> ImportDeviceScreen(back)
                            else -> DetailScreen(r, back)
                        }
                    }
                } else when (tab) {
                    0 -> SearchScreen(vm)
                    1 -> LibraryScreen()
                    2 -> QueueScreen()
                    else -> SettingsScreen(vm)
                }
            }
        }

        if (askRestore) {
            AlertDialog(
                onDismissRequest = { askRestore = false; SettingsStore.markRestoreAsked() },
                title = { Text("Restore your downloaded songs?") },
                text = { Text("If you downloaded songs with SaavnDL before, they are still in Music/SaavnDL. Allow access to the music on your phone and they will be added back to your library automatically.") },
                confirmButton = {
                    TextButton(onClick = {
                        askRestore = false
                        SettingsStore.markRestoreAsked()
                        host.withAudioPermission { ok ->
                            if (ok) scope.launch {
                                val n = LibrarySync.scanSaavnFolder(ctx)
                                host.snack(if (n > 0) "Restored $n songs" else "No previous downloads found")
                            }
                        }
                    }) { Text("Allow") }
                },
                dismissButton = { TextButton(onClick = { askRestore = false; SettingsStore.markRestoreAsked() }) { Text("Not now") } }
            )
        }
        vm.update?.let { u ->
            AlertDialog(
                onDismissRequest = { vm.update = null },
                title = { Text("Update ${u.tag} available") },
                text = { Text(u.notes.ifBlank { "A new version of SaavnDL is ready to download." }) },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        vm.update = null
                    }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { vm.update = null }) { Text("Later") } }
            )
        }
    }
}
