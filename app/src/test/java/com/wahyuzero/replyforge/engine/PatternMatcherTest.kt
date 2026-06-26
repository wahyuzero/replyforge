package com.wahyuzero.replyforge.engine

import com.wahyuzero.replyforge.ui.rule.MatchType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test

class PatternMatcherTest {

    // ======================== EXACT ========================

    @Test
    fun `EXACT match - identical strings`() {
        val result = PatternMatcher.matchPattern("hello", "hello", MatchType.EXACT, false)
        assertThat(result, `is`(true))
    }

    @Test
    fun `EXACT match - different strings`() {
        val result = PatternMatcher.matchPattern("hello", "world", MatchType.EXACT, false)
        assertThat(result, `is`(false))
    }

    @Test
    fun `EXACT match - case insensitive`() {
        val result = PatternMatcher.matchPattern("Hello", "HELLO", MatchType.EXACT, false)
        assertThat(result, `is`(true))
    }

    @Test
    fun `EXACT match - case sensitive mismatch`() {
        val result = PatternMatcher.matchPattern("Hello", "HELLO", MatchType.EXACT, true)
        assertThat(result, `is`(false))
    }

    // ======================== CONTAINS ========================

    @Test
    fun `CONTAINS - substring present`() {
        assertThat(PatternMatcher.matchPattern("ello", "hello world", MatchType.CONTAINS, false), `is`(true))
    }

    @Test
    fun `CONTAINS - substring absent`() {
        assertThat(PatternMatcher.matchPattern("xyz", "hello world", MatchType.CONTAINS, false), `is`(false))
    }

    @Test
    fun `CONTAINS - wildcard star`() {
        assertThat(PatternMatcher.matchPattern("hel*orld", "hello world", MatchType.CONTAINS, false), `is`(true))
    }

    @Test
    fun `CONTAINS - wildcard question mark single char`() {
        // ? = any single char; full message must match pattern
        assertThat(PatternMatcher.matchPattern("h?llo", "hallo", MatchType.CONTAINS, false), `is`(true))
    }

    // ======================== STARTS_WITH ========================

    @Test
    fun `STARTS_WITH - prefix match`() {
        assertThat(PatternMatcher.matchPattern("hello", "hello world", MatchType.STARTS_WITH, false), `is`(true))
    }

    @Test
    fun `STARTS_WITH - no prefix`() {
        assertThat(PatternMatcher.matchPattern("world", "hello world", MatchType.STARTS_WITH, false), `is`(false))
    }

    // ======================== ENDS_WITH ========================

    @Test
    fun `ENDS_WITH - suffix match`() {
        assertThat(PatternMatcher.matchPattern("world", "hello world", MatchType.ENDS_WITH, false), `is`(true))
    }

    @Test
    fun `ENDS_WITH - no suffix`() {
        assertThat(PatternMatcher.matchPattern("hello", "hello world", MatchType.ENDS_WITH, false), `is`(false))
    }

    // ======================== REGEX ========================

    @Test
    fun `REGEX - simple pattern`() {
        assertThat(PatternMatcher.matchPattern("hel{2}o", "hello", MatchType.REGEX, false), `is`(true))
    }

    @Test
    fun `REGEX - digit pattern`() {
        assertThat(PatternMatcher.matchPattern("\\d+", "abc123def", MatchType.REGEX, false), `is`(true))
    }

    @Test
    fun `REGEX - no match`() {
        assertThat(PatternMatcher.matchPattern("^abc$", "xyz", MatchType.REGEX, false), `is`(false))
    }

    @Test
    fun `REGEX - invalid pattern returns false gracefully`() {
        assertThat(PatternMatcher.matchPattern("[invalid", "test", MatchType.REGEX, false), `is`(false))
    }

    @Test
    fun `REGEX - capturing groups extracted`() {
        val result = PatternMatcher.matchPatternWithGroups(
            "(\\d+)-(\\d+)", "Order 123-456 done", MatchType.REGEX, false
        )
        assertThat(result.matched, `is`(true))
        assertThat(result.groups, hasSize(2))
        assertThat(result.groups[0], equalTo("123"))
        assertThat(result.groups[1], equalTo("456"))
    }

    @Test
    fun `REGEX - case insensitive flag`() {
        assertThat(PatternMatcher.matchPattern("HELLO", "hello world", MatchType.REGEX, false), `is`(true))
    }

    @Test
    fun `REGEX - case sensitive flag`() {
        assertThat(PatternMatcher.matchPattern("HELLO", "hello world", MatchType.REGEX, true), `is`(false))
    }

    // ======================== MATCH_ALL ========================

    @Test
    fun `MATCH_ALL - all words present`() {
        assertThat(PatternMatcher.matchPattern("hello world", "hello beautiful world", MatchType.MATCH_ALL, false), `is`(true))
    }

    @Test
    fun `MATCH_ALL - one word missing`() {
        assertThat(PatternMatcher.matchPattern("hello earth", "hello beautiful world", MatchType.MATCH_ALL, false), `is`(false))
    }

    // ======================== MATCH_ANY ========================

    @Test
    fun `MATCH_ANY - one word present`() {
        assertThat(PatternMatcher.matchPattern("hello earth", "hello beautiful world", MatchType.MATCH_ANY, false), `is`(true))
    }

    @Test
    fun `MATCH_ANY - no words present`() {
        assertThat(PatternMatcher.matchPattern("mars venus", "hello beautiful world", MatchType.MATCH_ANY, false), `is`(false))
    }

    // ======================== EDGE CASES ========================

    @Test
    fun `empty pattern returns false`() {
        assertThat(PatternMatcher.matchPattern("", "hello", MatchType.CONTAINS, false), `is`(false))
    }

    @Test
    fun `empty message returns false`() {
        assertThat(PatternMatcher.matchPattern("hello", "", MatchType.CONTAINS, false), `is`(false))
    }

    @Test
    fun `both empty returns false`() {
        assertThat(PatternMatcher.matchPattern("", "", MatchType.CONTAINS, false), `is`(false))
    }

    // ======================== ACCENT HANDLING ========================

    @Test
    fun `ignoreAccents strips diacritics`() {
        assertThat(
            PatternMatcher.matchPattern("cafe", "café latte", MatchType.CONTAINS, false, ignoreAccents = true),
            `is`(true)
        )
    }

    @Test
    fun `without ignoreAccents accent matters`() {
        assertThat(
            PatternMatcher.matchPattern("cafe", "café latte", MatchType.CONTAINS, false, ignoreAccents = false),
            `is`(false)
        )
    }

    // ======================== FUZZY MATCHING ========================

    @Test
    fun `fuzzy match - high similarity passes threshold`() {
        // "hello" vs "hello" = 100% similarity
        assertThat(
            PatternMatcher.matchPattern("hello", "hello", MatchType.CONTAINS, false, similarityThreshold = 80),
            `is`(true)
        )
    }

    @Test
    fun `fuzzy match - low similarity fails threshold`() {
        // "hello" vs "world" = 20% similarity
        assertThat(
            PatternMatcher.matchPattern("hello", "world", MatchType.CONTAINS, false, similarityThreshold = 80),
            `is`(false)
        )
    }

    @Test
    fun `fuzzy match - close enough passes`() {
        // "hello" vs "hallo" = 80% similarity (1 char diff out of 5)
        assertThat(
            PatternMatcher.matchPattern("hello", "hallo", MatchType.CONTAINS, false, similarityThreshold = 75),
            `is`(true)
        )
    }

    // ======================== WILDCARD EDGE CASES ========================

    @Test
    fun `wildcard - full wildcard matches anything`() {
        assertThat(PatternMatcher.matchPattern("*", "anything at all", MatchType.CONTAINS, false), `is`(true))
    }

    @Test
    fun `wildcard - special chars escaped`() {
        assertThat(PatternMatcher.matchPattern("a.b", "axb", MatchType.CONTAINS, false), `is`(false))
    }
}
