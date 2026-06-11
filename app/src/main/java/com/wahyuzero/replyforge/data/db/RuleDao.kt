package com.wahyuzero.replyforge.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wahyuzero.replyforge.data.model.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY priority DESC, createdAt ASC")
    fun getAllRules(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, createdAt ASC")
    fun getEnabledRules(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): Rule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM rules WHERE enabled = 1")
    suspend fun getEnabledCount(): Int

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM rules")
    suspend fun deleteAll()
}
