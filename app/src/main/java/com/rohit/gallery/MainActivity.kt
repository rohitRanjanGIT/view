package com.rohit.gallery

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rohit.gallery.core.ui.components.LocalThumbnailCache
import com.rohit.gallery.core.ui.components.ThumbnailCache
import com.rohit.gallery.core.ui.theme.GalleryTheme
import com.rohit.gallery.navigation.GalleryNavHost
import com.rohit.gallery.navigation.ViewerRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var thumbnailCache: ThumbnailCache

    /** Non-null when another app asked us to show one file; the app then opens straight in the viewer. */
    private var externalStart by mutableStateOf<ViewerRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalStart = externalViewerRoute(intent)
        setContent {
            CompositionLocalProvider(LocalThumbnailCache provides thumbnailCache) {
                GalleryTheme {
                    // There is no root Surface, so text and icons would default to black even in dark mode.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        // A new external file (onNewIntent) starts a fresh back stack.
                        key(externalStart) {
                            GalleryNavHost(externalStart = externalStart, onExitExternal = ::finish)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        externalViewerRoute(intent)?.let {
            setIntent(intent)
            externalStart = it
        }
    }
}

private val VIEW_ACTIONS = setOf(
    Intent.ACTION_VIEW,
    "com.android.camera.action.REVIEW",
    MediaStore.ACTION_REVIEW,
)

/** The viewer route for a "show this photo/video" intent from another app, or null for a normal launch. */
private fun externalViewerRoute(intent: Intent?): ViewerRoute? {
    if (intent == null || intent.action !in VIEW_ACTIONS) return null
    val uri = intent.data ?: return null
    if (uri.scheme != "content" && uri.scheme != "file") return null
    return ViewerRoute(mediaId = 0L, externalUri = uri.toString(), externalMime = intent.type)
}
