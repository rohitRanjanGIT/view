package com.rohit.gallery.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few icons `material-icons-core` lacks, drawn on the usual 24 x 24 grid (avoids the large extended set). */
object GalleryIcons {
    val Pause: ImageVector by lazy {
        icon("Pause") {
            moveTo(6f, 5f); horizontalLineTo(10f); verticalLineTo(19f); horizontalLineTo(6f); close()
            moveTo(14f, 5f); horizontalLineTo(18f); verticalLineTo(19f); horizontalLineTo(14f); close()
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            moveTo(10f, 4f); horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            verticalLineTo(18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            verticalLineTo(8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            horizontalLineTo(12f)
            close()
        }
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathBuilder = block)
            .build()
}
