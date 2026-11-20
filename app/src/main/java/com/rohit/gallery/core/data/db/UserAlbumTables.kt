package com.rohit.gallery.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * An album the user made (Albums section of the Albums tab). Virtual: only a name and a list of media ids
 * ([UserAlbumMemberEntity]); no file is ever copied or moved for it, and deleting it deletes only the list.
 */
@Entity(tableName = "user_albums")
data class UserAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** The photo chosen as cover; null (or no longer in the album) means its newest photo. */
    val coverMediaId: Long? = null,
    /** When it was pinned; pins show in this order. */
    val pinnedAt: Long? = null,
    /** Place in the custom order, or null if never placed. */
    val position: Int? = null,
)

/**
 * One photo in one user album. Rows are kept while the photo is in Trash or Locked (it simply isn't shown), so a
 * restored photo comes back into its albums. Indexed by [mediaId] for "which albums is this photo in".
 */
@Entity(
    tableName = "user_album_members",
    primaryKeys = ["albumId", "mediaId"],
    indices = [Index("mediaId")],
)
data class UserAlbumMemberEntity(
    val albumId: Long,
    val mediaId: Long,
    val addedAt: Long,
)

@Dao
interface UserAlbumDao {
    @Query("SELECT * FROM user_albums")
    fun observeAlbums(): Flow<List<UserAlbumEntity>>

    /** Every membership (a few thousand rows at most; resolved against the in-memory index). */
    @Query("SELECT * FROM user_album_members")
    fun observeMembers(): Flow<List<UserAlbumMemberEntity>>

    @Query("SELECT * FROM user_albums")
    suspend fun albums(): List<UserAlbumEntity>

    @Insert
    suspend fun insertAlbum(album: UserAlbumEntity): Long

    @Update
    suspend fun updateAlbums(albums: List<UserAlbumEntity>)

    @Query("DELETE FROM user_albums WHERE id IN (:ids)")
    suspend fun deleteAlbums(ids: List<Long>)

    @Query("DELETE FROM user_album_members WHERE albumId IN (:albumIds)")
    suspend fun deleteMembersOf(albumIds: List<Long>)

    @Query("SELECT mediaId FROM user_album_members WHERE albumId = :albumId")
    suspend fun memberIds(albumId: Long): List<Long>

    /** Photos already in the album are left as they are (their first "added" time stays). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<UserAlbumMemberEntity>)

    @Query("SELECT * FROM user_album_members WHERE albumId = :albumId AND mediaId IN (:mediaIds)")
    suspend fun members(albumId: Long, mediaIds: List<Long>): List<UserAlbumMemberEntity>

    @Query("DELETE FROM user_album_members WHERE albumId = :albumId AND mediaId IN (:mediaIds)")
    suspend fun deleteMembers(albumId: Long, mediaIds: List<Long>)
}
