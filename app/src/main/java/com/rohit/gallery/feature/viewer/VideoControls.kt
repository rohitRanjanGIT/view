package com.rohit.gallery.feature.viewer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.ui.components.PlayerIcons
import com.rohit.gallery.feature.timeline.formatDuration

/** Play/pause, scrubber with time labels, and mute for the video on screen. */
@Composable
fun VideoControls(
    playback: PlaybackState,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging the thumb the slider shows the finger, not the (lagging) player position.
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val duration = playback.durationMs
    val fraction = scrubFraction
        ?: if (duration > 0) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownMs = scrubFraction?.let { (it * duration).toLong() } ?: playback.positionMs

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = playback::togglePlay) {
            Icon(
                imageVector = if (playback.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                tint = Color.White,
            )
        }
        Text(formatDuration(shownMs), style = MaterialTheme.typography.labelMedium, color = Color.White)
        Slider(
            value = fraction,
            onValueChange = { scrubFraction = it },
            onValueChangeFinished = {
                scrubFraction?.let { playback.seekTo((it * duration).toLong()) }
                scrubFraction = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = Color.White)
        IconButton(onClick = onToggleMute) {
            Icon(
                imageVector = if (playback.isMuted) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp,
                contentDescription = if (playback.isMuted) "Unmute" else "Mute",
                tint = Color.White,
            )
        }
    }
}
