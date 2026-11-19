package com.rohit.gallery.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.Album
import kotlinx.coroutines.launch

private val SwitcherWidth = 300.dp
private val CoverSize = 44.dp

/**
 * Inside an album: a sideways swipe anywhere on [content] pulls in a list of the other albums from the left, and
 * tapping one switches to it ([onPick]). The edges stay the system back gesture; the grid only scrolls up and down,
 * so a sideways swipe in the middle is free. [enabled] false (while selecting) turns the swipe off.
 */
@Composable
fun AlbumSwitcherDrawer(
    albums: List<Album>,
    currentId: Long?,
    enabled: Boolean,
    onPick: (Album) -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val coverPx = with(LocalDensity.current) { CoverSize.roundToPx() }

    // Opening shows the current album, a couple of rows down from the top.
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            val index = albums.indexOfFirst { it.bucketId == currentId }
            if (index >= 0) listState.scrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = enabled || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(SwitcherWidth)) {
                Text(
                    "Albums",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
                )
                LazyColumn(state = listState) {
                    items(albums, key = { it.bucketId }) { album ->
                        NavigationDrawerItem(
                            label = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                MediaThumbnail(
                                    item = album.cover,
                                    cellSizePx = coverPx,
                                    modifier = Modifier.size(CoverSize).clip(RoundedCornerShape(10.dp)),
                                    showBadge = false,
                                )
                            },
                            badge = { Text("${album.count}", style = MaterialTheme.typography.labelMedium) },
                            selected = album.bucketId == currentId,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (album.bucketId != currentId) onPick(album)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
        content = content,
    )
}
