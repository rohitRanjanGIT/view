package com.rohit.gallery.feature.viewer

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import com.rohit.gallery.core.ui.theme.GalleryMotion
import kotlin.math.min
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * The viewer's own animation for turning the phone. Android's rotation animation is switched off while the viewer is
 * open (it would turn the whole screen, bars and all); instead the new layout appears at once and the photo starts
 * where it was on the glass, at the old angle and size, then springs upright into its new place, while the buttons
 * slide into their new spots ([chrome] 0 -> 1). All values are read in graphics layers, so the turn never recomposes.
 */
@Stable
class ViewerTurn {
    /** Degrees the photo is turned now (0 at rest). */
    var angle by mutableFloatStateOf(0f)
        internal set
    /** The photo's scale on top of its new fitted size (1 at rest). */
    var scale by mutableFloatStateOf(1f)
        internal set
    /** How far the bars and the rail have moved into their new places (1 at rest). */
    var chrome by mutableFloatStateOf(1f)
        internal set
    internal var turns by mutableIntStateOf(0)
}

/** [aspect]: width / height of the photo on screen now (0 if unknown), to match its old size. */
@Composable
fun rememberViewerTurn(aspect: () -> Float): ViewerTurn {
    val turn = remember { ViewerTurn() }
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        val w = window ?: return@DisposableEffect onDispose { }
        val before = w.attributes.rotationAnimation
        w.attributes = w.attributes.apply { rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT }
        onDispose { w.attributes = w.attributes.apply { rotationAnimation = before } }
    }

    val size = LocalWindowInfo.current.containerSize
    val last = remember { object { var rotation = -1; var size = IntSize.Zero } }
    // Runs after the new layout is composed and before it is drawn, so its first frame already shows the old angle.
    SideEffect {
        val rotation = view.display?.rotation ?: return@SideEffect
        val fromRotation = last.rotation
        val fromSize = last.size
        last.rotation = rotation
        last.size = size
        if (fromRotation < 0 || (fromRotation == rotation && fromSize == size)) return@SideEffect
        // Quarter turns from the old screen to the new one, the short way round.
        var steps = (rotation - fromRotation) % 4
        if (steps > 2) steps -= 4
        if (steps < -2) steps += 4
        if (steps == 0) return@SideEffect
        turn.angle = -90f * steps
        turn.scale = startScale(aspect(), fromSize, size)
        turn.chrome = 0f
        turn.turns++
    }
    LaunchedEffect(turn.turns) {
        if (turn.turns == 0) return@LaunchedEffect
        val spec = GalleryMotion.fluid<Float>(response = 0.45f, dampingRatio = 0.86f)
        coroutineScope {
            launch { animate(turn.angle, 0f, animationSpec = spec) { v, _ -> turn.angle = v } }
            launch { animate(turn.scale, 1f, animationSpec = spec) { v, _ -> turn.scale = v } }
            launch { animate(turn.chrome, 1f, animationSpec = GalleryMotion.fluid(response = 0.5f)) { v, _ -> turn.chrome = v } }
        }
    }
    return turn
}

/**
 * Scale that makes the photo, fitted to the [to] screen and turned back by the turn, as big on the glass as it was fitted
 * to the [from] screen. Turned back, its width lies along the same edge of the phone as before, so the widths match.
 * 1 when the photo's shape is unknown.
 */
private fun startScale(aspect: Float, from: IntSize, to: IntSize): Float {
    if (aspect <= 0f || from.width <= 0 || to.width <= 0) return 1f
    val oldWidth = min(from.width.toFloat(), from.height * aspect)
    val newWidth = min(to.width.toFloat(), to.height * aspect)
    return oldWidth / newWidth
}
