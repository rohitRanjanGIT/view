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

    /** An open eye. */
    val Visible: ImageVector by lazy {
        icon(
            "Visible",
            "M12 5C6.5 5 2.5 9.5 1.5 12c1 2.5 5 7 10.5 7s9.5-4.5 10.5-7C21.5 9.5 17.5 5 12 5z" +
                "m0 2.2c3.6 0 6.6 2.6 7.9 4.8-1.3 2.2-4.3 4.8-7.9 4.8S5.4 14.2 4.1 12C5.4 9.8 8.4 7.2 12 7.2z" +
                "M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z",
        )
    }

    /** An eye with a slash: hidden. */
    val Hidden: ImageVector by lazy {
        icon(
            "Hidden",
            "M3.3 2.3L2 3.6l3.1 3.1C3.5 8.1 2.2 10 1.5 12c1 2.5 5 7 10.5 7 1.9 0 3.6-.5 5.1-1.3l3.3 3.3 1.3-1.3L3.3 2.3z" +
                "M12 16.8c-3.6 0-6.6-2.6-7.9-4.8.6-1.1 1.6-2.3 2.6-3.2l1.9 1.9A3.5 3.5 0 0 0 13.3 15.4l1.9 1.9c-1 .3-2 .5-3.2.5z" +
                "M12 5c-1.3 0-2.6.3-3.7.7l1.8 1.8c.6-.2 1.2-.3 1.9-.3 3.6 0 6.6 2.6 7.9 4.8-.5.9-1.2 1.8-2 2.6l1.6 1.6c1.4-1.3 2.4-2.8 3-4.2C21.5 9.5 17.5 5 12 5z",
        )
    }

    /** Three bars: drag to reorder. */
    val DragHandle: ImageVector by lazy { icon("DragHandle", "M4 7h16v2H4zM4 11h16v2H4zM4 15h16v2H4z") }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
