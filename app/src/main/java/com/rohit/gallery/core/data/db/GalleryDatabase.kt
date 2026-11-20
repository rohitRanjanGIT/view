package com.rohit.gallery.core.data.db

import androidx.room.AutoMigration
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

    @Query("DELETE FROM album_prefs WHERE bucketId IN (:bucketIds)")
    suspend fun deleteAll(bucketIds: List<Long>)
}

/**
 * What a photo looked like when it went to Trash. Android re-reads a file when it comes back out and can lose the
 * date it had (WhatsApp photos carry no date inside the file), so restores put [shownDateMs] and [isFavorite] back.
 */
@Entity(tableName = "trash_snapshots")
data class TrashSnapshotEntity(
    @PrimaryKey val mediaId: Long,
    val isVideo: Boolean,
    /** The date the grid showed (date taken, else the file's date): a restore puts it back as the date taken. */
    val shownDateMs: Long,
    val isFavorite: Boolean,
    val savedAt: Long,
)

@Dao
interface TrashSnapshotDao {
    @Query("SELECT * FROM trash_snapshots")
    fun observeAll(): Flow<List<TrashSnapshotEntity>>

    @Upsert
    suspend fun upsertAll(snapshots: List<TrashSnapshotEntity>)

    @Query("DELETE FROM trash_snapshots WHERE mediaId IN (:mediaIds)")
    suspend fun deleteAll(mediaIds: List<Long>)

    /** Trash empties itself after 30 days; older snapshots have nothing left to restore. */
    @Query("DELETE FROM trash_snapshots WHERE savedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

/** The folder a photo was in before the app last moved it into an album: "Remove album" sends it back there. */
@Entity(tableName = "media_origins")
data class MediaOriginEntity(
    @PrimaryKey val mediaId: Long,
    /** Like `DCIM/Camera/`. */
    val relativePath: String,
    val movedAt: Long,
)

@Dao
interface MediaOriginDao {
    @Query("SELECT * FROM media_origins WHERE mediaId IN (:mediaIds)")
    suspend fun getAll(mediaIds: List<Long>): List<MediaOriginEntity>

    @Upsert
    suspend fun upsertAll(origins: List<MediaOriginEntity>)

    @Query("DELETE FROM media_origins WHERE mediaId IN (:mediaIds)")
    suspend fun deleteAll(mediaIds: List<Long>)
}

// Version 2 added the vault table, version 3 the trash snapshots and move origins, version 4 the user albums (new
// tables only, so Room migrates them from the exported schemas).
@Database(
    entities = [
        AlbumPrefEntity::class, VaultItemEntity::class, TrashSnapshotEntity::class, MediaOriginEntity::class,
        UserAlbumEntity::class, UserAlbumMemberEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun albumPrefs(): AlbumPrefDao

    abstract fun vault(): VaultDao

    abstract fun trashSnapshots(): TrashSnapshotDao

    abstract fun mediaOrigins(): MediaOriginDao

    abstract fun userAlbums(): UserAlbumDao
}
