package com.planmyplate.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.planmyplate.model.SyncLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(syncLog: SyncLog)

    @Query("SELECT * FROM sync_logs ORDER BY createdAt DESC")
    fun getAllSyncLogs(): Flow<List<SyncLog>>
}