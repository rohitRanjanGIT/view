package com.rohit.gallery.feature.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.domain.albumNameError
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.ui.components.MediaThumbnail

/**
 * Bottom sheet to choose where media goes: an existing album or a new one. [onPick] gets the chosen
 * album, or null plus the typed name for a new album.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    title: String,
    albums: List<Album>,
    onPick: (album: Album?, newName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding()) {
            item(key = "new") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = null) }
                    Text("New album", style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(albums, key = { it.bucketId }) { album ->
                AlbumRow(album, onClick = { onPick(album, null) })
            }
        }
    }

    if (creating) NewAlbumDialog(onCreate = { onPick(null, it) }, onDismiss = { creating = false })
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    val thumbPx = with(LocalDensity.current) { 48.dp.roundToPx() * 2 }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MediaThumbnail(album.cover, thumbPx, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), showBadge = false)
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text("${album.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NewAlbumDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    val error = albumNameError(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New album") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; touched = true },
                singleLine = true,
                label = { Text("Album name") },
                isError = touched && error != null,
                supportingText = { if (touched && error != null) Text(error) },
            )
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = { onCreate(name.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
