package com.rohit.gallery.core.data.mediastore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.exifinterface.media.ExifInterface
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the info sheet shows that is not already in the in-memory index. */
@Immutable
data class MediaDetails(
    val fileName: String?,
    /** Folder relative to the storage root, e.g. `DCIM/Camera/`. */
    val folder: String?,
    val camera: String?,
    /** ISO, shutter speed, aperture and focal length, each already formatted (null when the file has none). */
    val exposure: ExposureValues?,
    /** Decimal "lat, lon" text; null when the file has no location or it cannot be read. */
    val location: String?,
    /** True when location could not be read because ACCESS_MEDIA_LOCATION is not granted. */
    val locationNeedsPermission: Boolean,
)

/** Reads per-file details on demand (never on the main thread). Originals are only read, never modified. */
@Singleton
class MediaInfoLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val (name, folder) = queryNameAndFolder(item)
        val canReadLocation = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        var camera: String? = null
        var exposure: ExposureValues? = null
        var location: String? = null
        if (item.isVideo) {
            location = runCatching { videoLocation(item) }.getOrNull()
        } else {
            runCatching {
                // setRequireOriginal gives access to the GPS tags, which are redacted otherwise.
                val uri = if (canReadLocation && item.isMediaStoreUri) MediaStore.setRequireOriginal(item.uri) else item.uri
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    camera = cameraName(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                    exposure = exposureValues(exif)
                    if (canReadLocation) {
                        location = exif.latLong?.let { formatLocation(it[0], it[1]) }
                    }
                }
            }
        }
        MediaDetails(
            fileName = name,
            folder = folder,
            camera = camera,
            exposure = exposure,
            location = location,
            locationNeedsPermission = !canReadLocation && location == null,
        )
    }

    private fun queryNameAndFolder(item: MediaItem): Pair<String?, String?> {
        val uri = item.uri
        if (uri.scheme == "file") return uri.lastPathSegment to null
        // Other apps' providers usually only know OpenableColumns; RELATIVE_PATH is MediaStore's.
        val projection = if (item.isMediaStoreUri) {
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            arrayOf(OpenableColumns.DISPLAY_NAME)
        }
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getStringOrNull(1) else null
            }
        }.getOrNull() ?: (null to null)
    }

    private val MediaItem.isMediaStoreUri: Boolean get() = uri.authority == MEDIA_STORE_AUTHORITY

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (index < columnCount && !isNull(index)) getString(index) else null

    private fun videoLocation(item: MediaItem): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val iso6709 = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION) ?: return null
            // Looks like "+37.7749-122.4194/".
            val match = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(iso6709) ?: return null
            formatLocation(match.groupValues[1].toDouble(), match.groupValues[2].toDouble())
        } finally {
            retriever.release()
        }
    }
}

internal fun formatLocation(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.5f, %.5f", lat, lon)

/** The camera settings a photo was taken with, each formatted for display. */
@Immutable
data class ExposureValues(
    val iso: String?,
    val shutter: String?,
    val aperture: String?,
    val focalLength: String?,
)

private fun exposureValues(exif: ExifInterface): ExposureValues? {
    val values = ExposureValues(
        iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.takeIf { it.isNotBlank() },
        shutter = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let(::formatExposure),
        aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
            ?.let { String.format(Locale.US, "f/%.1f", it) },
        focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }
            ?.let { String.format(Locale.US, "%.0f mm", it) },
    )
    return values.takeIf { listOf(it.iso, it.shutter, it.aperture, it.focalLength).any { v -> v != null } }
}

/** "motorola" + "motorola edge 60 pro" reads as "motorola edge 60 pro", not the make twice. */
internal fun cameraName(make: String?, model: String?): String? {
    val m = make?.trim().orEmpty()
    val mo = model?.trim().orEmpty()
    return when {
        mo.isEmpty() -> m.ifEmpty { null }
        m.isEmpty() || mo.startsWith(m, ignoreCase = true) -> mo
        else -> "$m $mo"
    }
}

internal fun formatExposure(seconds: Double): String =
    if (seconds >= 1.0) String.format(Locale.US, "%.1f s", seconds) else "1/${(1.0 / seconds).roundToInt()} s"
