package com.planmyplate.data.repository

import com.planmyplate.data.SyncLogDao
import com.planmyplate.model.SyncLog
import kotlinx.coroutines.flow.Flow

class SyncLogRepository(
    private val syncLogDao: SyncLogDao
) {
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