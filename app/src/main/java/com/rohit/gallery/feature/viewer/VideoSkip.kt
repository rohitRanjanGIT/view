package com.rohit.gallery.feature.viewer

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomableState

/** The feedback stays this long after the last tap, then fades. */
private const val SKIP_FEEDBACK_HOLD_MS = 600L
private const val SKIP_RIPPLE_MS = 450

/** Above this the video counts as zoomed in: a double-tap anywhere zooms out instead of skipping. */
private const val ZOOMED_FRACTION = 0.02f

/** Live state of double-tap skipping on one video page: the run, and what the overlay shows. */
@Stable
class VideoSkipState {
    internal val run = SkipRun()

    /** Page width, for telling which third a double-tap hit (Telephoto gives the point, not the size). */
    internal var widthPx = 0f

    /** Side the overlay shows; kept after the run ends so the feedback can fade out where it was. */
    var shownSide by mutableStateOf(TapSide.Forward)
        private set
    var shownSeconds by mutableIntStateOf(0)
        private set
    /** Bumped on every step: restarts the overlay's ripple and its fade timer. */
    var pulse by mutableIntStateOf(0)
        private set

    /** Seeks one more step in the run's direction and updates the feedback. */
    internal fun step(playback: PlaybackState, stepSeconds: Int, haptic: HapticFeedback) {
        val side = run.side ?: return
        val stepMs = stepSeconds * 1_000L
        val delta = if (side == TapSide.Back) -stepMs else stepMs
        // currentPosition already reports a pending seek, so quick steps add up instead of repeating one jump.
        playback.seekTo(skipTarget(playback.player.currentPosition, delta, playback.durationMs))
        shownSide = side
        shownSeconds = run.steps * stepSeconds
        pulse++
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
}

@Composable
fun rememberVideoSkipState(): VideoSkipState = remember { VideoSkipState() }

/**
 * Telephoto's double-tap: on the left or right third of a video at rest it starts a skip run; in the middle third,
 * or anywhere while zoomed in (so zoom can never get stuck), it does the usual zoom in / out.
 */
fun videoSkipDoubleClick(
    skip: VideoSkipState,
    playback: PlaybackState?,
    stepSeconds: Int,
    haptic: HapticFeedback,
): DoubleClickToZoomListener = object : DoubleClickToZoomListener {
    private val zoom = DoubleClickToZoomListener.cycle()

    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        val side = tapSide(centroid.x, skip.widthPx)
        val zoomed = (state.zoomFraction ?: 0f) > ZOOMED_FRACTION
        if (playback == null || side == TapSide.Middle || zoomed) {
            skip.run.end()
            zoom.onDoubleClick(state, centroid)
        } else {
            skip.run.start(side, SystemClock.uptimeMillis())
            skip.step(playback, stepSeconds, haptic)
        }
    }
}

/**
 * Follow-up taps of a skip run (YouTube style): once a double-tap has skipped, each further single tap on the same
 * side within the run's window adds a step at once. It watches in the Initial pass and consumes only the lift of
 * such a tap, so Telephoto never sees a click (no chrome toggle, no second double-tap) while swipes, holds and
 * pinches that start there are left alone. Any other press ends the run and passes through untouched.
 */
fun Modifier.videoSkipTaps(
    playback: PlaybackState?,
    skip: VideoSkipState,
    stepSeconds: Int,
    haptic: HapticFeedback,
): Modifier = if (playback == null) this else pointerInput(playback, stepSeconds) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Decided at the press, which this sees before Telephoto: so the second press of the double-tap that starts
        // a run is never mistaken for a follow-up, whether Telephoto reports the double-tap on that press or its lift.
        if (!skip.run.continuesWith(tapSide(down.position.x, size.width.toFloat()), down.uptimeMillis)) {
            skip.run.end()
            return@awaitEachGesture
        }
        // Null if held past the long-press timeout (then it is a hold for 2x, not a tap).
        withTimeoutOrNull(longPressMs) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.count { it.pressed } > 1 ||
                    (change.position - down.position).getDistance() > slop
                ) {
                    skip.run.end()
                    break
                }
                if (!change.pressed) {
                    change.consume()
                    skip.run.step(change.uptimeMillis)
                    skip.step(playback, stepSeconds, haptic)
                    break
                }
            }
        } ?: skip.run.end()
    }
}

/**
 * Feedback for a skip run: a soft half-circle from the tapped edge with a ripple per step and the run's total
 * ("« 30 s"), fading out [SKIP_FEEDBACK_HOLD_MS] after the last tap. Alpha and ripple are read in draw only.
 */
@Composable
fun VideoSkipOverlay(skip: VideoSkipState, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(skip.pulse) {
        if (skip.pulse == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        launch {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(SKIP_RIPPLE_MS, easing = FastOutSlowInEasing))
        }
        delay(SKIP_FEEDBACK_HOLD_MS)
        alpha.animateTo(0f, tween(GalleryMotion.FADE_MS))
    }
    val forward = skip.shownSide == TapSide.Forward
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .drawBehind {
                if (alpha.value == 0f) return@drawBehind
                val center = Offset(if (forward) size.width else 0f, size.height / 2f)
                val radius = minOf(size.width * 0.45f, size.height * 0.5f)
                drawCircle(Color.White.copy(alpha = 0.12f), radius, center)
                val p = ripple.value
                drawCircle(Color.White.copy(alpha = 0.16f * (1f - p)), radius * (0.3f + 0.7f * p), center)
            },
    ) {
        Box(
            Modifier
                .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.3f),
            contentAlignment = Alignment.Center,
        ) {
            val seconds = skip.shownSeconds
            Text(
                if (forward) "$seconds s  »" else "«  $seconds s",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium.copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 8f)),
            )
        }
    }
}
