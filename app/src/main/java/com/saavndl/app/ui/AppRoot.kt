package com.saavndl.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.saavndl.app.download.Downloads
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppVM, incoming: String?, onConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val host = remember {
        Host(
            snack = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } },
            push = { stack.add(it) }
        )
    }
    val tasks by Downloads.tasks.collectAsState()
    val activeCount = tasks.count { it.active }

    LaunchedEffect(incoming) {
        if (incoming != null) { openLink(host, incoming); onConsumed() }
    }
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
                            icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Discover") })
                        NavigationBarItem(selected = tab == 1, onClick = { stack.clear(); tab = 1 },
                            icon = { Icon(Icons.Filled.LibraryMusic, null) }, label = { Text("Library") })
                        NavigationBarItem(selected = tab == 2, onClick = { stack.clear(); tab = 2 },
                            icon = {
                                BadgedBox(badge = { if (activeCount > 0) Badge { Text("$activeCount") } }) {
                                    Icon(Icons.Filled.CloudDownload, null)
                                }
                            }, label = { Text("Downloads") })
                        NavigationBarItem(selected = tab == 3, onClick = { stack.clear(); tab = 3 },
                            icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (stack.isNotEmpty()) {
                    val r = stack.last()
                    key(r) { DetailScreen(r) { stack.removeAt(stack.lastIndex) } }
                } else when (tab) {
                    0 -> SearchScreen(vm)
                    1 -> LibraryScreen()
                    2 -> QueueScreen()
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
