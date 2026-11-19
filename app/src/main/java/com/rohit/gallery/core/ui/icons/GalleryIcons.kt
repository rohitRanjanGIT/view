package com.rohit.gallery.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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

    /** Pictures tab: a frame with a mountain and a sun inside. */
    val PicturesTab: ImageVector by lazy {
        icon("PicturesTab", PathFillType.EvenOdd) {
            moveTo(3f, 5f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(3f); close()
            moveTo(5f, 7f); verticalLineTo(17f); horizontalLineTo(19f); verticalLineTo(7f); close()
            moveTo(5f, 17f); lineTo(9.5f, 12f); lineTo(12.2f, 14.8f); lineTo(14.4f, 12.4f); lineTo(19f, 17f); close()
            moveTo(17f, 9.8f); arcTo(1.2f, 1.2f, 0f, true, true, 14.6f, 9.8f); arcTo(1.2f, 1.2f, 0f, true, true, 17f, 9.8f); close()
        }
    }

    /** Albums tab: a stack of two cards. */
    val AlbumsTab: ImageVector by lazy {
        icon("AlbumsTab") {
            moveTo(6f, 3.5f); horizontalLineTo(18f); verticalLineTo(6f); horizontalLineTo(6f); close()
            moveTo(3f, 7.5f); horizontalLineTo(21f); verticalLineTo(20f); horizontalLineTo(3f); close()
        }
    }

    /** Menu tab: four tiles in a grid. */
    val MenuTab: ImageVector by lazy {
        icon("MenuTab") {
            moveTo(4f, 4f); horizontalLineTo(10.5f); verticalLineTo(10.5f); horizontalLineTo(4f); close()
            moveTo(13.5f, 4f); horizontalLineTo(20f); verticalLineTo(10.5f); horizontalLineTo(13.5f); close()
            moveTo(4f, 13.5f); horizontalLineTo(10.5f); verticalLineTo(20f); horizontalLineTo(4f); close()
            moveTo(13.5f, 13.5f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(13.5f); close()
        }
    }

    private fun icon(
        name: String,
        fillType: PathFillType = PathFillType.NonZero,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block)
            .build()
}
