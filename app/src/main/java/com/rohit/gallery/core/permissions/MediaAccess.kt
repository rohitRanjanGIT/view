package com.rohit.gallery.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** What the app can currently read from the user's media library. */
enum class MediaAccess {
    /** All photos and videos. */
    Full,

    /** Only the photos and videos the user picked ("Select photos and videos"). */
    Partial,

    /** Nothing granted yet, or denied once; the system dialog can still be shown. */
    Denied,

    /** Denied in a way the system will no longer prompt for; only app settings can fix it. */
    DeniedPermanently,
    ;

    val canRead: Boolean get() = this == Full || this == Partial
}

@Stable
class MediaAccessState internal constructor(
    val access: MediaAccess,
    private val requestAction: () -> Unit,
    private val settingsAction: () -> Unit,
) {
    /** Shows the system permission dialog (also used to upgrade Partial to Full). */
    fun request() = requestAction()

    fun openAppSettings() = settingsAction()
}

private val FULL_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
)

private val REQUEST_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
)

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun readAccess(context: Context): MediaAccess = when {
    FULL_PERMISSIONS.all { context.isGranted(it) } -> MediaAccess.Full
    REQUEST_PERMISSIONS.any { context.isGranted(it) } -> MediaAccess.Partial
    else -> MediaAccess.Denied
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Tracks media permission, refreshing whenever the app resumes (so changes made in system
 * settings or the photo picker are picked up) and after each permission dialog.
 */
@Composable
fun rememberMediaAccessState(): MediaAccessState {
    val context = LocalContext.current
    var access by remember { mutableStateOf(readAccess(context)) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = readAccess(context)
        if (access.canRead) permanentlyDenied = false
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = readAccess(context)
        if (!access.canRead) {
            val activity = context.findActivity()
            // After the user has refused, the system stops showing the dialog and
            // shouldShowRequestPermissionRationale returns false.
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    val effective = if (!access.canRead && permanentlyDenied) MediaAccess.DeniedPermanently else access

    return remember(effective) {
        MediaAccessState(
            access = effective,
            requestAction = { launcher.launch(if (effective == MediaAccess.Partial) FULL_PERMISSIONS else REQUEST_PERMISSIONS) },
            settingsAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}
