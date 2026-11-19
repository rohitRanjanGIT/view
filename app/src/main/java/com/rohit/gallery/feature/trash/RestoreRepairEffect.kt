package com.rohit.gallery.feature.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rohit.gallery.core.domain.Repair
import com.rohit.gallery.core.domain.RestoreRepair
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RestoreRepairEntryPoint {
    fun restoreRepair(): RestoreRepair
}

/**
 * Watches for photos that came back from Trash with a different date or favourite mark and puts the old values back
 * (see [RestoreRepair]). One for the whole app, so every way of restoring is covered. With "Skip confirmation
 * pop-ups" on it is silent; otherwise Android asks once to let the app change those photos.
 */
@Composable
fun RestoreRepairEffect() {
    val app = LocalContext.current.applicationContext
    val repair = remember(app) { EntryPointAccessors.fromApplication(app, RestoreRepairEntryPoint::class.java).restoreRepair() }
    // What waits on Android's answer (plain holder: not drawn).
    val pending = remember { object { var repairs: List<Repair> = emptyList() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val repairs = pending.repairs
        pending.repairs = emptyList()
        if (repairs.isEmpty()) return@rememberLauncherForActivityResult
        // Declined: keep things as they are and don't ask again for these photos.
        if (result.resultCode == Activity.RESULT_OK) repair.applyAsync(repairs) else repair.forgetAsync(repairs.map { it.item.id })
    }

    LaunchedEffect(repair) {
        repair.plans
            .distinctUntilChanged()
            .collect { plan ->
                repair.forget(plan.settled)
                // One request at a time; a newer plan with the same photos comes again after it is answered.
                if (plan.repairs.isEmpty() || pending.repairs.isNotEmpty()) return@collect
                val sender = repair.writeRequest(plan.repairs)
                if (sender == null) {
                    repair.forget(plan.repairs.map { it.item.id })
                } else {
                    pending.repairs = plan.repairs
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
    }
}
