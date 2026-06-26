package com.wahyuzero.replyforge.data.model

import com.wahyuzero.replyforge.ui.rule.MatchType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class RuleTest {

    @Test
    fun `default values are correct`() {
        val rule = Rule(name = "Test", pattern = "hello", response = "Hi")
        assertThat(rule.id, equalTo(0L))
        assertThat(rule.matchType, equalTo(MatchType.CONTAINS))
        assertThat(rule.isRegex, `is`(false))
        assertThat(rule.enabled, `is`(true))
        assertThat(rule.contactFilter, equalTo(ContactFilter.ALL))
        assertThat(rule.responseMode, equalTo(ResponseMode.SINGLE))
        assertThat(rule.probability, equalTo(100))
        assertThat(rule.lineBreaks, `is`(true))
        assertThat(rule.caseInsensitive, `is`(true))
        assertThat(rule.receiverType, equalTo(Rule.RECEIVER_BOTH))
        assertThat(rule.activeDays, equalTo("1,2,3,4,5,6,7"))
    }

    @Test
    fun `receiver type constants are correct`() {
        assertThat(Rule.RECEIVER_BOTH, equalTo(0))
        assertThat(Rule.RECEIVER_CONTACTS, equalTo(1))
        assertThat(Rule.RECEIVER_GROUPS, equalTo(2))
    }

    @Test
    fun `copy with modified fields preserves others`() {
        val original = Rule(name = "Test", pattern = "hello", response = "Hi")
        val modified = original.copy(enabled = false)
        assertThat(modified.enabled, `is`(false))
        assertThat(modified.name, equalTo("Test"))
        assertThat(modified.pattern, equalTo("hello"))
    }

    @Test
    fun `two rules with same content are equal`() {
        val r1 = Rule(id = 1, name = "A", pattern = "p", response = "r")
        val r2 = Rule(id = 1, name = "A", pattern = "p", response = "r")
        assertThat(r1, equalTo(r2))
    }
}

class ReplyHistoryTest {

    @Test
    fun `default values are correct`() {
        val h = ReplyHistory(sender = "John", message = "Hi", response = "Hello")
        assertThat(h.id, equalTo(0L))
        assertThat(h.ruleId, nullValue())
        assertThat(h.isGroup, `is`(false))
        assertThat(h.groupName, nullValue())
        assertThat(h.processTimeMs, equalTo(0L))
    }

    @Test
    fun `group reply has correct fields`() {
        val h = ReplyHistory(
            sender = "Alice",
            message = "Test",
            response = "Reply",
            isGroup = true,
            groupName = "Dev Team"
        )
        assertThat(h.isGroup, `is`(true))
        assertThat(h.groupName, equalTo("Dev Team"))
    }
}

class HolidayTest {

    @Test
    fun `holiday entity fields`() {
        val h = Holiday(name = "Tahun Baru", date = "2026-01-01", isRecurringAnnual = true)
        assertThat(h.name, equalTo("Tahun Baru"))
        assertThat(h.date, equalTo("2026-01-01"))
        assertThat(h.isRecurringAnnual, `is`(true))
    }
}

class AiProviderTest {

    @Test
    fun `default values are correct`() {
        val p = AiProvider(name = "Test", baseUrl = "https://api.test.com", apiKey = "key", modelName = "gpt-4")
        assertThat(p.type, equalTo(AiProviderType.OPENAI))
        assertThat(p.isActive, `is`(true))
        assertThat(p.maxTokens, equalTo(1024))
        assertThat(p.temperature, equalTo(0.7f))
    }
}
