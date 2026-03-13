package com.planmyplate.data.repository

import android.content.Context
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.SyncLogDao
import com.planmyplate.model.SyncLog
import kotlinx.coroutines.flow.Flow

class SyncLogRepository(
    private val context: Context
) {
    private val syncLogDao: SyncLogDao
        get() = AppDatabase.getDatabase(context).syncLogDao()

    fun getAllSyncLogs(): Flow<List<SyncLog>> = syncLogDao.getAllSyncLogs()

    suspend fun log(
        service: String,
        action: String,
        status: String,
        source: String,
        message: String,
        sessionId: Long? = null
    ) {
        syncLogDao.insertSyncLog(
            SyncLog(
                service = service,
                action = action,
                status = status,
                source = source,
                message = message,
                sessionId = sessionId
            )
        )
    }
}
