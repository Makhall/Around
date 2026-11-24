package com.around.app.data.history

import com.around.app.data.model.HistoryPoint
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeHistory(
        circleId: String,
        uid: String,
        since: Long,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<HistoryPoint>>

    suspend fun appendHistory(
        circleIds: Set<String>,
        uid: String,
        point: HistoryPoint,
    ): Result<Unit>

    suspend fun clearHistory(circleId: String, uid: String): Result<Unit>

    companion object {
        const val DEFAULT_LIMIT = 1_000
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
