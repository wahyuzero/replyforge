package com.wahyuzero.replyforge.ui.rule

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class MatchTypeTest {

    @Test
    fun `all match types exist`() {
        val types = MatchType.values().toList()
        assertThat(types, hasItem(MatchType.EXACT))
        assertThat(types, hasItem(MatchType.CONTAINS))
        assertThat(types, hasItem(MatchType.STARTS_WITH))
        assertThat(types, hasItem(MatchType.ENDS_WITH))
        assertThat(types, hasItem(MatchType.REGEX))
        assertThat(types, hasItem(MatchType.MATCH_ALL))
        assertThat(types, hasItem(MatchType.MATCH_ANY))
    }

    @Test
    fun `all match types count`() {
        assertThat(MatchType.values().size, equalTo(7))
    }

    @Test
    fun `display names are correct`() {
        assertThat(MatchType.EXACT.displayName, equalTo("Exact Match"))
        assertThat(MatchType.CONTAINS.displayName, equalTo("Contains"))
        assertThat(MatchType.STARTS_WITH.displayName, equalTo("Starts With"))
        assertThat(MatchType.ENDS_WITH.displayName, equalTo("Ends With"))
        assertThat(MatchType.REGEX.displayName, equalTo("Regular Expression"))
        assertThat(MatchType.MATCH_ALL.displayName, equalTo("Match All Words"))
        assertThat(MatchType.MATCH_ANY.displayName, equalTo("Match Any Word"))
    }

    @Test
    fun `valueOf works correctly`() {
        assertThat(MatchType.valueOf("EXACT"), equalTo(MatchType.EXACT))
        assertThat(MatchType.valueOf("REGEX"), equalTo(MatchType.REGEX))
    }
}
