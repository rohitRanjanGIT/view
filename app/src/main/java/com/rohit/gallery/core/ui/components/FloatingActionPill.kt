package com.rohit.gallery.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Width of one action in a [FloatingActionPill]: the same for every action, so spacing is always even. */
private val SlotWidth = 76.dp

/**
 * The action bar shown while selecting (Share, Favourite, Trash...), as a floating frosted capsule like the tab bar
 * ([FloatingTabBar]): centred above the bottom edge and sized to its actions. Give each action [pillSlot].
 * Frosted against [LocalChromeHaze], like the other selection bars.
 */
@Composable
fun FloatingActionPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(FloatingTabBarHeight)
                .frostedChrome(capsule = true, style = floatingHazeStyle())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** One action's slot in a [FloatingActionPill]. */
fun Modifier.pillSlot(): Modifier = width(SlotWidth)
