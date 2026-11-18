package com.rohit.gallery.feature.settings

import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.prefs.AppSettings
import com.rohit.gallery.core.data.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
