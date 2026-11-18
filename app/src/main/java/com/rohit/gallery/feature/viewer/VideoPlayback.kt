package com.rohit.gallery.feature.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.rohit.gallery.core.model.MediaItem
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem as PlayerMediaItem

/**
 * Remembers where each video was left, so reopening it resumes there, also after the app is closed: kept in
 * memory and written to DataStore in the background (the [MAX_SAVED] most recent videos).
 */
@Singleton
class PlaybackPositionStore @Inject constructor(
    private val prefs: AppPreferences,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    // Insertion order = most recently saved last. Guarded by itself.
    private val positions = LinkedHashMap<Long, Long>()

    init {
        scope.launch {
            val saved = prefs.videoPositions.first()
            synchronized(positions) { saved.forEach { (id, pos) -> positions.putIfAbsent(id, pos) } }
        }
    }

    fun resumePosition(id: Long): Long = synchronized(positions) { positions[id] } ?: 0L

    fun save(id: Long, positionMs: Long, durationMs: Long) {
        val snapshot = synchronized(positions) {
            positions.remove(id)
            // Near the start or the very end there is nothing worth resuming.
            if (positionMs >= MIN_RESUME_MS && (durationMs <= 0 || positionMs <= durationMs - END_MARGIN_MS)) {
                positions[id] = positionMs
            }
            while (positions.size > MAX_SAVED) positions.remove(positions.keys.first())
            LinkedHashMap(positions)
        }
        scope.launch { prefs.setVideoPositions(snapshot) }
    }

    private companion object {
        const val MIN_RESUME_MS = 3_000L
        const val END_MARGIN_MS = 2_000L
        const val MAX_SAVED = 100
    }
}

/** Observable state of the one ExoPlayer that plays the video currently on screen. */
@Stable
class PlaybackState internal constructor(
    val player: ExoPlayer,
    private val itemId: Long,
) : Player.Listener {
    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var isMuted by mutableStateOf(false)
        private set

    init {
        player.addListener(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
    }

    fun refreshPosition() {
        positionMs = player.currentPosition
    }

    fun togglePlay() {
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            else -> player.play()
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms
    }

    fun applyMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        isMuted = muted
    }

    internal fun release(store: PlaybackPositionStore) {
        store.save(itemId, player.currentPosition, player.duration.coerceAtLeast(0L))
        player.removeListener(this)
        player.release()
    }
}

/**
 * Creates a player for [item] when it is a video (null for photos), starts it from the last
 * position, and releases it when the item changes or the viewer closes. Pauses when the app
 * goes to the background and keeps the screen on while playing.
 */
@Composable
fun rememberPlayback(item: MediaItem, muted: Boolean, store: PlaybackPositionStore): PlaybackState? {
    if (!item.isVideo) return null
    val context = LocalContext.current
    val state = remember(item.id) {
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
        player.setMediaItem(PlayerMediaItem.fromUri(item.uri), store.resumePosition(item.id))
        player.prepare()
        player.playWhenReady = true
        PlaybackState(player, item.id)
    }

    LaunchedEffect(state, muted) { state.applyMuted(muted) }
    LaunchedEffect(state) {
        while (true) {
            state.refreshPosition()
            delay(if (state.isPlaying) 100 else 400)
        }
    }
    DisposableEffect(state) { onDispose { state.release(store) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.player.pause() }

    val view = LocalView.current
    DisposableEffect(state.isPlaying) {
        view.keepScreenOn = state.isPlaying
        onDispose { view.keepScreenOn = false }
    }
    return state
}
