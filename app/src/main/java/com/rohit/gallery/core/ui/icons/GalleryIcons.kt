package com.rohit.gallery.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
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

    /** Pictures tab (line icon): a rounded frame with a mountain ridge and a sun. */
    val PicturesTab: ImageVector by lazy {
        lineIcon("PicturesTab") {
            roundRect(3f, 4.5f, 21f, 19.5f, 3f)
            moveTo(3.5f, 16.5f); lineTo(8.5f, 11.5f); lineTo(12.5f, 15.5f); lineTo(15f, 13f); lineTo(20.5f, 18.5f)
            moveTo(17.6f, 8.8f); arcTo(1.6f, 1.6f, 0f, true, true, 14.4f, 8.8f); arcTo(1.6f, 1.6f, 0f, true, true, 17.6f, 8.8f)
        }
    }

    /** Albums tab (line icon): a card with two more stacked behind it. */
    val AlbumsTab: ImageVector by lazy {
        lineIcon("AlbumsTab") {
            roundRect(3f, 9f, 21f, 20.5f, 3f)
            moveTo(5.5f, 6.25f); horizontalLineTo(18.5f)
            moveTo(8f, 3.5f); horizontalLineTo(16f)
        }
    }

    /** Menu tab (line icon): four rounded tiles. */
    val MenuTab: ImageVector by lazy {
        lineIcon("MenuTab") {
            roundRect(3.5f, 3.5f, 10.5f, 10.5f, 2.2f)
            roundRect(13.5f, 3.5f, 20.5f, 10.5f, 2.2f)
            roundRect(3.5f, 13.5f, 10.5f, 20.5f, 2.2f)
            roundRect(13.5f, 13.5f, 20.5f, 20.5f, 2.2f)
        }
    }

    /** Unlock (line icon): a padlock with its shackle swung open. */
    /** Copy to clipboard: a sheet in front of another. */
    val Copy: ImageVector by lazy {
        lineIcon("Copy") {
            roundRect(8.5f, 8.5f, 20f, 20f, 2.5f)
            // The sheet behind: only its top and left edges show.
            moveTo(4.5f, 15.5f)
            verticalLineTo(6.5f); arcTo(2.5f, 2.5f, 0f, false, true, 7f, 4f)
            horizontalLineTo(15.5f)
        }
    }

    val LockOpen: ImageVector by lazy {
        lineIcon("LockOpen") {
            roundRect(4.5f, 10.5f, 19.5f, 20.5f, 2.5f)
            moveTo(8f, 10.5f); verticalLineTo(7.5f); arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
            moveTo(12f, 14.5f); verticalLineTo(16.5f)
        }
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathBuilder = block)
            .build()

    /** Outlined: 1.8-unit strokes with round ends and corners. The icon's tint colours the stroke. */
    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        horizontalLineTo(right - r); arcTo(r, r, 0f, false, true, right, top + r)
        verticalLineTo(bottom - r); arcTo(r, r, 0f, false, true, right - r, bottom)
        horizontalLineTo(left + r); arcTo(r, r, 0f, false, true, left, bottom - r)
        verticalLineTo(top + r); arcTo(r, r, 0f, false, true, left + r, top)
        close()
    }
}
