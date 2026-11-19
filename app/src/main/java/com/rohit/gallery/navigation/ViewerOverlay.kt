package com.rohit.gallery.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rohit.gallery.core.ui.components.LocalSharedTransitionScope
import com.rohit.gallery.core.ui.components.SharedPhotoState
import com.rohit.gallery.core.ui.theme.GalleryMotion
import com.rohit.gallery.feature.viewer.ViewerScreen
import com.rohit.gallery.feature.viewer.ViewerViewModel
import com.rohit.gallery.feature.viewer.rememberViewerViewModelFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The viewer of one grid screen. The viewer is drawn as a layer over that screen instead of being a screen of its
 * own, so the grid stays composed underneath: closing only removes the layer, and the photo's flight back into its
 * cell is not eaten by a rebuild of the whole grid (that took about 0.24 s on the phone, most of the flight).
 */
@Stable
class ViewerHost(route: ViewerRoute?, session: Int) : SharedPhotoState {
    /** What the viewer shows; non-null while it is open or closing. */
    var route by mutableStateOf(route)
        private set

    /** Bumped on each open, so every visit gets fresh viewer state. */
    var session by mutableIntStateOf(session)
        private set

    /** The close flight is running; the layer goes away when it ends. */
    var closing by mutableStateOf(false)
        private set

    override var activeId by mutableStateOf(route?.mediaId)

    override val viewerShowing: Boolean get() = route != null && !closing

    override var viewerSettled by mutableStateOf(false)
        internal set

    /** Step 1 of opening: tags the tapped cell as the shared element, a frame before the viewer appears. */
    fun prepare(mediaId: Long) {
        if (route == null) activeId = mediaId
    }

    /** Step 2 of opening. */
    fun open(route: ViewerRoute) {
        session++
        closing = false
        viewerSettled = false
        activeId = route.mediaId
        this.route = route
    }

    fun close() {
        if (route == null) return
        closing = true
        viewerSettled = false
    }

    internal fun finishClose() {
        route = null
        closing = false
        viewerSettled = false
        activeId = null
    }

    companion object {
        /** Keeps an open viewer across process death and while another screen is on top of it. */
        val Saver: Saver<ViewerHost, String> = Saver(
            save = { host ->
                val route = host.route?.takeIf { !host.closing }
                "${host.session}|" + (route?.let { Json.encodeToString(ViewerRoute.serializer(), it) } ?: "")
            },
            restore = { saved ->
                val session = saved.substringBefore('|').toIntOrNull() ?: 0
                val json = saved.substringAfter('|')
                val route = if (json.isEmpty()) null else runCatching { Json.decodeFromString(ViewerRoute.serializer(), json) }.getOrNull()
                ViewerHost(route, session)
            },
        )
    }
}

@Composable
fun rememberViewerHost(): ViewerHost = rememberSaveable(saver = ViewerHost.Saver) { ViewerHost(null, 0) }

/**
 * Owns the view models of a screen's viewer visits. Kept by the screen's back-stack entry, so an open viewer
 * survives a rotation or a trip to an album; each visit's view model is cleared when that visit closes.
 */
class ViewerSessions : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    override fun onCleared() = viewModelStore.clear()
}

/**
 * Draws [host]'s viewer over the screen while it is open, and keeps it until the close flight into the grid has
 * landed. Fades in like the old viewer screen did and fades out as it closes.
 */
@Composable
fun ViewerOverlay(
    host: ViewerHost,
    onOpenAlbum: (bucketId: Long) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val route = host.route ?: return
    val sessions: ViewerSessions = viewModel { ViewerSessions() }
    val factory = rememberViewerViewModelFactory()
    val transitionScope = LocalSharedTransitionScope.current
    val session = host.session

    // Fallback while closing (the viewer's own back handlers are off then): swallow Back instead of leaving the
    // screen underneath. Registered before the viewer's, so those win while it is open.
    BackHandler { host.close() }

    key(session) {
        val viewModel: ViewerViewModel = viewModel(viewModelStoreOwner = sessions, key = "viewer-$session") {
            factory.create(route)
        }
        val fade = remember { Animatable(0f) }
        LaunchedEffect(Unit) { fade.animateTo(1f, tween(GalleryMotion.FADE_MS)) }
        // Once the open flight has landed, unlink the grid cell (see SharedPhotoState.viewerSettled).
        LaunchedEffect(Unit) {
            withFrameNanos { }
            withFrameNanos { }
            snapshotFlow { transitionScope?.isTransitionActive == true }.first { !it }
            if (!host.closing) host.viewerSettled = true
        }
        if (host.closing) {
            LaunchedEffect(Unit) {
                coroutineScope {
                    launch { fade.animateTo(0f, tween(GalleryMotion.FADE_MS)) }
                    // Let the flight start, then wait for it to land.
                    withFrameNanos { }
                    withFrameNanos { }
                    snapshotFlow { transitionScope?.isTransitionActive == true }.first { !it }
                }
                host.finishClose()
                sessions.viewModelStore.clear()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                // Touches never reach the grid underneath while the viewer is there.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) {
            ViewerScreen(
                onBack = host::close,
                onCurrentItemChanged = { id -> if (id != null && !host.closing) host.activeId = id },
                onOpenAlbum = onOpenAlbum,
                onOpenFavorites = onOpenFavorites,
                viewModel = viewModel,
            )
        }
    }
}
