package com.rohit.gallery.core.di

import android.content.Context
import androidx.room.Room
import com.rohit.gallery.core.data.db.AlbumPrefDao
import com.rohit.gallery.core.data.db.GalleryDatabase
import com.rohit.gallery.core.data.db.VaultDao
import com.rohit.gallery.core.data.vault.VaultStorage
import java.io.File
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

    @Provides
    fun provideVaultDao(db: GalleryDatabase): VaultDao = db.vault()

    /** The vault folder in app-private storage (no other app or file manager can read it). */
    @Provides
    @Singleton
    fun provideVaultStorage(@ApplicationContext context: Context): VaultStorage = VaultStorage(File(context.filesDir, "vault"))
}
