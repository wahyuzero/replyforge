package com.wahyuzero.replyforge.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wahyuzero.replyforge.data.model.ReplyHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM reply_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ReplyHistory>>

    @Query("SELECT * FROM reply_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<ReplyHistory>

    @Query("SELECT * FROM reply_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReplyHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ReplyHistory): Long

    @Delete
    suspend fun delete(history: ReplyHistory)

    @Query("DELETE FROM reply_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reply_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM reply_history")
    suspend fun getCount(): Int

    @Query("SELECT * FROM reply_history WHERE sender LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%' OR response LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<ReplyHistory>>

    @Query("SELECT * FROM reply_history WHERE isGroup = :isGroup ORDER BY timestamp DESC")
    fun filterByGroup(isGroup: Boolean): Flow<List<ReplyHistory>>

    @Query("SELECT * FROM reply_history WHERE timestamp >= :fromTimestamp AND timestamp <= :toTimestamp ORDER BY timestamp DESC")
    fun filterByDateRange(fromTimestamp: Long, toTimestamp: Long): Flow<List<ReplyHistory>>

    // Stats queries
    @Query("SELECT COUNT(*) FROM reply_history")
    suspend fun getTotalReplies(): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getRepliesToday(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE isGroup = 0")
    suspend fun getRepliesToContacts(): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE isGroup = 1")
    suspend fun getRepliesToGroups(): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE ruleId = :ruleId AND timestamp >= :startOfDay AND timestamp < :endOfDay")
    suspend fun getRuleRepliesToday(ruleId: Long, startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT MAX(timestamp) FROM reply_history WHERE ruleId = :ruleId AND sender = :sender")
    suspend fun getLastReplyTime(ruleId: Long, sender: String): Long?

    @Query("SELECT MAX(timestamp) FROM reply_history WHERE sender = :sender")
    suspend fun getLastReplyForContact(sender: String): Long?
}
