package com.rohit.gallery.feature.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.rohit.gallery.core.ui.components.floatingHazeStyle
import com.rohit.gallery.core.ui.icons.GalleryIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.rohit.gallery.core.ui.components.FloatingActionPill
import com.rohit.gallery.core.ui.components.pillSlot
import com.rohit.gallery.core.ui.components.frostedChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Top bar while selecting: close, "N selected", and select all. */
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color.Transparent, modifier = modifier.fillMaxWidth().frostedChrome(edgeAtTop = false)) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Exit selection") }
            Text(
                if (count == 0) "Select items" else "$count selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) { Text("All") }
        }
    }
}

/** Bottom bar while selecting; takes the place of the navigation bar. */
@Composable
fun SelectionActionBar(
    enabled: Boolean,
    allFavorite: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onTrash: () -> Unit,
    onMove: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    /** Copy to clipboard; [clipboardEnabled] only with one photo (most apps paste only the first of several). */
    onClipboard: (() -> Unit)? = null,
    clipboardEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val more = buildList {
        if (onMove != null) add(MoreAction(GalleryIcons.FolderMove, "Move to album", onMove))
        if (onCopy != null) add(MoreAction(GalleryIcons.FolderCopy, "Copy to album", onCopy))
        if (onClipboard != null) {
            add(MoreAction(GalleryIcons.Clipboard, "Copy to clipboard", onClipboard, clipboardEnabled, hint = if (clipboardEnabled) null else "One photo at a time"))
        }
        if (onLock != null) add(MoreAction(GalleryIcons.Lock, "Lock", onLock, dividerBefore = isNotEmpty()))
    }
    FloatingActionPill(modifier) {
        ActionItem(Icons.Filled.Share, "Share", enabled, onShare, Modifier.pillSlot())
        ActionItem(
            if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (allFavorite) "Unfavourite" else "Favourite",
            enabled,
            onFavorite,
            Modifier.pillSlot(),
        )
        ActionItem(Icons.Filled.Delete, "Trash", enabled, onTrash, Modifier.pillSlot())
        if (more.isNotEmpty()) {
            val menu = remember { MutableTransitionState(false) }
            val haptics = LocalHapticFeedback.current
            // Nothing selected any more: fold the card away.
            LaunchedEffect(enabled) { if (!enabled) menu.targetState = false }
            // The slot spans the capsule's full height, so the card sits a set gap above the capsule's top edge.
            Box(Modifier.pillSlot().fillMaxHeight(), contentAlignment = Alignment.Center) {
                ActionItem(Icons.Filled.MoreVert, "More", enabled, {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    menu.targetState = true
                }, Modifier.fillMaxWidth())
                MoreMenuCard(menu, more)
            }
        }
    }
}

/** One row of the More card. */
@Immutable
private class MoreAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    /** A second line, e.g. why the row is greyed out. */
    val hint: String? = null,
    /** A hairline above the row, to set it apart (Lock). */
    val dividerBefore: Boolean = false,
)

/**
 * The More menu: a small frosted card that springs up out of the More button and sits just above the capsule,
 * right-aligned with it. Every row has an icon and a label. A tap outside, Back, or a row closes it.
 */
@Composable
private fun MoreMenuCard(state: MutableTransitionState<Boolean>, actions: List<MoreAction>) {
    // Stay composed until the closing animation is over.
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val position = remember(density) {
        with(density) { AboveAnchor(gap = 10.dp.roundToPx(), margin = 12.dp.roundToPx(), endInset = 6.dp.roundToPx()) }
    }
    val close = { state.targetState = false }
    // Grows from the More button's corner: the card's bottom right.
    val origin = TransformOrigin(0.88f, 1f)
    Popup(popupPositionProvider = position, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn(tween(110)) + scaleIn(spring(dampingRatio = 0.78f, stiffness = 520f), initialScale = 0.6f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.85f, transformOrigin = origin),
        ) {
            Column(
                Modifier
                    .width(MenuWidth)
                    .frostedChrome(style = floatingHazeStyle(), corner = 24.dp)
                    .padding(vertical = 6.dp),
            ) {
                actions.forEach { action ->
                    if (action.dividerBefore) {
                        HorizontalDivider(
                            Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    MoreRow(action) {
                        close()
                        action.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(action: MoreAction, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (action.enabled) 1f else 0.38f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = action.enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(action.label, style = MaterialTheme.typography.bodyLarge, color = tint)
            action.hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

private val MenuWidth = 232.dp

/**
 * Places a popup above its anchor (the More slot, as tall as the capsule) with a [gap], its right edge [endInset]
 * past the anchor's (the capsule's own edge), kept [margin] inside the window.
 */
private class AboveAnchor(private val gap: Int, private val margin: Int, private val endInset: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = (anchorBounds.right + endInset).coerceAtMost(windowSize.width - margin)
        val x = (right - popupContentSize.width).coerceAtLeast(margin)
        val y = (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

@Composable
internal fun ActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        modifier
            // In the capsule, the press ripple is a pill too.
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Round check badge shown in the corner of every cell while selecting. */
@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}

