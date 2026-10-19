package com.saavndl.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saavndl.app.data.AudioFormat
import com.saavndl.app.data.SaavnApi
import com.saavndl.app.data.SettingsStore
import com.saavndl.app.download.MediaSaver
import com.saavndl.app.download.Mp3Encoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun <T> ChipRow(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, v) -> FilterChip(selected = selected == v, onClick = { onPick(v) }, label = { Text(label) }) }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FormatOption(f: AudioFormat, selected: Boolean, extra: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(f.label, style = MaterialTheme.typography.titleSmall)
            Text(f.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (extra != null) Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun SettingsScreen(vm: AppVM) {
    val s by SettingsStore.state.collectAsState()
    val ctx = LocalContext.current
    val host = LocalHost.current
    val scope = rememberCoroutineScope()
    var url by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
    var apiStatus by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.batch(ctx, uri)
    }
    val mp3Ok = remember { Mp3Encoder.isAvailable() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Format, quality, theme and more", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Download format", Icons.Filled.Tune) {
            AudioFormat.entries.forEach { f ->
                FormatOption(f, s.format == f, if (f == AudioFormat.MP3 && !mp3Ok) "MP3 encoder not bundled in this build — will fall back to tagged M4A" else null) {
                    SettingsStore.update { it.copy(format = f) }
                }
            }
            Label("Audio quality")
            ChipRow(listOf("96 kbps" to "96kbps", "160 kbps" to "160kbps", "320 kbps" to "320kbps"), s.quality) { q -> SettingsStore.update { it.copy(quality = q) } }
            ToggleRow("Save cover art as .jpg", "Also stores the album art in Pictures/SaavnDL", s.saveCover) { v -> SettingsStore.update { it.copy(saveCover = v) } }
            ToggleRow("Skip existing files", "Don't download a song that is already saved", s.skipExisting) { v -> SettingsStore.update { it.copy(skipExisting = v) } }
            Label("File name")
            ChipRow(listOf("Title - Artist" to 0, "Artist - Title" to 1, "Title only" to 2), s.nameStyle) { n -> SettingsStore.update { it.copy(nameStyle = n) } }
            Label("Simultaneous downloads")
            ChipRow(listOf("1" to 1, "2" to 2, "3" to 3), s.concurrent) { n -> SettingsStore.update { it.copy(concurrent = n) } }
        }

        SectionCard("Appearance", Icons.Filled.Palette) {
            ChipRow(listOf("System" to "system", "Light" to "light", "Dark" to "dark", "AMOLED" to "amoled"), s.theme) { t -> SettingsStore.update { it.copy(theme = t) } }
            Label("Results per page")
            ChipRow(listOf("10" to 10, "20" to 20, "30" to 30, "50" to 50), s.limit) { n -> SettingsStore.update { it.copy(limit = n) } }
        }

        SectionCard("API server", Icons.Filled.Language) {
            OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    SettingsStore.update { it.copy(baseUrl = url.trim().ifBlank { "https://saavn.sumit.co" }) }
                    apiStatus = "Saved"
                }) { Text("Save") }
                OutlinedButton(onClick = {
                    apiStatus = "Testing…"
                    scope.launch {
                        apiStatus = try { SaavnApi.ping(); "Connected ✓" } catch (e: CancellationException) { throw e } catch (e: Exception) { "Failed: ${e.message}" }
                    }
                }) { Text("Test connection") }
            }
            if (apiStatus.isNotBlank()) Text(apiStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Public instances can be slow or rate-limited. You can deploy the jiosaavn-api project yourself (e.g. on Vercel) and paste its URL here.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Batch import", Icons.Filled.FileUpload) {
            Text("Pick a .txt file with one song name per line. The top result for each line is added to the queue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { picker.launch(arrayOf("text/*")) }) { Text("Choose text file") }
            if (vm.batchStatus.isNotBlank()) Text(vm.batchStatus, style = MaterialTheme.typography.bodySmall)
        }

        SectionCard("Storage", Icons.Filled.Storage) {
            Text("Songs are saved to ${MediaSaver.ROOT} (each album/playlist/artist gets its own sub-folder).", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FSaavnDL"), "vnd.android.document/directory")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { host.snack("Open your Files app → Music → SaavnDL") }
            }) { Icon(Icons.Filled.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open folder") }
        }

        SectionCard("About", Icons.Filled.Info) {
            Text("SaavnDL is an unofficial client built on the open-source jiosaavn-api project and is not affiliated with JioSaavn. Please respect the content owners' terms and use downloads for personal use only.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}
