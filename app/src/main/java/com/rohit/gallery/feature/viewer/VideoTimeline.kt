package com.rohit.gallery.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.components.PlayerIcons
import com.rohit.gallery.feature.timeline.formatDuration
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Width of one video frame in the expanded filmstrip. */
internal val FrameWidth = 40.dp

/** One frame per this much of the video, within [MIN_FRAMES]..[MAX_FRAMES]: short clips still get a usable strip. */
private const val MS_PER_FRAME = 1_500L
private const val MIN_FRAMES = 10
private const val MAX_FRAMES = 80

/** How many frames the filmstrip shows for a video of [durationMs]. */
internal fun frameCountFor(durationMs: Long): Int =
    if (durationMs <= 0) MIN_FRAMES else (durationMs / MS_PER_FRAME).toInt().coerceIn(MIN_FRAMES, MAX_FRAMES)

/** Play / pause, elapsed and remaining time, and mute, above the filmstrip while a video is on screen. */
@Composable
fun VideoControlsRow(playback: PlaybackState, onToggleMute: () -> Unit, modifier: Modifier = Modifier) {
    val duration = playback.durationMs
    val shownMs = playback.positionMs
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = playback::togglePlay) {
            Icon(
                imageVector = if (playback.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                tint = Color.White,
            )
        }
        Text(formatDuration(shownMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
        Spacer(Modifier.weight(1f))
        Text(
            "-" + formatDuration((duration - shownMs).coerceAtLeast(0L)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        IconButton(onClick = onToggleMute) {
            Icon(
                imageVector = if (playback.isMuted) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp,
                contentDescription = if (playback.isMuted) "Unmute" else "Mute",
                tint = Color.White,
            )
        }
    }
}

/**
 * Frames of the few videos most recently expanded in the filmstrip, read in the background (nearest the playhead
 * first, so the part on screen fills in first). Each slot is null until its frame is ready.
 */
internal class VideoFrameStore(private val context: Context, private val scope: CoroutineScope, private val frameHeightPx: Int) {
    private class Entry(val frames: SnapshotStateList<ImageBitmap?>, val job: Job)
    private val entries = LinkedHashMap<Long, Entry>()

    fun frames(item: MediaItem, count: Int, durationMs: Long, startFraction: () -> Float): List<ImageBitmap?> {
        entries[item.id]?.takeIf { it.frames.size == count }?.let {
            // Most recently used last.
            entries.remove(item.id)
            entries[item.id] = it
            return it.frames
        }
        val frames = mutableStateListOf<ImageBitmap?>().apply { repeat(count) { add(null) } }
        // Read without subscribing: this runs during composition.
        val near = (Snapshot.withoutReadObservation(startFraction) * count).toInt().coerceIn(0, count - 1)
        val order = (0 until count).sortedBy { abs(it - near) }
        val job = scope.launch {
            if (durationMs <= 0) return@launch
            withContext(Dispatchers.IO) {
                readFrames(context, item, order, count, durationMs, frameHeightPx) { index, bitmap ->
                    val image = bitmap.asImageBitmap()
                    withContext(Dispatchers.Main) { frames[index] = image }
                }
            }
        }
        entries.remove(item.id)?.job?.cancel()
        entries[item.id] = Entry(frames, job)
        while (entries.size > KEEP) entries.remove(entries.keys.first())?.job?.cancel()
        return frames
    }

    private companion object {
        const val KEEP = 3
    }
}

private suspend fun readFrames(
    context: Context,
    item: MediaItem,
    order: List<Int>,
    count: Int,
    durationMs: Long,
    frameHeightPx: Int,
    onFrame: suspend (Int, Bitmap) -> Unit,
) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, item.uri)
        for (index in order) {
            kotlin.coroutines.coroutineContext.ensureActive()
            // The middle of each frame's stretch of time.
            val timeUs = ((index + 0.5) / count * durationMs * 1000).toLong()
            val bitmap = runCatching {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, frameHeightPx * 2, frameHeightPx * 2)
            }.getOrNull() ?: continue
            onFrame(index, bitmap)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Unreadable file: the thumbnail stands in for every frame.
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * The frames of a video across the whole draw area (each one [size].width / count wide, centre-cropped), the
 * [fallback] thumbnail where a frame is not ready yet, and everything after [playheadX] (local px) dimmed.
 */
internal fun DrawScope.drawVideoFrames(frames: List<ImageBitmap?>, fallback: ImageBitmap?, playheadX: Float) {
    val count = frames.size
    if (count == 0) return
    val frameWidth = size.width / count
    for (i in 0 until count) {
        val image = frames[i] ?: fallback ?: continue
        drawCropped(image, Offset(i * frameWidth, 0f), Size(frameWidth + 0.5f, size.height))
    }
    if (playheadX < size.width) {
        val from = playheadX.coerceAtLeast(0f)
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(from, 0f), Size(size.width - from, size.height))
    }
}

/** [image] filling [size] at [topLeft], cropped around its centre. */
private fun DrawScope.drawCropped(image: ImageBitmap, topLeft: Offset, size: Size) {
    if (size.width < 1f || size.height < 1f) return
    val target = size.width / size.height
    val source = image.width.toFloat() / image.height
    val (srcW, srcH) = if (source > target) (image.height * target) to image.height.toFloat() else image.width.toFloat() to (image.width / target)
    drawImage(
        image,
        srcOffset = IntOffset(((image.width - srcW) / 2).roundToInt(), ((image.height - srcH) / 2).roundToInt()),
        srcSize = IntSize(srcW.roundToInt().coerceAtLeast(1), srcH.roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}
