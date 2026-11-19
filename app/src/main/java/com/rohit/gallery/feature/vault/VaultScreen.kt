package com.rohit.gallery.feature.vault

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.frostedChrome
import com.rohit.gallery.core.ui.icons.GalleryIcons
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.MediaListScreen

/**
 * Locked: photos and videos moved into the app's private storage. Opens only after fingerprint, face or the phone's
 * screen lock; locks again as soon as it is left or the app is hidden. Can't be screenshotted or recorded.
 * [onRelock] closes anything still showing a locked photo (the viewer layer) when it locks.
 */
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    onRelock: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    SecureWindow()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val latestOnRelock by rememberUpdatedState(onRelock)
    // Leaving the screen or the app (also the screen going off) locks it again.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.lockNow()
        latestOnRelock()
    }

    if (unlocked) {
        VaultContent(viewModel, onBack, onOpenMedia)
    } else {
        LockedGate(onBack = onBack, onUnlocked = viewModel::onUnlocked)
    }
}

@Composable
private fun LockedGate(onBack: () -> Unit, onUnlocked: () -> Unit) {
    val activity = LocalActivity.current
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var noScreenLock by rememberSaveable { mutableStateOf(false) }
    fun ask() {
        val a = activity ?: return
        promptVaultUnlock(a) { result ->
            when (result) {
                VaultAuthResult.Success -> onUnlocked()
                VaultAuthResult.NoScreenLock -> noScreenLock = true
                is VaultAuthResult.Failed -> message = result.message
            }
        }
    }
    // Ask straight away; the button is there if the prompt was dismissed.
    LaunchedEffect(Unit) { ask() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Text("Locked", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                if (noScreenLock) {
                    "Set up a screen lock (PIN, pattern, password or fingerprint) in your phone's settings to use Locked."
                } else {
                    "Photos and videos here are only in this app. Other apps and file managers can't see them."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (!noScreenLock) {
                Button(onClick = ::ask, modifier = Modifier.padding(top = 24.dp)) { Text("Unlock") }
            }
        }
    }
}

@Composable
private fun VaultContent(viewModel: VaultViewModel, onBack: () -> Unit, onOpenMedia: (MediaItem) -> Unit) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val count = selection.ids.size
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmUnlockAll by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    MediaListScreen(
        title = "Locked",
        emptyText = "Nothing locked yet. Select photos, then More > Lock.",
        items = items,
        selection = selection,
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        asTimeline = true,
        titleActions = {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Unlock everything") },
                        enabled = !items.isNullOrEmpty(),
                        onClick = { menu = false; confirmUnlockAll = true },
                    )
                }
            }
        },
    ) { modifier ->
        Surface(color = Color.Transparent, modifier = modifier.fillMaxWidth().frostedChrome()) {
            Row(Modifier.navigationBarsPadding().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionItem(Icons.Filled.Share, "Share", count > 0, {
                    val chosen = viewModel.selectedItems()
                    if (chosen.isNotEmpty()) runCatching { context.startActivity(viewModel.shareIntent(chosen)) }
                }, Modifier.weight(1f))
                ActionItem(GalleryIcons.LockOpen, "Unlock", count > 0, viewModel::unlockSelected, Modifier.weight(1f))
                ActionItem(Icons.Filled.Delete, "Delete", count > 0, { confirmDelete = true }, Modifier.weight(1f))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    (if (count == 1) "This item is" else "These $count items are") +
                        " only in Locked, so this deletes the only copy. This can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteSelected() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmUnlockAll) {
        AlertDialog(
            onDismissRequest = { confirmUnlockAll = false },
            title = { Text("Unlock everything?") },
            text = { Text("Every photo and video goes back to the album it was locked from, where other apps can see it again.") },
            confirmButton = { TextButton(onClick = { confirmUnlockAll = false; viewModel.unlockAll() }) { Text("Unlock all") } },
            dismissButton = { TextButton(onClick = { confirmUnlockAll = false }) { Text("Cancel") } },
        )
    }
    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Unlocking…") },
            text = {
                LinearProgressIndicator(
                    progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}
