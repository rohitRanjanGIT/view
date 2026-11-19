package com.rohit.gallery.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val ThumbHeight = 56.dp
private val ThumbWidth = 36.dp
private const val HIDE_DELAY_MS = 1_500L

/**
 * A draggable handle on the end edge of a long list, shown while the list scrolls (and hidden
 * again shortly after). Dragging it calls [onDragTo] with a 0..1 fraction of the list and shows
 * [labelAt] for that fraction in a bubble beside the handle, with a haptic tick when it changes.
 *
 * [scrollFraction] is read in layout/draw only, so list scrolling does not recompose callers.
 */
@Composable
fun FastScroller(
    scrollFraction: () -> Float,
    isScrolling: Boolean,
    onDragTo: (Float) -> Unit,
    labelAt: (Float) -> String,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, dragging) {
        if (isScrolling || dragging) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    val latestOnDragTo by rememberUpdatedState(onDragTo)
    val latestFraction by rememberUpdatedState(scrollFraction)
    val haptic = LocalHapticFeedback.current
    val label = if (dragging) labelAt(dragFraction) else ""
    LaunchedEffect(label) { if (dragging && label.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }

    BoxWithConstraints(modifier.fillMaxHeight().padding(top = topPadding, bottom = bottomPadding)) {
        val density = LocalDensity.current
        val thumbPx = with(density) { ThumbHeight.toPx() }
        val trackPx = (constraints.maxHeight - thumbPx).coerceAtLeast(1f)
        val thumbY = { (if (dragging) dragFraction else latestFraction()).coerceIn(0f, 1f) * trackPx }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY().roundToInt()) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = dragging, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    DateBubble(label)
                }
                Thumb(
                    Modifier.pointerInput(trackPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragFraction = latestFraction().coerceIn(0f, 1f)
                                dragging = true
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            change.consume()
                            dragFraction = (dragFraction + dy / trackPx).coerceIn(0f, 1f)
                            latestOnDragTo(dragFraction)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Thumb(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        shadowElevation = 3.dp,
        modifier = modifier.size(ThumbWidth, ThumbHeight),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DateBubble(label: String) {
    Box(Modifier.padding(end = 12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 4.dp,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
