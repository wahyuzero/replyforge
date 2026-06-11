package com.wahyuzero.replyforge.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AiProviderType {
    OPENAI,
    GEMINI,
    CUSTOM
}

@Entity(
    tableName = "ai_providers",
    indices = [
        Index("isActive"),
        Index("type")
    ]
)
@Keep
data class AiProvider(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AiProviderType = AiProviderType.OPENAI,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isActive: Boolean = true,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f
)
