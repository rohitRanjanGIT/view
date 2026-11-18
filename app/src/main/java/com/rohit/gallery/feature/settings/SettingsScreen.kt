package com.rohit.gallery.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rohit.gallery.core.data.prefs.ThemeMode

/**
 * Look, viewer and delete-prompt options. Changes apply immediately (no Save button), and the "skip
 * confirmation" row re-checks the system permission on every resume because the user flips it in another screen.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val canManageMedia by viewModel.canManageMedia.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshManageMedia() }

    Column(Modifier.fillMaxSize()) {
        TitleBar("Settings", onBack)

        // Settings load in a few ms; showing nothing meanwhile avoids a flash of default values.
        val current = settings ?: return@Column
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            SectionTitle("Look")
            ThemeMode.entries.forEach { mode ->
                RadioRow(mode.label, selected = current.theme == mode, onSelect = { viewModel.setTheme(mode) })
            }
            SwitchRow(
                title = "Wallpaper colours",
                subtitle = "Use colours from your wallpaper",
                checked = current.wallpaperColors,
                onCheckedChange = viewModel::setWallpaperColors,
            )

            SectionTitle("Viewer")
            SwitchRow("Start videos muted", null, current.startMuted, viewModel::setStartMuted)
            SwitchRow(
                title = "Play motion photos automatically",
                subtitle = "Play the short clip once when you open one",
                checked = current.motionAutoplay,
                onCheckedChange = viewModel::setMotionAutoplay,
            )
            SwitchRow(
                title = "Show Ultra HDR photos in HDR",
                subtitle = "Brighter highlights on photos taken in HDR",
                checked = current.ultraHdr,
                onCheckedChange = viewModel::setUltraHdr,
            )

            SectionTitle("Deleting and favourites")
            InfoRow(
                title = "Skip confirmation pop-ups",
                text = if (canManageMedia) {
                    "On: no pop-up for trash and favourites. Items still go to Trash first. Tap to change."
                } else {
                    "Off: Android asks before each trash or favourite. Tap to allow Gallery to skip it."
                },
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:" + context.packageName)),
                        )
                    }
                },
            )

            SectionTitle("About")
            InfoRow("Version", viewModel.versionName.ifEmpty { "Unknown" })
            InfoRow("Privacy", "Gallery has no internet permission. Your photos and settings never leave this phone.")
            LicencesRow()
        }
    }
}

@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The row handles the click, so the switch itself is display-only.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun InfoRow(title: String, text: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LicencesRow() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    InfoRow(
        title = "Open-source licences",
        text = if (expanded) "Tap to hide" else "Tap to show the libraries Gallery is built with",
        onClick = { expanded = !expanded },
    )
    if (expanded) {
        Libraries.forEach { name ->
            Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 24.dp, top = 4.dp, bottom = 4.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text("Apache License 2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val Libraries = listOf(
    "Jetpack Compose",
    "Material 3",
    "AndroidX (Navigation, Lifecycle, Room, DataStore, Activity)",
    "Hilt / Dagger",
    "Kotlin coroutines and serialization",
    "Coil",
    "Telephoto (Saket Narayan)",
    "Media3 / ExoPlayer",
)
