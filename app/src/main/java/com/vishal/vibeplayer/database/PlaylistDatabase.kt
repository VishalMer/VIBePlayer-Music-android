package com.vishal.vibeplayer.database

import android.content.Context
import androidx.room.*

// ==========================================
// 1. THE TABLES (Entities)
// ==========================================

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val creationDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE // Magic: If you delete a playlist, it auto-deletes the songs inside it!
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val songPath: String, // Holds your local MP3 path OR Jamendo URL!
    val isOnline: Boolean
)

// ==========================================
// 2. THE QUERIES (Data Access Object - DAO)
// ==========================================

@Dao
interface PlaylistDao {

    // Create a new playlist
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    // Add a song to a playlist
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIntoPlaylist(song: PlaylistSongEntity)

    // Get all playlists for the UI
    @Query("SELECT * FROM playlists ORDER BY creationDate DESC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    // Get all song paths inside a specific playlist
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongsInPlaylist(playlistId: Int): List<PlaylistSongEntity>

    // Delete a playlist
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    // --- ADD THIS TO PlaylistDao ---
    // Deletes a specific song from a specific custom playlist
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songPath = :songPath")
    suspend fun removeSongFromPlaylist(playlistId: Int, songPath: String)

    // --- ADD THIS TO PlaylistDao ---
    // Clears a playlist so we can re-insert the songs in a new order
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun removeAllSongsFromPlaylist(playlistId: Int)
}

// ==========================================
// 3. THE DATABASE ENGINE
// ==========================================

@Database(entities = [PlaylistEntity::class, PlaylistSongEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibe_player_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}