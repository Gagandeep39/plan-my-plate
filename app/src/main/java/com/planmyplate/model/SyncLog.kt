package com.planmyplate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val service: String,
    val action: String,
    val status: String,
    val source: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sessionId: Long? = null
) {
    companion object {
        const val SERVICE_CALENDAR = "Google Calendar"
        const val SERVICE_DRIVE = "Google Drive"

        const val SOURCE_QUEUE = "Queue"
        const val SOURCE_DIRECT = "Direct"

        const val STATUS_SUCCESS = "Success"
        const val STATUS_FAILURE = "Failure"
        const val STATUS_SKIPPED = "Skipped"
    }
}