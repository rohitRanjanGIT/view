package com.rohit.gallery.core.data.repository

import com.rohit.gallery.core.model.Album
import com.rohit.gallery.core.model.buildAlbums
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

/** Albums derived from the in-memory media index, so they are always in step with the timeline. */
@Singleton
class AlbumRepository @Inject constructor(repository: MediaRepository) {
    val albums: Flow<List<Album>> = repository.state
        .map { state -> if (state is MediaIndexState.Ready) buildAlbums(state.items) else emptyList() }
        .flowOn(Dispatchers.Default)
}
