package com.wahyuzero.replyforge.network

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class AiDataModelsTest {

    @Test
    fun `ChatCompletionRequest has correct fields`() {
        val req = ChatCompletionRequest(
            model = "gpt-4o",
            messages = listOf(ChatMessage("user", "Hello")),
            max_tokens = 100,
            temperature = 0.7f
        )
        assertThat(req.model, equalTo("gpt-4o"))
        assertThat(req.messages, hasSize(1))
        assertThat(req.max_tokens, equalTo(100))
        assertThat(req.temperature, equalTo(0.7f))
    }

    @Test
    fun `ChatCompletionRequest nullables default null`() {
        val req = ChatCompletionRequest(
            model = "gpt-4",
            messages = emptyList()
        )
        assertThat(req.max_tokens, nullValue())
        assertThat(req.temperature, nullValue())
    }

    @Test
    fun `ChatMessage fields`() {
        val msg = ChatMessage("system", "You are helpful")
        assertThat(msg.role, equalTo("system"))
        assertThat(msg.content, equalTo("You are helpful"))
    }

    @Test
    fun `ChatCompletionResponse with full data`() {
        val resp = ChatCompletionResponse(
            id = "chatcmpl-123",
            choices = listOf(
                ChatChoice(
                    index = 0,
                    message = ChatMessage("assistant", "Hi there!"),
                    finishReason = "stop"
                )
            ),
            usage = ChatUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
        )
        assertThat(resp.id, equalTo("chatcmpl-123"))
        assertThat(resp.choices, hasSize(1))
        assertThat(resp.choices!![0].message!!.content, equalTo("Hi there!"))
        assertThat(resp.usage!!.totalTokens, equalTo(15))
    }

    @Test
    fun `ChatCompletionResponse with null fields`() {
        val resp = ChatCompletionResponse()
        assertThat(resp.id, nullValue())
        assertThat(resp.choices, nullValue())
        assertThat(resp.usage, nullValue())
    }

    @Test
    fun `ChatChoice defaults`() {
        val choice = ChatChoice()
        assertThat(choice.index, equalTo(0))
        assertThat(choice.message, nullValue())
        assertThat(choice.finishReason, nullValue())
    }

    @Test
    fun `ChatUsage defaults to zero`() {
        val usage = ChatUsage()
        assertThat(usage.promptTokens, equalTo(0))
        assertThat(usage.completionTokens, equalTo(0))
        assertThat(usage.totalTokens, equalTo(0))
    }
}
