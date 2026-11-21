package com.rohit.gallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** The speeds the card offers, slowest first. */
private val Speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** "1x", "1.5x", "0.25x". */
internal fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/**
 * The speed chip on the video bar: shows the chosen speed (filled in slightly when it is not 1x) and opens
 * [SpeedCard] above itself. The speed belongs to this video's [PlaybackState], so every video starts at 1x.
 */
@Composable
fun VideoSpeedChip(playback: PlaybackState, modifier: Modifier = Modifier) {
    // A new video closes the card.
    val menu = remember(playback) { MutableTransitionState(false) }
    val haptics = LocalHapticFeedback.current
    val speed = playback.speed
    val changed = speed != 1f
    val label = speedLabel(speed)
    // 48 dp touch target around a small pill.
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(role = Role.Button, onClickLabel = "Change playback speed") {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                menu.targetState = true
            }
            .semantics { contentDescription = "Playback speed $label" }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(percent = 50)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .background(Color.White.copy(alpha = if (changed) 0.24f else 0f), shape)
                .border(1.dp, Color.White.copy(alpha = if (changed) 0.6f else 0.35f), shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        SpeedCard(menu, current = speed, onPick = playback::changeSpeed)
    }
}

/**
 * The speed card: springs up out of the chip and sits just above it, one row of speeds with the current one
 * lit. Picking a speed applies it and closes the card; a tap outside or Back closes it too.
 *
 * The viewer has no frosted-bar source (and a popup is its own window, so it could not blur the video anyway), so
 * the card is a dark translucent rounded card that matches the viewer's chrome rather than the app's frosted menus.
 */
@Composable
private fun SpeedCard(state: MutableTransitionState<Boolean>, current: Float, onPick: (Float) -> Unit) {
    // Stay composed until the closing animation is over.
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val position = remember(density) {
        with(density) { AboveChip(gap = 4.dp.roundToPx(), margin = 12.dp.roundToPx()) }
    }
    val close = { state.targetState = false }
    // Grows from the chip: the card's bottom right.
    val origin = TransformOrigin(0.85f, 1f)
    val shape = RoundedCornerShape(percent = 50)
    Popup(popupPositionProvider = position, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(110)) + scaleIn(spring(dampingRatio = 0.78f, stiffness = 520f), initialScale = 0.6f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.85f, transformOrigin = origin),
        ) {
            Row(
                Modifier
                    .clip(shape)
                    .background(CardColor, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Speeds.forEach { speed ->
                    SpeedOption(speed, selected = speed == current) {
                        onPick(speed)
                        close()
                    }
                }
            }
        }
    }
}

/** One speed in the row: the chosen one sits in a lighter pill. 44 dp tall, as wide as its label plus a little. */
@Composable
private fun SpeedOption(speed: Float, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        Modifier
            .height(44.dp)
            .widthIn(min = 40.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.2f else 0f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${speedLabel(speed)} speed" }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            speedLabel(speed),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
        )
    }
}

private val CardColor = Color(0xFF202124).copy(alpha = 0.92f)

/** Places a popup above its anchor (the chip) with a [gap], right-aligned with it, kept [margin] inside the window. */
private class AboveChip(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = anchorBounds.right.coerceAtMost(windowSize.width - margin)
        val x = (right - popupContentSize.width).coerceAtLeast(margin)
        val y = (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}
