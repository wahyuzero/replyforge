package com.wahyuzero.replyforge.network

import android.util.Log
import com.wahyuzero.replyforge.BuildConfig
import com.wahyuzero.replyforge.data.db.AiUsageDao
import com.wahyuzero.replyforge.data.db.ConversationDao
import com.wahyuzero.replyforge.data.model.AiProvider
import com.wahyuzero.replyforge.data.model.AiProviderType
import com.wahyuzero.replyforge.data.model.AiUsage
import com.wahyuzero.replyforge.data.model.ConversationMessage
import com.wahyuzero.replyforge.data.model.MessageRole
import com.wahyuzero.replyforge.engine.AutoReplyEngine
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * High-level AI service: calls AI API, handles errors, parses response,
 * manages conversation memory and token usage tracking.
 */
class AiService(
    private val conversationDao: ConversationDao,
    private val aiUsageDao: AiUsageDao
) {
    companion object {
        private const val TAG = "AiService"
        private const val DEFAULT_CONTEXT_WINDOW = 20
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful WhatsApp auto-reply assistant. Be concise and friendly."

        // Cost per 1M tokens (USD)
        private val COST_RATES = mapOf(
            // GPT-4o
            "gpt-4o" to CostRate(2.50, 10.0),
            "gpt-4o-mini" to CostRate(0.15, 0.60),
            // GPT-3.5
            "gpt-3.5-turbo" to CostRate(0.50, 1.50),
            // GPT-4
            "gpt-4" to CostRate(30.0, 60.0),
            "gpt-4-turbo" to CostRate(10.0, 30.0),
            // Gemini Flash
            "gemini-1.5-flash" to CostRate(0.075, 0.30),
            "gemini-2.0-flash" to CostRate(0.10, 0.40),
            "gemini-1.5-pro" to CostRate(1.25, 5.0),
            "gemini-2.5-flash" to CostRate(0.15, 0.60),
            "gemini-2.5-pro" to CostRate(1.25, 10.0),
        )

        private val DEFAULT_COST_RATE = CostRate(1.0, 3.0)
    }

    data class CostRate(val inputPerMillion: Double, val outputPerMillion: Double)

    data class AiReplyResult(
        val replyText: String,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/") // Placeholder; actual URL is passed per-request
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiClient = retrofit.create(AiApiClient::class.java)

    /**
     * Get AI reply for an incoming message.
     * @param provider The AI provider configuration
     * @param contactName The contact who sent the message
     * @param incomingMessage The incoming message text
     * @param systemPrompt Optional system prompt (from rule), overrides any default
     * @param temperature Optional temperature override (from rule)
     * @param contextWindowSize Max conversation history to include
     * @return AiReplyResult or null on error
     */
    suspend fun getAiReply(
        provider: AiProvider,
        contactName: String,
        incomingMessage: String,
        systemPrompt: String? = null,
        temperature: Float? = null,
        contextWindowSize: Int = DEFAULT_CONTEXT_WINDOW
    ): AiReplyResult? {
        return try {
            // Save incoming message to conversation memory
            conversationDao.insert(
                ConversationMessage(
                    contactName = contactName,
                    role = MessageRole.USER,
                    content = incomingMessage
                )
            )

            // Load conversation history
            val recentMessages = conversationDao.getRecentMessages(
                contactName,
                contextWindowSize
            ).reversed() // Get oldest first

            // Build messages array
            val messages = mutableListOf<ChatMessage>()

            // System prompt
            val sysPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
            messages.add(ChatMessage(role = "system", content = sysPrompt))

            // Add conversation history
            for (msg in recentMessages) {
                messages.add(
                    ChatMessage(
                        role = msg.role.name.lowercase(),
                        content = msg.content
                    )
                )
            }

            // Build request
            val effectiveTemp = temperature ?: provider.temperature
            val request = ChatCompletionRequest(
                model = provider.modelName,
                messages = messages,
                max_tokens = provider.maxTokens,
                temperature = effectiveTemp
            )

            // Build URL
            val url = buildUrl(provider)

            // Call API
            val response = apiClient.chatCompletion(
                url = url,
                authorization = "Bearer ${provider.apiKey}",
                request = request
            )

            // Extract reply with truncation for WhatsApp limit
            val rawContent = response.choices?.firstOrNull()?.message?.content?.trim()
            if (rawContent.isNullOrBlank()) {
                Log.w(TAG, "AI returned empty response")
                // Rollback: remove the user message we saved earlier
                conversationDao.deleteLastMessage(contactName)
                return null
            }
            val replyText = if (rawContent.length > AutoReplyEngine.WHATSAPP_MSG_LIMIT) rawContent.substring(0, AutoReplyEngine.WHATSAPP_MSG_LIMIT) + "…" else rawContent

            // Save assistant response to conversation memory
            conversationDao.insert(
                ConversationMessage(
                    contactName = contactName,
                    role = MessageRole.ASSISTANT,
                    content = replyText
                )
            )

            // Cleanup old messages
            conversationDao.cleanupOldMessages(contactName, contextWindowSize)

            // Record usage
            val usage = response.usage
            if (usage != null) {
                val cost = estimateCost(provider, usage.promptTokens, usage.completionTokens)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
                aiUsageDao.insert(
                    AiUsage(
                        date = today,
                        providerId = provider.id,
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        totalTokens = usage.totalTokens,
                        estimatedCost = cost
                    )
                )
            }

            AiReplyResult(
                replyText = replyText,
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
                totalTokens = usage?.totalTokens ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI API call failed", e)
            null
        }
    }

    private fun buildUrl(provider: AiProvider): String {
        var base = provider.baseUrl.trimEnd('/')
        // Ensure it ends with the chat completions endpoint
        if (!base.endsWith("/v1/chat/completions")) {
            if (!base.endsWith("/v1")) {
                base = "$base/v1"
            }
            base = "$base/chat/completions"
        }
        return base
    }

    private fun estimateCost(provider: AiProvider, promptTokens: Int, completionTokens: Int): Double {
        val modelName = provider.modelName.lowercase()
        val costRate = COST_RATES.entries.find { modelName.contains(it.key) }?.value
            ?: DEFAULT_COST_RATE

        val inputCost = (promptTokens / 1_000_000.0) * costRate.inputPerMillion
        val outputCost = (completionTokens / 1_000_000.0) * costRate.outputPerMillion
        return inputCost + outputCost
    }
}
