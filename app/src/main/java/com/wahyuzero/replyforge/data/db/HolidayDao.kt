package com.wahyuzero.replyforge.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wahyuzero.replyforge.data.model.Holiday
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun getAllHolidays(): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): Holiday?

    @Query("SELECT EXISTS(SELECT 1 FROM holidays WHERE date = :date OR (isRecurringAnnual = 1 AND substr(date, 6) = substr(:date, 6)))")
    suspend fun isHoliday(date: String): Boolean

    @Query("SELECT COUNT(*) FROM holidays")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: Holiday): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<Holiday>)

    @Delete
    suspend fun delete(holiday: Holiday)

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM holidays")
    suspend fun deleteAll()
}
