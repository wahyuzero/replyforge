package com.wahyuzero.replyforge.data.db

import com.wahyuzero.replyforge.data.model.ContactFilter
import com.wahyuzero.replyforge.data.model.ResponseMode
import com.wahyuzero.replyforge.data.model.AiProviderType
import com.wahyuzero.replyforge.data.model.MessageRole
import com.wahyuzero.replyforge.ui.rule.MatchType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    // ======================== MatchType ========================

    @Test
    fun `MatchType round-trip CONTAINS`() {
        val result = converters.toMatchType(converters.fromMatchType(MatchType.CONTAINS))
        assertThat(result, equalTo(MatchType.CONTAINS))
    }

    @Test
    fun `MatchType round-trip REGEX`() {
        val result = converters.toMatchType(converters.fromMatchType(MatchType.REGEX))
        assertThat(result, equalTo(MatchType.REGEX))
    }

    @Test
    fun `MatchType fallback on invalid value`() {
        assertThat(converters.toMatchType("INVALID"), equalTo(MatchType.CONTAINS))
    }

    @Test
    fun `MatchType fallback on empty string`() {
        assertThat(converters.toMatchType(""), equalTo(MatchType.CONTAINS))
    }

    // ======================== ContactFilter ========================

    @Test
    fun `ContactFilter round-trip SPECIFIC`() {
        val result = converters.toContactFilter(converters.fromContactFilter(ContactFilter.SPECIFIC))
        assertThat(result, equalTo(ContactFilter.SPECIFIC))
    }

    @Test
    fun `ContactFilter round-trip EXCLUDE`() {
        val result = converters.toContactFilter(converters.fromContactFilter(ContactFilter.EXCLUDE))
        assertThat(result, equalTo(ContactFilter.EXCLUDE))
    }

    @Test
    fun `ContactFilter fallback on invalid`() {
        assertThat(converters.toContactFilter("GARBAGE"), equalTo(ContactFilter.ALL))
    }

    // ======================== ResponseMode ========================

    @Test
    fun `ResponseMode round-trip RANDOM`() {
        val result = converters.toResponseMode(converters.fromResponseMode(ResponseMode.RANDOM))
        assertThat(result, equalTo(ResponseMode.RANDOM))
    }

    @Test
    fun `ResponseMode round-trip SEQUENTIAL`() {
        val result = converters.toResponseMode(converters.fromResponseMode(ResponseMode.SEQUENTIAL))
        assertThat(result, equalTo(ResponseMode.SEQUENTIAL))
    }

    @Test
    fun `ResponseMode fallback on invalid`() {
        assertThat(converters.toResponseMode("UNKNOWN"), equalTo(ResponseMode.SINGLE))
    }

    // ======================== AiProviderType ========================

    @Test
    fun `AiProviderType round-trip OPENAI`() {
        val result = converters.toAiProviderType(converters.fromAiProviderType(AiProviderType.OPENAI))
        assertThat(result, equalTo(AiProviderType.OPENAI))
    }

    @Test
    fun `AiProviderType fallback on invalid`() {
        assertThat(converters.toAiProviderType("NONEXISTENT"), equalTo(AiProviderType.OPENAI))
    }

    // ======================== MessageRole ========================

    @Test
    fun `MessageRole round-trip ASSISTANT`() {
        val result = converters.toMessageRole(converters.fromMessageRole(MessageRole.ASSISTANT))
        assertThat(result, equalTo(MessageRole.ASSISTANT))
    }

    @Test
    fun `MessageRole fallback on invalid`() {
        assertThat(converters.toMessageRole("SYSTEM_ROLE"), equalTo(MessageRole.USER))
    }
}
