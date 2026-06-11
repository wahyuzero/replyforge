package com.wahyuzero.replyforge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wahyuzero.replyforge.data.model.AiUsage

@Dao
interface AiUsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: AiUsage): Long

    @Query("SELECT * FROM ai_usage WHERE date = :date ORDER BY id DESC")
    suspend fun getUsageForDate(date: String): List<AiUsage>

    @Query("SELECT * FROM ai_usage WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getUsageForDateRange(startDate: String, endDate: String): List<AiUsage>

    @Query("SELECT SUM(totalTokens) FROM ai_usage WHERE date = :date")
    suspend fun getTotalTokensForDate(date: String): Long?

    @Query("SELECT SUM(estimatedCost) FROM ai_usage WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalCostForDateRange(startDate: String, endDate: String): Double?

    @Query("SELECT SUM(estimatedCost) FROM ai_usage")
    suspend fun getTotalCost(): Double?

    @Query("SELECT SUM(totalTokens) FROM ai_usage")
    suspend fun getTotalTokens(): Long?

    @Query("SELECT SUM(promptTokens) FROM ai_usage")
    suspend fun getTotalPromptTokens(): Long?

    @Query("SELECT SUM(completionTokens) FROM ai_usage")
    suspend fun getTotalCompletionTokens(): Long?

    @Query("DELETE FROM ai_usage")
    suspend fun deleteAll()
}
