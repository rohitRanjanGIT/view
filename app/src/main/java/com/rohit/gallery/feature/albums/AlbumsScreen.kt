package com.rohit.gallery.feature.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.permissions.MediaAccessGate
import com.rohit.gallery.core.ui.components.MediaThumbnail

private const val ALBUM_COLUMNS = 2
private val ALBUM_GAP = 12.dp

/** The Albums tab: one card per folder, newest activity first. */
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Album) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    MediaAccessGate(modifier.fillMaxSize()) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
        val albums by viewModel.albums.collectAsStateWithLifecycle()
        val list = albums
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Albums", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(24.dp))
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No albums yet", style = MaterialTheme.typography.titleMedium)
                }
                else -> {
                    val cellPx = (LocalWindowInfo.current.containerSize.width / ALBUM_COLUMNS)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(ALBUM_COLUMNS),
                        horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP),
                        verticalArrangement = Arrangement.spacedBy(ALBUM_GAP),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentBottomPadding + 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list, key = { it.bucketId }) { album -> AlbumCard(album, cellPx, onClick = { onOpenAlbum(album) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, cellPx: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        MediaThumbnail(
            item = album.cover,
            cellSizePx = cellPx,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            showBadge = false,
        )
        Text(
            album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        Text(
            "${album.count}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
