package com.wahyuzero.replyforge.data.db

import androidx.annotation.Keep
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "rate_limits",
    primaryKeys = ["ruleId", "contactName"]
)
@Keep
data class RateLimitEntry(
    val ruleId: Long,
    val contactName: String,
    val lastReplyTime: Long = 0L,
    val replyCountToday: Int = 0,
    val lastResetDate: String = "" // yyyy-MM-dd
)

@Dao
interface RateLimitDao {

    @Query("SELECT * FROM rate_limits WHERE ruleId = :ruleId AND contactName = :contactName LIMIT 1")
    suspend fun getEntry(ruleId: Long, contactName: String): RateLimitEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RateLimitEntry)

    @Query("DELETE FROM rate_limits")
    suspend fun deleteAll()
}
