package com.wahyuzero.replyforge.data.db

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class HolidayDataTest {

    @Test
    fun `HOLIDAY_DATA is not empty`() {
        assertThat(HOLIDAY_DATA, not(empty()))
    }

    @Test
    fun `HOLIDAY_DATA contains 2026 new year`() {
        val newYear = HOLIDAY_DATA.find { it.first == "Tahun Baru 2026" }
        assertThat(newYear, notNullValue())
        assertThat(newYear!!.second, equalTo("2026-01-01"))
        assertThat(newYear.third, `is`(true))
    }

    @Test
    fun `HOLIDAY_DATA contains Indonesian Independence Day`() {
        val independence = HOLIDAY_DATA.find { it.first.contains("Kemerdekaan") }
        assertThat(independence, notNullValue())
        assertThat(independence!!.second, equalTo("2026-08-17"))
        assertThat(independence.third, `is`(true))
    }

    @Test
    fun `all holiday dates are valid yyyy-MM-dd format`() {
        val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
        for ((name, date, _) in HOLIDAY_DATA) {
            assertThat("Holiday '$name' has invalid date: $date", dateRegex.matches(date), `is`(true))
        }
    }

    @Test
    fun `all holiday dates are unique`() {
        val dates = HOLIDAY_DATA.map { it.second }
        assertThat("Duplicate dates found", dates.toSet().size, equalTo(dates.size))
    }
}

class RateLimitEntryTest {

    @Test
    fun `default values`() {
        val entry = RateLimitEntry(ruleId = 1, contactName = "John")
        assertThat(entry.lastReplyTime, equalTo(0L))
        assertThat(entry.replyCountToday, equalTo(0))
        assertThat(entry.lastResetDate, equalTo(""))
    }

    @Test
    fun `equality based on all fields`() {
        val e1 = RateLimitEntry(1, "Alice", 1000L, 3, "2026-01-01")
        val e2 = RateLimitEntry(1, "Alice", 1000L, 3, "2026-01-01")
        assertThat(e1, equalTo(e2))
    }
}
