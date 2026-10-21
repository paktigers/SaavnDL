package com.saavndl.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.saavndl.app.data.LibrarySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.ui.AppRoot
import com.saavndl.app.ui.AppVM
import com.saavndl.app.ui.SaavnTheme

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incoming = extractLink(intent)
        setContent {
            val s by SettingsStore.state.collectAsState()
            val dir = if (s.lang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides dir) {
                SaavnTheme(s.theme) {
                    NotificationPermission()
                    AppRoot(vm, incoming) { incoming = null }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // keep the library in sync with the phone's storage (deleted files disappear, restored downloads appear)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                LibrarySync.removeMissing(applicationContext)
                if (LibrarySync.hasPermission(applicationContext)) LibrarySync.scanSaavnFolder(applicationContext)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractLink(intent)?.let { incoming = it }
    }

    private fun extractLink(i: Intent?): String? {
        val raw = i?.dataString ?: i?.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://\\S*(?:jiosaavn|spotify)\\.com\\S*").find(raw)?.value
    }
}

@Composable
private fun NotificationPermission() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
