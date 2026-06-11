package com.wahyuzero.replyforge.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wahyuzero.replyforge.data.model.AiProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface AiProviderDao {

    @Query("SELECT * FROM ai_providers ORDER BY name ASC")
    fun getAllProviders(): Flow<List<AiProvider>>

    @Query("SELECT * FROM ai_providers WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveProviders(): Flow<List<AiProvider>>

    @Query("SELECT * FROM ai_providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: Long): AiProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: AiProvider): Long

    @Update
    suspend fun update(provider: AiProvider)

    @Delete
    suspend fun delete(provider: AiProvider)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE ai_providers SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("SELECT COUNT(*) FROM ai_providers")
    suspend fun getCount(): Int
}
