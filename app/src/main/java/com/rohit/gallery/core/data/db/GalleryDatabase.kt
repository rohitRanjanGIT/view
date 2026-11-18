package com.rohit.gallery.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The user's choices for one album (MediaStore bucket). Only app-owned data; media stays in MediaStore. */
@Entity(tableName = "album_prefs")
data class AlbumPrefEntity(
    @PrimaryKey val bucketId: Long,
    val hidden: Boolean = false,
    val pinnedAt: Long? = null,
    val position: Int? = null,
)

@Dao
interface AlbumPrefDao {
    @Query("SELECT * FROM album_prefs")
    fun observeAll(): Flow<List<AlbumPrefEntity>>

    @Query("SELECT * FROM album_prefs WHERE bucketId = :bucketId")
    suspend fun get(bucketId: Long): AlbumPrefEntity?

    @Query("SELECT * FROM album_prefs")
    suspend fun getAll(): List<AlbumPrefEntity>

    @Upsert
    suspend fun upsert(pref: AlbumPrefEntity)

    @Upsert
    suspend fun upsertAll(prefs: List<AlbumPrefEntity>)
}

@Database(entities = [AlbumPrefEntity::class], version = 1, exportSchema = true)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun albumPrefs(): AlbumPrefDao
}
