package com.rohit.gallery.feature.viewer

import android.app.Activity
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rohit.gallery.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Restore and permanent-delete for the photo shown in the trash viewer. Always composed, so the result callback is never lost. */
@Stable
class TrashViewerActions internal constructor(
    private val scope: CoroutineScope,
    private val request: (suspend () -> IntentSender?) -> Unit,
    private val viewModel: ViewerViewModel,
) {
    var confirmingDelete by mutableStateOf(false)
        private set

    fun restore(item: MediaItem) = request { viewModel.restoreRequest(item) }
    fun askDelete() { confirmingDelete = true }
    fun dismiss() { confirmingDelete = false }
    fun delete(item: MediaItem) {
        confirmingDelete = false
        request { viewModel.deleteRequest(item) }
    }
}

@Composable
fun rememberTrashViewerActions(viewModel: ViewerViewModel): TrashViewerActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onTrashChanged()
    }
    return remember(viewModel) {
        TrashViewerActions(
            scope = scope,
            request = { make ->
                scope.launch {
                    val sender = make()
                    if (sender == null) {
                        Toast.makeText(context, "Couldn't start that action", Toast.LENGTH_SHORT).show()
                    } else {
                        launcher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            },
            viewModel = viewModel,
        )
    }
}

@Composable
fun TrashViewerDialog(actions: TrashViewerActions, item: MediaItem) {
    if (!actions.confirmingDelete) return
    AlertDialog(
        onDismissRequest = actions::dismiss,
        title = { Text("Delete permanently?") },
        text = { Text("This ${if (item.isVideo) "video" else "photo"} will be deleted for good. This can't be undone.") },
        confirmButton = { TextButton(onClick = { actions.delete(item) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = actions::dismiss) { Text("Cancel") } },
    )
}

