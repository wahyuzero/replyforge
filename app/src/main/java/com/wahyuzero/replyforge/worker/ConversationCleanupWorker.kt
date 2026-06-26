package com.wahyuzero.replyforge.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wahyuzero.replyforge.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic worker to clean up old conversation messages beyond retention period.
 * Runs daily (via WorkManager periodic request) to keep DB size bounded.
 */
class ConversationCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ConvCleanupWorker"
        const val RETENTION_DAYS = 30L
        const val WORK_NAME = "conversation_cleanup"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val cutoffTime = System.currentTimeMillis() - (RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val deleted = db.conversationDao().deleteOlderThan(cutoffTime)
            Log.i(TAG, "Cleaned up $deleted conversation messages older than $RETENTION_DAYS days")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Conversation cleanup failed", e)
            Result.retry()
        }
    }
}
