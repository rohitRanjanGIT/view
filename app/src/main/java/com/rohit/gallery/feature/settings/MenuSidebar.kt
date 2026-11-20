package com.rohit.gallery.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.rohit.gallery.core.ui.components.floatingHazeStyle
import com.rohit.gallery.core.ui.components.frostedChrome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Menu as a sidebar that slides in from the right over the Albums tab (Favourites, Videos, Trash, Settings).
 * [progress] is how far it is open, 0 to 1; a drag moves it with the finger, then it settles open or shut.
 */
@Stable
class MenuSidebarState internal constructor(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** The panel's width in pixels, measured when it is laid out (a drag needs it to turn pixels into progress). */
    internal var widthPx = 1f

    private var settling: Job? = null

    /** Drawn at all (open, opening or closing). */
    val isVisible: Boolean get() = progress > 0f

    internal fun startDrag() {
        settling?.cancel()
    }

    /** A finger moved sideways by [dx] pixels: left opens, right closes. */
    internal fun dragBy(dx: Float) {
        progress = (progress - dx / widthPx).coerceIn(0f, 1f)
    }

    /** The finger let go at [velocityX] px/s: a quick flick decides, otherwise open past 30 %. */
    internal fun settle(velocityX: Float, flingPx: Float) {
        val open = when {
            velocityX < -flingPx -> true
            velocityX > flingPx -> false
            else -> progress > OPEN_THRESHOLD
        }
        animateTo(if (open) 1f else 0f, velocityX = velocityX)
    }

    fun open() = animateTo(1f)

    fun close() = animateTo(0f)

    /** Shut at once (a screen is opening on top, so there is nothing to watch). */
    fun closeNow() {
        settling?.cancel()
        progress = 0f
    }

    private fun animateTo(target: Float, velocityX: Float = 0f) {
        settling?.cancel()
        settling = scope.launch {
            // Velocity in progress per second: a leftward flick (negative px) opens (positive progress).
            animate(progress, target, initialVelocity = -velocityX / widthPx, animationSpec = SidebarSpring) { value, _ ->
                progress = value
            }
        }
    }
}

@Composable
fun rememberMenuSidebarState(): MenuSidebarState {
    val scope = rememberCoroutineScope()
    return remember(scope) { MenuSidebarState(scope) }
}

private const val OPEN_THRESHOLD = 0.3f

/** The card's gap from the right edge, and its top below the status bar (under the Albums bar). */
private val CardMargin = 12.dp
private val CardTop = 60.dp

/** No bounce, a touch softer than the tab snap: the panel is bigger than a tab's worth of movement. */
private val SidebarSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

/**
 * Lets a sideways drag move the sidebar. [opening] only takes finger-left drags that start while [enabled] (on the
 * Albums tab); otherwise drags both ways move it (on the open sidebar). Vertical drags, a second finger, or the other
 * direction are left alone for the pager and the grids. Runs before them (initial pass) and consumes what it takes.
 */
fun Modifier.sidebarDrag(state: MenuSidebarState, opening: Boolean, enabled: () -> Boolean): Modifier = pointerInput(state, opening) {
    val slop = viewConfiguration.touchSlop
    val flingPx = 400.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled()) return@awaitEachGesture
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var total = Offset.Zero
        var dragging = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // A pinch is for the grid; put the sidebar where it belongs.
            if (event.changes.count { it.pressed } > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (dragging) {
                    change.consume()
                    state.settle(tracker.calculateVelocity().x, flingPx)
                }
                return@awaitEachGesture
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            val delta = change.positionChange()
            if (!dragging) {
                total += delta
                if (total.getDistance() < slop) continue
                val sideways = abs(total.x) > abs(total.y)
                if (!sideways || (opening && total.x > 0f)) return@awaitEachGesture
                dragging = true
                state.startDrag()
            }
            change.consume()
            state.dragBy(delta.x)
        }
        if (dragging) state.settle(0f, flingPx)
    }
}

/**
 * The sidebar itself, over everything on the home screen (the tab bar too): a floating frosted card that slides in
 * from the right just under the Albums bar, blurring [haze]'s content, and a dim over the rest. Tap the dim, swipe right or press Back to close.
 */
@Composable
fun MenuSidebar(
    state: MenuSidebarState,
    haze: HazeState?,
    onOpenFavorites: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isVisible) { state.close() }
    if (!state.isVisible) return
    BoxWithConstraints(modifier.fillMaxSize().sidebarDrag(state, opening = false) { true }) {
        val width = min(maxWidth * 0.6f, 240.dp)
        // The card floats this far in from the right edge; closed, it sits just off screen.
        val travelPx = with(LocalDensity.current) { (width + CardMargin).toPx() }
        state.widthPx = travelPx
        // The dim over Albums; a tap on it closes.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress }
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = state::close),
        )
        // A floating card just under the Albums bar (by its ☰), only as tall as its rows.
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = CardTop, end = CardMargin)
                .width(width)
                .graphicsLayer { translationX = (1f - state.progress) * travelPx }
                .frostedChrome(state = haze, style = floatingHazeStyle(), corner = 24.dp)
                // Taps inside the card never reach the dim behind it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(6.dp),
        ) {
            SidebarRow(Icons.Outlined.FavoriteBorder, "Favourites", onOpenFavorites)
            SidebarRow(Icons.Outlined.PlayArrow, "Videos", onOpenVideos)
            SidebarRow(Icons.Outlined.Delete, "Trash", onOpenTrash)
            HorizontalDivider(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            SidebarRow(Icons.Outlined.Settings, "Settings", onOpenSettings)
        }
    }
}

@Composable
private fun SidebarRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
    }
}
