package com.wahyuzero.replyforge.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holidays",
    indices = [Index("date")]
)
@Keep
data class Holiday(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val date: String, // yyyy-MM-dd
    val isRecurringAnnual: Boolean = false
)
