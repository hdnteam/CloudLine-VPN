package com.hdnteam.cloudlinevpn.data.db

import androidx.room.*
import com.hdnteam.cloudlinevpn.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {

    @Query("SELECT * FROM server_configs WHERE subscriptionId = :subId ORDER BY latency ASC, sortOrder ASC")
    fun getBySubscription(subId: Long): Flow<List<ServerConfig>>

    @Query("SELECT * FROM server_configs ORDER BY latency ASC, sortOrder ASC")
    fun getAll(): Flow<List<ServerConfig>>

    @Query("SELECT * FROM server_configs WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelected(): ServerConfig?

    @Query("SELECT * FROM server_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServerConfig?

    @Query("SELECT * FROM server_configs ORDER BY latency ASC LIMIT 1")
    suspend fun getBestLatency(): ServerConfig?

    @Query("SELECT * FROM server_configs ORDER BY latency ASC, sortOrder ASC")
    suspend fun getAllDirect(): List<ServerConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ServerConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ServerConfig): Long

    @Update
    suspend fun update(config: ServerConfig)

    @Query("UPDATE server_configs SET latency = :latency WHERE id = :id")
    suspend fun updateLatency(id: Long, latency: Long)

    @Query("UPDATE server_configs SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE server_configs SET isSelected = 1 WHERE id = :id")
    suspend fun select(id: Long)

    @Query("DELETE FROM server_configs WHERE subscriptionId = :subId")
    suspend fun deleteBySubscription(subId: Long)

    @Delete
    suspend fun delete(config: ServerConfig)

    @Query("SELECT COUNT(*) FROM server_configs WHERE subscriptionId = :subId")
    suspend fun countBySubscription(subId: Long): Int
}
