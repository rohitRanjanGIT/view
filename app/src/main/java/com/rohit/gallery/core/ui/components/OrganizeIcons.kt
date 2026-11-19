package com.rohit.gallery.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Album organising icons that material-icons-core lacks, drawn here as simple original shapes. */
object OrganizeIcons {
    /** A push pin: round head, body, needle. */
    val Pin: ImageVector by lazy {
        icon("Pin", "M9 2h6v2h-1v5l3 3v2h-4v6l-1 2-1-2v-6H7v-2l3-3V4H9z")
    }

    /** Three bars: drag to reorder. */
    val DragHandle: ImageVector by lazy { icon("DragHandle", "M4 7h16v2H4zM4 11h16v2H4zM4 15h16v2H4z") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
