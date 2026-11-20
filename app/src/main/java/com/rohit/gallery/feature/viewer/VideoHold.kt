package com.rohit.gallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.SeekParameters
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.timeline.formatDuration
import kotlin.math.abs

/** What a press and hold on the video is doing. */
enum class HoldMode { None, Fast, Seek }

/** Seeking by sliding while holding: one screen width covers this much of the video, within these bounds. */
private const val SEEK_MIN_SPAN_MS = 20_000L
private const val SEEK_MAX_SPAN_MS = 120_000L

/** Live state of a hold on the video, for the overlay. */
@Stable
class VideoHoldState {
    var mode by mutableStateOf(HoldMode.None)
        internal set
    var targetMs by mutableLongStateOf(0L)
        internal set
    var deltaMs by mutableLongStateOf(0L)
        internal set
}

@Composable
fun rememberVideoHoldState(): VideoHoldState = remember { VideoHoldState() }

/**
 * Press and hold the video (VLC style): it plays at 2x until the finger lifts. Sliding sideways while holding seeks
 * instead (back to the chosen speed, paused while sliding, then plays on from there if it was playing). Once the hold
 * starts it takes the touch for itself, so zoom, swipes and taps ignore it; a press that moves or lifts before the
 * long-press timeout is left to them.
 */
fun Modifier.videoHold(playback: PlaybackState, state: VideoHoldState, haptic: HapticFeedback): Modifier =
    pointerInput(playback) {
        val slop = viewConfiguration.touchSlop
        val longPressMs = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // True if the press ended, moved or became a pinch before the timeout; null if it was held.
            val endedEarly = withTimeoutOrNull(longPressMs) {
                var early = false
                while (!early) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    early = change == null || !change.pressed || event.changes.count { it.pressed } > 1 ||
                        (change.position - down.position).getDistance() > slop || change.isConsumed
                }
                true
            }
            if (endedEarly != null) return@awaitEachGesture

            val player = playback.player
            val wasPlaying = player.playWhenReady
            val duration = playback.durationMs
            val msPerPx = duration.coerceIn(SEEK_MIN_SPAN_MS, SEEK_MAX_SPAN_MS).toFloat() / size.width.coerceAtLeast(1)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            player.setPlaybackSpeed(2f)
            if (!wasPlaying) player.play()
            state.mode = HoldMode.Fast
            var startX = 0f
            var startMs = 0L
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null || !change.pressed) break
                    if (state.mode == HoldMode.Fast && abs(change.position.x - down.position.x) > slop && duration > 0) {
                        // Sliding: from fast play to seeking.
                        state.mode = HoldMode.Seek
                        player.setPlaybackSpeed(playback.speed)
                        player.pause()
                        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        startX = change.position.x
                        startMs = player.currentPosition
                        state.targetMs = startMs
                        state.deltaMs = 0L
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    if (state.mode == HoldMode.Seek) {
                        val target = (startMs + (change.position.x - startX) * msPerPx).toLong().coerceIn(0L, duration)
                        if (target != state.targetMs) {
                            state.targetMs = target
                            state.deltaMs = target - startMs
                            player.seekTo(target)
                        }
                    }
                }
            } finally {
                if (state.mode == HoldMode.Seek) {
                    player.setSeekParameters(SeekParameters.EXACT)
                    playback.seekTo(state.targetMs)
                    if (wasPlaying) player.play()
                } else {
                    player.setPlaybackSpeed(playback.speed)
                    if (!wasPlaying) player.pause()
                }
                state.mode = HoldMode.None
            }
        }
    }

/** "2x" while holding; the time being sought (and how far from where it started) while sliding. */
@Composable
fun VideoHoldOverlay(state: VideoHoldState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.mode == HoldMode.Fast,
            enter = fadeIn(tween(GalleryMotion.FADE_MS)),
            exit = fadeOut(tween(GalleryMotion.FADE_MS)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
        ) {
            Text(
                "2×  ▸▸",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(
            visible = state.mode == HoldMode.Seek,
            enter = fadeIn(tween(GalleryMotion.FADE_MS)),
            exit = fadeOut(tween(GalleryMotion.FADE_MS)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(formatDuration(state.targetMs), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                val delta = state.deltaMs
                Text(
                    (if (delta < 0) "−" else "+") + formatDuration(abs(delta)),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}
