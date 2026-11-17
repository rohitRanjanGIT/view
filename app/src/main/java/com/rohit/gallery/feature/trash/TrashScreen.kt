package com.rohit.gallery.feature.trash

import android.app.Activity
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.ui.components.MediaThumbnail
import com.rohit.gallery.feature.selection.ActionItem
import com.rohit.gallery.feature.selection.SelectionCheck
import kotlinx.coroutines.launch

private const val COLUMNS = 4

/** Recycle bin: restore items, or delete them permanently after an explicit confirmation. */
@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

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

    BackHandler(enabled = selection.isSelecting) { selection.clear() }

    val cellPx = LocalWindowInfo.current.containerSize.width / COLUMNS
    val count = selection.ids.size

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (selection.isSelecting) selection.clear() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        if (count == 0) "Trash" else "$count selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    val all = items.orEmpty()
                    if (all.isNotEmpty()) TextButton(onClick = { selection.selectAll(all) }) { Text("All") }
                }
            }

            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Trash is empty", style = MaterialTheme.typography.titleMedium)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(COLUMNS) }) {
                        Text(
                            "Items here are deleted automatically after 30 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(list, key = { it.id }) { item ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clickable { selection.toggle(item.id) },
                        ) {
                            MediaThumbnail(item, cellPx, Modifier.fillMaxSize())
                            if (selection.isSelecting) {
                                SelectionCheck(item.id in selection.ids, Modifier.align(Alignment.TopStart).padding(6.dp))
                            }
                        }
                    }
                }
            }
        }

        if (selection.isSelecting) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier.navigationBarsPadding().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionItem(Icons.Filled.Refresh, "Restore", count > 0, { launch { viewModel.restoreRequest() } }, Modifier.weight(1f))
                    ActionItem(Icons.Filled.Delete, "Delete", count > 0, { confirmDelete = true }, Modifier.weight(1f))
                }
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
