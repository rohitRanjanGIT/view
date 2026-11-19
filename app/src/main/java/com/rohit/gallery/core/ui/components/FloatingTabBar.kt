package com.rohit.gallery.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** Height of the capsule; it floats [BarBottom] above the bottom inset. */
val FloatingTabBarHeight = 56.dp
private val BarBottom = 12.dp
private val SlotWidth = 112.dp
private val Inset = 4.dp

/** One tab of [FloatingTabBar]: an icon and its label (the label shows only on the selected tab). */
data class FloatingTab(val label: String, val icon: ImageVector)

/**
 * The bottom navigation as a compact floating capsule, centred above the bottom edge and sized to its tabs. Every
 * tab has the same width, so spacing is always even. One soft light pill marks the selected tab; it follows
 * [position] (the pager's live page, fractional while swiping), so it slides with the finger between tabs. The
 * selected tab shows its label next to its icon; the others show the icon alone.
 */
@Composable
fun FloatingTabBar(
    tabs: List<FloatingTab>,
    selectedIndex: Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(percent = 50)
    val indicator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = BarBottom),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(FloatingTabBarHeight)
                .width(SlotWidth * tabs.size + Inset * 2)
                .frostedChrome(haze, capsule = true, style = floatingHazeStyle())
                .padding(Inset),
        ) {
            // The selection pill: moved by the graphics layer only, so following a swipe never recomposes the bar.
            Box(
                Modifier
                    .width(SlotWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = position().coerceIn(0f, (tabs.size - 1).toFloat()) * SlotWidth.toPx() }
                    .background(indicator, pill),
            )
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    FloatingTabItem(tab, selected = index == selectedIndex, onClick = { onSelect(index) })
                }
            }
        }
    }
}

@Composable
private fun FloatingTabItem(tab: FloatingTab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (selected) colors.onSurface else colors.onSurfaceVariant
    Row(
        Modifier
            .width(SlotWidth)
            .fillMaxHeight()
            .semantics { this.selected = selected; role = Role.Tab }
            // The sliding pill is the feedback; a ripple would flash a second shape over it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = tab.label.takeUnless { selected }, tint = tint, modifier = Modifier.size(22.dp))
        AnimatedVisibility(visible = selected, enter = fadeIn(tween(160)), exit = fadeOut(tween(100))) {
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
