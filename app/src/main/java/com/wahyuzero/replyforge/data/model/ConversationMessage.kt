package com.wahyuzero.replyforge.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole {
    USER,
    ASSISTANT
}

@Entity(
    tableName = "conversation_messages",
    indices = [
        Index("contactName"),
        Index("timestamp")
    ]
)
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
