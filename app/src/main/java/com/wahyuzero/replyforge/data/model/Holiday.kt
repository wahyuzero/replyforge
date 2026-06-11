package com.wahyuzero.replyforge.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holidays",
    indices = [Index("date")]
)
data class Holiday(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val date: String, // yyyy-MM-dd
    val isRecurringAnnual: Int = 0 // 0=false, 1=true
)
