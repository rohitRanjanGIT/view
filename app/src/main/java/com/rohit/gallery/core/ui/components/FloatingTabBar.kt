package com.rohit.gallery.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** Height of the capsule itself; the bar floats [BarMargin] above the bottom edge. */
val FloatingTabBarHeight = 64.dp
private val BarMargin = 16.dp
private val BarBottom = 12.dp
private val PillHeight = 48.dp

/** One tab of [FloatingTabBar]: an icon and its label (the label shows only on the selected tab). */
data class FloatingTab(val label: String, val icon: ImageVector)

/**
 * The bottom navigation as a floating frosted capsule, inset from the screen edges. The selected tab opens into a
 * pill with its label; the others show their icon alone. Sits above the content, which scrolls underneath it.
 */
@Composable
fun FloatingTabBar(
    tabs: List<FloatingTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(start = BarMargin, end = BarMargin, bottom = BarBottom)
            .fillMaxWidth()
            .height(FloatingTabBarHeight)
            .frostedChrome(haze, capsule = true)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            FloatingTabItem(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun FloatingTabItem(tab: FloatingTab, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .height(PillHeight)
            // Opens and closes its width as the label appears, so the neighbours glide instead of jumping.
            .animateContentSize()
            .clip(RoundedCornerShape(percent = 50))
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(container)
                .padding(horizontal = if (selected) 16.dp else 0.dp)
                .height(PillHeight)
                .widthIn(min = PillHeight),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
