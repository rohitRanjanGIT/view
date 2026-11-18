package com.rohit.gallery.core.di

import android.content.Context
import androidx.room.Room
import com.rohit.gallery.core.data.db.AlbumPrefDao
import com.rohit.gallery.core.data.db.GalleryDatabase
import com.rohit.gallery.core.data.repository.HiddenAlbums
import com.rohit.gallery.core.data.repository.RoomHiddenAlbums
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GalleryDatabase =
        Room.databaseBuilder(context, GalleryDatabase::class.java, "gallery.db").build()

    @Provides
    fun provideAlbumPrefDao(db: GalleryDatabase): AlbumPrefDao = db.albumPrefs()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HiddenAlbumsModule {
    @Binds
    abstract fun bindHiddenAlbums(impl: RoomHiddenAlbums): HiddenAlbums
}
