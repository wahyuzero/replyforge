package com.wahyuzero.replyforge.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface for OpenAI-compatible Chat Completions API.
 * Works with OpenAI, Gemini (OpenAI-compatible endpoint), and custom providers.
 */
interface AiApiClient {

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

// Request models

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int? = null,
    val temperature: Float? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

// Response models

data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ChatChoice>? = null,
    val usage: ChatUsage? = null
)

data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class ChatUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)
