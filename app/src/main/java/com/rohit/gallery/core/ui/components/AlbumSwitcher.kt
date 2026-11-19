package com.rohit.gallery.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Share of the screen width the album strip takes when open. */
private const val STRIP_FRACTION = 0.26f
private val CoverShape = RoundedCornerShape(12.dp)

/**
 * Inside an album: a sideways swipe on the grid ([content]) slides in a narrow strip of album covers from the left,
 * pushing the grid over (still visible and usable) rather than covering it. Tapping a cover switches album
 * ([onPick]) and leaves the strip open, to flick through albums; swiping left or Back closes it. The screen edges stay
 * the system back gesture: the grid only scrolls up and down, so a sideways swipe in the middle is free.
 *
 * The strip follows the finger 1:1, and on release goes where the gesture's momentum points ([GalleryMotion.project]),
 * so a flick opens or closes it from any point. [enabled] false (while selecting) turns the swipe off.
 */
@Composable
fun AlbumSwitcherDrawer(
    albums: List<Album>,
    currentId: Long?,
    enabled: Boolean,
    onPick: (Album) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width * STRIP_FRACTION
    val stripWidth = with(density) { widthPx.toDp() }
    val scope = rememberCoroutineScope()

    // How far the strip is out, in px (0 closed, widthPx open). Open / closed survives leaving the screen.
    var open by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(if (open) widthPx else 0f) }
    val settle = remember { object { var job: Job? = null } }

    fun settleTo(target: Float, velocity: Float = 0f) {
        open = target > 0f
        settle.job?.cancel()
        settle.job = scope.launch {
            animate(offset, target, velocity, GalleryMotion.dragReturn()) { value, _ -> offset = value }
        }
    }

    val dragState = rememberDraggableState { delta ->
        val next = offset + delta
        // Past fully open it resists (rubber band) instead of stopping dead.
        offset = when {
            next < 0f -> 0f
            next <= widthPx -> next
            else -> widthPx + GalleryMotion.rubberBand(next - widthPx, widthPx)
        }
    }

    BackHandler(enabled = open) { settleTo(0f) }

    val shown by remember { derivedStateOf { offset > 0.5f } }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled || open,
                // Grabbing the strip mid-flight takes it over from where it is.
                startDragImmediately = settle.job?.isActive == true,
                onDragStarted = { settle.job?.cancel() },
                onDragStopped = { velocity ->
                    val projected = offset + GalleryMotion.project(velocity)
                    settleTo(if (projected > widthPx / 2) widthPx else 0f, velocity)
                },
            ),
    ) {
        Box(Modifier.fillMaxSize().offset { IntOffset(offset.roundToInt(), 0) }) { content() }
        if (shown) {
            AlbumStrip(
                albums = albums,
                currentId = currentId,
                onPick = onPick,
                modifier = Modifier
                    .width(stripWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = offset - widthPx },
            )
        }
    }
}

/** The covers, one under another, each with its name and count; the album being shown is outlined. */
@Composable
private fun AlbumStrip(albums: List<Album>, currentId: Long?, onPick: (Album) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    // Opening shows the current album near the top.
    LaunchedEffect(Unit) {
        val index = albums.indexOfFirst { it.bucketId == currentId }
        if (index > 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
    }
    val coverPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 96.dp),
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        items(albums, key = { it.bucketId }) { album ->
            val current = album.bucketId == currentId
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(CoverShape)
                    .clickable { if (!current) onPick(album) },
            ) {
                MediaThumbnail(
                    item = album.cover,
                    cellSizePx = coverPx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CoverShape)
                        .then(if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CoverShape) else Modifier),
                    showBadge = false,
                )
                Text(
                    album.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (current) FontWeight.SemiBold else null,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                )
                Text(
                    "${album.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}
