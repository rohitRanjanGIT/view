package com.rohit.gallery.feature.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rohit.gallery.core.data.prefs.AppPreferences
import com.rohit.gallery.core.data.prefs.GridSizeKey
import com.rohit.gallery.feature.timeline.GridLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Remembered grid sizes for the secondary screens: one level shared by the album, Favorites and Videos
 * timelines, and Trash's column count. Changed in memory at once (pinch) and saved in the background;
 * null until read, so a screen never opens at the default and then jumps.
 */
@HiltViewModel
class GridSizeViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {

    private val _level = MutableStateFlow<GridLevel?>(null)
    val level: StateFlow<GridLevel?> = _level

    private val _trashColumns = MutableStateFlow<Int?>(null)
    val trashColumns: StateFlow<Int?> = _trashColumns

    init {
        viewModelScope.launch {
            val saved = prefs.gridSize(GridSizeKey.Lists).first()
            _level.value = GridLevel.entries.firstOrNull { it.name == saved } ?: GridLevel.Default
        }
        viewModelScope.launch {
            val saved = prefs.gridSize(GridSizeKey.Trash).first()?.toIntOrNull()
            _trashColumns.value = saved?.takeIf { it in FLAT_GRID_COLUMNS } ?: GRID_COLUMNS
        }
    }

    fun setLevel(level: GridLevel) {
        _level.value = level
        viewModelScope.launch { prefs.setGridSize(GridSizeKey.Lists, level.name) }
    }

    fun setTrashColumns(columns: Int) {
        _trashColumns.value = columns
        viewModelScope.launch { prefs.setGridSize(GridSizeKey.Trash, columns.toString()) }
    }
}
