package com.rohit.gallery.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One locked photo or video: its file in the vault (app-private storage) and everything needed to show it and to put
 * it back where it came from. [pending] is true from the copy until the original has been deleted from shared
 * storage; a pending row is never shown and is settled on the next start if the app died in between.
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateTakenMs: Long,
    val dateModifiedMs: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val sha256: String,
    /** The folder it was locked from, like `DCIM/Camera/`; unlocking puts it back there. */
    val originalRelativePath: String,
    val albumName: String,
    val originalMediaId: Long,
    val lockedAtMs: Long,
    val pending: Boolean,
)

@Dao
interface VaultDao {
    @Query(
        "SELECT * FROM vault_items WHERE pending = 0 " +
            "ORDER BY CASE WHEN dateTakenMs > 0 THEN dateTakenMs ELSE dateModifiedMs END DESC, id DESC",
    )
    fun observeCommitted(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE pending = 0")
    suspend fun committed(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE pending = 1")
    suspend fun pending(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id IN (:ids)")
    suspend fun get(ids: List<Long>): List<VaultItemEntity>

    @Insert
    suspend fun insert(item: VaultItemEntity): Long

    @Query("UPDATE vault_items SET pending = 0 WHERE id IN (:ids)")
    suspend fun commit(ids: List<Long>)

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
