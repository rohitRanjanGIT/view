package com.rohit.gallery.feature.settings

import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.prefs.AppSettings
import com.rohit.gallery.core.data.prefs.ThemeMode
import com.rohit.gallery.core.data.vault.VaultRepository
import com.rohit.gallery.feature.vault.LockProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen. Settings are null until DataStore answers (a few ms) so the screen never flashes
 * default values; the "skip confirmation" state is a system permission, so it is re-read on demand instead of stored.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val vault: VaultRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _canManageMedia = MutableStateFlow(false)
    val canManageMedia: StateFlow<Boolean> = _canManageMedia.asStateFlow()

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    init {
        refreshManageMedia()
    }

    fun setTheme(theme: ThemeMode) = change { it.copy(theme = theme) }

    fun setWallpaperColors(enabled: Boolean) = change { it.copy(wallpaperColors = enabled) }

    fun setStartMuted(enabled: Boolean) = change { it.copy(startMuted = enabled) }

    fun setMotionAutoplay(enabled: Boolean) = change { it.copy(motionAutoplay = enabled) }

    fun setUltraHdr(enabled: Boolean) = change { it.copy(ultraHdr = enabled) }

    fun setSkipSeconds(seconds: Int) = change { it.copy(skipSeconds = seconds) }

    /** Locked is turned on; null until read. */
    val lockedEnabled: StateFlow<Boolean?> = prefs.lockedEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** How many photos and videos are in Locked; null while counting. */
    val lockedCount: StateFlow<Int?> = vault.items
        .map { it?.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _turningOff = MutableStateFlow<LockProgress?>(null)

    /** Progress while turning Locked off moves everything back out. */
    val turningOff: StateFlow<LockProgress?> = _turningOff.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun turnOnLocked() {
        viewModelScope.launch { prefs.setLockedEnabled(true) }
    }

    /**
     * Turns Locked off (the screen has already asked for the screen lock and confirmed). Everything in it goes back to
     * the album it came from first; only when every item made it does the switch go off. If some couldn't be moved,
     * Locked stays on and keeps them, so nothing is ever left behind in a folder with no way in.
     */
    fun turnOffLocked() {
        if (_turningOff.value != null) return
        viewModelScope.launch {
            // Waits for the real count: a not-yet-loaded list must never read as "empty" and skip moving items out.
            val total = vault.items.filterNotNull().first().size
            if (total > 0) {
                _turningOff.value = LockProgress(0, total)
                val result = try {
                    vault.unlockAll { done, all -> _turningOff.value = LockProgress(done, all) }
                } finally {
                    _turningOff.value = null
                }
                if (result.failed > 0) {
                    _messages.tryEmit("${result.failed} couldn't be moved back, so Locked stays on. Try again.")
                    return@launch
                }
                _messages.tryEmit("Moved ${result.done} back to their albums. Locked is off.")
            }
            prefs.setLockedEnabled(false)
        }
    }

    /** Call when returning from the system "media management" screen, where the user may have changed it. */
    fun refreshManageMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _canManageMedia.value = runCatching { MediaStore.canManageMedia(context) }.getOrDefault(false)
        }
    }

    private fun change(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { prefs.update(transform) }
    }
}
