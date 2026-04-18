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

// --- NEW ANALYTICS TABLE ---
@Entity(tableName = "play_history_table")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val songPath: String,
    val timestamp: Long, // Stores the exact millisecond the song was played
    val listenedDurationMs: Long
)


// ==========================================
// 2. THE QUERIES (Data Access Object - DAO)
// ==========================================

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIntoPlaylist(song: PlaylistSongEntity)

    @Query("SELECT * FROM playlists ORDER BY creationDate DESC")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongsInPlaylist(playlistId: Int): List<PlaylistSongEntity>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songPath = :songPath")
    suspend fun removeSongFromPlaylist(playlistId: Int, songPath: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun removeAllSongsFromPlaylist(playlistId: Int)
}

// --- NEW ANALYTICS QUERIES ---
@Dao
interface HistoryDao {
    @Insert
    suspend fun insertPlayRecord(record: PlayHistoryEntity)

    // Gets all plays that happened AFTER the timestamp we provide
    @Query("SELECT * FROM play_history_table WHERE timestamp >= :sevenDaysAgoMs")
    suspend fun getRecentPlays(sevenDaysAgoMs: Long): List<PlayHistoryEntity>
}


// ==========================================
// 3. THE DATABASE ENGINE
// ==========================================

// WE INCREASED VERSION TO 2 AND ADDED PLAYHISTORYENTITY!
@Database(entities = [PlaylistEntity::class, PlaylistSongEntity::class, PlayHistoryEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao

    // ADDED THE NEW DAO ACCESSOR
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibe_player_database"
                )
                    .fallbackToDestructiveMigration() // Added this safety net for future DB changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}