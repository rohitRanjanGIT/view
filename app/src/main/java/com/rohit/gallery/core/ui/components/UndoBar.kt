package com.rohit.gallery.core.ui.components

import android.app.Activity
import android.content.IntentSender
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An action that can be reversed, offered for a few seconds after it happened ("Moved to trash · Undo"). Used instead
 * of a confirmation for things that are recoverable anyway: confirming every time trains people to tap Yes unread.
 * [undo] returns the system request that reverses it (granted silently with "Manage media" access); [onUndone] runs
 * once that went through.
 */
class UndoRequest internal constructor(
    val message: String,
    val undo: suspend () -> IntentSender?,
    val onUndone: () -> Unit,
    /** Set for an app-only change (taken out of a user album): reversed directly, with no system request. */
    val direct: (suspend () -> Unit)? = null,
)

@Stable
class UndoBarState {
    internal var current by mutableStateOf<UndoRequest?>(null)

    fun show(message: String, undo: suspend () -> IntentSender?, onUndone: () -> Unit) {
        current = UndoRequest(message, undo, onUndone)
    }

    /** For a change only the app's own data saw (no system request): Undo runs [undo] itself. */
    fun show(message: String, undo: suspend () -> Unit) {
        current = UndoRequest(message, undo = { null }, onUndone = {}, direct = undo)
    }
}

val LocalUndoBar = staticCompositionLocalOf<UndoBarState?> { null }

private const val UNDO_SHOWN_MS = 5_000L

/** Shows [state]'s undo bar, if any, for a few seconds. Place it once, near the bottom of the app. */
@Composable
fun UndoBarHost(state: UndoBarState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { object { var onUndone: (() -> Unit)? = null } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val done = pending.onUndone
        pending.onUndone = null
        if (result.resultCode == Activity.RESULT_OK) done?.invoke()
    }

    val request = state.current
    // Kept while the bar animates out.
    var shown by remember { mutableStateOf<UndoRequest?>(null) }
    if (request != null) shown = request
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        delay(UNDO_SHOWN_MS)
        if (state.current === request) state.current = null
    }

    AnimatedVisibility(
        visible = request != null,
        modifier = modifier,
        enter = fadeIn(tween(160)) + slideInVertically { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically { it / 2 },
    ) {
        val r = shown ?: return@AnimatedVisibility
        Snackbar(
            action = {
                TextButton(onClick = {
                    state.current = null
                    scope.launch {
                        r.direct?.let { direct ->
                            direct()
                            return@launch
                        }
                        val sender = runCatching { r.undo() }.getOrNull()
                        if (sender == null) {
                            Toast.makeText(context, "Couldn't undo; it's in Trash", Toast.LENGTH_SHORT).show()
                        } else {
                            pending.onUndone = r.onUndone
                            launcher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    }
                }) { Text("Undo") }
            },
        ) { Text(r.message) }
    }
}
