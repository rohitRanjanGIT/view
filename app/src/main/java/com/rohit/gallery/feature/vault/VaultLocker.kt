package com.rohit.gallery.feature.vault

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rohit.gallery.core.data.vault.VaultRepository
import com.rohit.gallery.core.model.MediaItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VaultEntryPoint {
    fun vaultRepository(): VaultRepository
}

/** Progress of the copy into the vault, for the dialog; null when idle. */
class LockProgress(val done: Int, val total: Int)

/**
 * Locks photos from any screen: copies them into the vault (with a progress dialog), then asks Android to delete the
 * originals, then keeps or rolls back each copy depending on whether its original is gone (see [VaultRepository]).
 */
@Stable
class VaultLocker internal constructor(private val start: (List<MediaItem>) -> Unit) {
    fun lock(items: List<MediaItem>) = start(items)
}

/** [onLocked] gets how many items ended up locked (0 when nothing was, e.g. the delete was declined). */
@Composable
fun rememberVaultLocker(onLocked: (Int) -> Unit = {}): VaultLocker {
    val context = LocalContext.current
    val app = context.applicationContext
    val repository = remember(app) { EntryPointAccessors.fromApplication(app, VaultEntryPoint::class.java).vaultRepository() }
    val scope = rememberCoroutineScope()
    val latestOnLocked by rememberUpdatedState(onLocked)
    var progress by remember { mutableStateOf<LockProgress?>(null) }
    // The batch waiting for Android's delete confirmation (plain holder: not drawn).
    val pending = remember { object { var batch: VaultRepository.LockBatch? = null } }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { _ ->
        val batch = pending.batch ?: return@rememberLauncherForActivityResult
        pending.batch = null
        scope.launch {
            // Whatever the answer, each item is settled by whether its original is still there.
            val locked = repository.finishLock(batch)
            val skipped = batch.originals.size - locked + batch.failed
            val message = when {
                locked == 0 -> "Nothing was locked"
                skipped == 0 -> "Locked ${plural(locked)}"
                else -> "Locked ${plural(locked)}, $skipped couldn't be"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            latestOnLocked(locked)
        }
    }

    val locker = remember(repository) {
        VaultLocker { items ->
            if (items.isEmpty() || progress != null) return@VaultLocker
            scope.launch {
                progress = LockProgress(0, items.size)
                val batch = try {
                    repository.prepareLock(items) { done, total -> progress = LockProgress(done, total) }
                } catch (e: Exception) {
                    null
                } finally {
                    progress = null
                }
                if (batch == null || batch.originals.isEmpty()) {
                    Toast.makeText(context, "Couldn't lock that", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val sender = runCatching { repository.deleteOriginalsRequest(batch) }.getOrNull()
                if (sender == null) {
                    repository.finishLock(batch) // originals still there: removes the copies again
                    Toast.makeText(context, "Couldn't lock that", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                pending.batch = batch
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }

    progress?.let { LockProgressDialog(it) }
    return locker
}

@Composable
private fun LockProgressDialog(progress: LockProgress) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Locking…") },
        text = {
            Column {
                Text(
                    "Copying ${progress.done} of ${progress.total} into Locked, then the originals are removed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        },
    )
}

internal fun plural(count: Int) = if (count == 1) "1 item" else "$count items"
