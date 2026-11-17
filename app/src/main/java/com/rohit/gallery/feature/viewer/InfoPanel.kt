package com.rohit.gallery.feature.viewer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.data.mediastore.MediaDetails
import com.rohit.gallery.core.model.MediaItem
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DateTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

fun formatDateTime(epochMs: Long): String = DateTimeFormat.format(Instant.ofEpochMilli(epochMs))

/**
 * Details panel, stacked under the photo. Asks for ACCESS_MEDIA_LOCATION the first time it is opened (tracked by the
 * caller via [askedForLocation]) because GPS tags are hidden from apps without it.
 */

@Composable
fun InfoPanel(
    item: MediaItem,
    askedForLocation: Boolean,
    onAskedForLocation: () -> Unit,
    loadDetails: suspend (MediaItem) -> MediaDetails,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
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
                .padding(top = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val d = details
            Text(d?.fileName ?: "Details", style = MaterialTheme.typography.titleLarge)
            InfoRow("Date", formatDateTime(item.sortDateMs))
            d?.folder?.let { InfoRow("Folder", it) }
            InfoRow("Size", formatFileSize(item.sizeBytes))
            if (item.width > 0 && item.height > 0) {
                InfoRow("Resolution", "${item.width} × ${item.height} (${megapixels(item)})")
            }
            InfoRow("Type", item.mimeType)
            d?.camera?.let { InfoRow("Camera", it) }
            d?.exposure?.takeIf { it.isNotEmpty() }?.let { InfoRow("Exposure", it.joinToString("  ·  ")) }
            when {
                d?.location != null -> InfoRow("GPS location", d.location)
                d?.locationNeedsPermission == true ->
                    InfoRow("GPS location", "Allow location access for media in system settings to see it")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

private fun megapixels(item: MediaItem): String =
    DecimalFormat("0.#").format(item.width.toLong() * item.height / 1_000_000.0) + " MP"

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> DecimalFormat("0.#").format(bytes / 1024.0) + " KB"
    bytes < 1024L * 1024 * 1024 -> DecimalFormat("0.#").format(bytes / (1024.0 * 1024)) + " MB"
    else -> DecimalFormat("0.##").format(bytes / (1024.0 * 1024 * 1024)) + " GB"
}
