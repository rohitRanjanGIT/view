package com.rohit.gallery.feature.trash

import com.rohit.gallery.core.ui.components.frostedChrome
import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.MediaListScreen
import kotlinx.coroutines.launch

/** Recycle bin: restore items, or delete them permanently after an explicit confirmation. */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    // Coming back from the viewer after a restore or delete.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val count = selection.ids.size

    // Restore and permanent delete both go through a system confirmation; we refresh only if accepted.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onChanged()
    }
    fun launch(request: suspend () -> IntentSender?) {
        scope.launch {
            val sender = runCatching { request() }.getOrNull()
            if (sender == null) {
                Toast.makeText(context, "Couldn't start that action", Toast.LENGTH_SHORT).show()
            } else {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }

    MediaListScreen(
        title = "Trash",
        emptyText = "Trash is empty",
        items = items,
        selection = selection,
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        gridHeader = "Items here are deleted automatically after 30 days.",
    ) { modifier ->
        Surface(color = Color.Transparent, modifier = modifier.fillMaxWidth().frostedChrome()) {
            Row(
                Modifier.navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionItem(Icons.Filled.Refresh, "Restore", count > 0, { launch { viewModel.restoreRequest() } }, Modifier.weight(1f))
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
                    (if (count == 1) "This item" else "These $count items") +
                        " will be deleted for good. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; launch { viewModel.deleteRequest() } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
