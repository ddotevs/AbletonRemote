package com.abletonremote.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val sendPort: Int = 11000,
    val receivePort: Int = 11001,
    val isDefault: Boolean = false,
    val lastConnected: Long = 0
)

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY lastConnected DESC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultServer(): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServer(id: Long): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: ServerEntity): Long

    @Delete
    suspend fun delete(server: ServerEntity)

    @Query("UPDATE servers SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE servers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("UPDATE servers SET lastConnected = :time WHERE id = :id")
    suspend fun updateLastConnected(id: Long, time: Long)
}

@Database(entities = [ServerEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
}
