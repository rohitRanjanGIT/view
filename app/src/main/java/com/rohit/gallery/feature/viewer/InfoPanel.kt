package com.rohit.gallery.feature.viewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import com.rohit.gallery.core.ui.components.FavoriteHeartRed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.data.mediastore.ExposureValues
import com.rohit.gallery.core.data.mediastore.MediaDetails
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.ui.icons.GalleryIcons
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val DayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val DayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

/** "Today", "Yesterday", "12 November" this year, else "12 November 2025". */
fun dayLabel(epochMs: Long, today: LocalDate = LocalDate.now()): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> DayMonth.format(date)
        else -> DayMonthYear.format(date)
    }
}

/** The time of day, in the phone's 12 / 24-hour setting. */
fun timeLabel(context: Context, epochMs: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMs))

/** "18 November 2026 at 22:53". */
private fun fullDateLabel(context: Context, epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${DayMonthYear.format(date)} at ${timeLabel(context, epochMs)}"
}

/**
 * Details panel, stacked under the photo: date, file name and place; camera, size and exposure; folder. Asks for
 * ACCESS_MEDIA_LOCATION the first time it is opened (tracked by the caller via [askedForLocation]) because GPS tags
 * are hidden from apps without it. [bottomSpace] keeps the last row clear of the action bar drawn over the panel.
 */
@Composable
fun InfoPanel(
    item: MediaItem,
    askedForLocation: Boolean,
    onAskedForLocation: () -> Unit,
    loadDetails: suspend (MediaItem) -> MediaDetails,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    bottomSpace: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenFavorites: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var locationGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locationGranted = it }
    LaunchedEffect(Unit) {
        if (!locationGranted && !askedForLocation) {
            onAskedForLocation()
            launcher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    val details by produceState<MediaDetails?>(null, item.id, locationGranted) { value = loadDetails(item) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp + bottomSpace),
        ) {
            val d = details
            // Files opened from other apps may have no date or size.
            if (item.sortDateMs > 0) {
                Text(fullDateLabel(context, item.sortDateMs), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            (d?.fileName ?: item.displayName.ifEmpty { null })?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (d != null) {
                Text(
                    when {
                        d.location != null -> d.location
                        d.locationNeedsPermission -> "Location hidden: allow media location access in system settings"
                        else -> "No location information"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }

            PanelDivider()

            d?.camera?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(fileFacts(item), style = MaterialTheme.typography.bodyMedium, color = muted)
            d?.exposure?.let {
                Spacer(Modifier.height(12.dp))
                ExposureBox(it)
            }

            // The album (folder) it is in; tapping opens that album.
            val albumName = item.bucketName.takeIf { it.isNotBlank() && onOpenAlbum != null }
            if (albumName != null || d?.folder != null) {
                PanelDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onOpenAlbum != null) Modifier.clickable(onClick = onOpenAlbum) else Modifier)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GalleryIcons.Folder, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(albumName ?: d?.folder.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        if (albumName != null) {
                            d?.folder?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                        }
                    }
                    if (onOpenAlbum != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open album",
                            tint = muted,
                        )
                    }
                }
            }
            // A favourite also links to Favourites.
            if (onOpenFavorites != null) {
                if (albumName == null && d?.folder == null) PanelDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenFavorites)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = FavoriteHeartRed, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Favourites", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open favourites", tint = muted)
                }
            }
        }
    }
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** ISO, shutter, aperture and focal length in one rounded box, split by thin lines. */
@Composable
private fun ExposureBox(values: ExposureValues) {
    val cells = listOfNotNull(
        values.iso?.let { "ISO" to it },
        values.shutter?.let { "" to it },
        values.aperture?.let { "" to it },
        values.focalLength?.let { "" to it },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Text(
                buildAnnotatedString {
                    if (label.isNotEmpty()) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("$label ") }
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value) }
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "12 MP | 3000 × 4000 | 2.7 MB | JPEG", plus the length for a video. */
private fun fileFacts(item: MediaItem): String = buildList {
    if (item.width > 0 && item.height > 0) {
        if (!item.isVideo) add(megapixels(item))
        add("${item.width} × ${item.height}")
    }
    if (item.isVideo && item.durationMs > 0) add(formatDuration(item.durationMs))
    if (item.sizeBytes > 0) add(formatFileSize(item.sizeBytes))
    item.mimeType.substringAfter('/', "").takeIf { it.isNotEmpty() }?.let { add(it.uppercase(Locale.ROOT)) }
}.joinToString("  |  ")

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

private fun megapixels(item: MediaItem): String =
    DecimalFormat("0.#").format(item.width.toLong() * item.height / 1_000_000.0) + " MP"

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> DecimalFormat("0.#").format(bytes / 1024.0) + " KB"
    bytes < 1024L * 1024 * 1024 -> DecimalFormat("0.#").format(bytes / (1024.0 * 1024)) + " MB"
    else -> DecimalFormat("0.##").format(bytes / (1024.0 * 1024 * 1024)) + " GB"
}
