package com.rohit.gallery.feature.favorites

import android.app.Activity
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.feature.selection.GRID_COLUMNS
import com.rohit.gallery.feature.selection.SelectableMediaGrid
import com.rohit.gallery.feature.selection.SelectionActionBar
import com.rohit.gallery.feature.selection.SelectionTopBar
import kotlinx.coroutines.launch

/** Everything marked as a favorite, with the usual selection actions. */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val cellPx = LocalWindowInfo.current.containerSize.width / GRID_COLUMNS
    val count = selection.ids.size

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!selection.isSelecting) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text("Favorites", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                SelectionTopBar(
                    count = count,
                    onClose = selection::clear,
                    onSelectAll = { items?.let(selection::selectAll) },
                )
            }

            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No favorites yet", style = MaterialTheme.typography.titleMedium)
                }
                else -> SelectableMediaGrid(list, selection, cellPx, onOpenMedia)
            }
        }

        if (selection.isSelecting) {
            SelectionActionBar(
                enabled = count > 0,
                allFavorite = true,
                onShare = {
                    val intent = viewModel.shareIntent()
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, "Couldn't share that many items at once", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onFavorite = { launch { viewModel.unfavoriteRequest() } },
                onTrash = { launch { viewModel.trashRequest() } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
