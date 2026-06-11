package com.wahyuzero.replyforge.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reply_history",
    foreignKeys = [
        ForeignKey(
            entity = Rule::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("ruleId"),
        Index("timestamp"),
        Index("sender")
    ]
)
data class ReplyHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleId: Long? = null,
    val sender: String,
    val message: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val processTimeMs: Long = 0L
)
