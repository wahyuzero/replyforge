package com.wahyuzero.replyforge.engine

import com.wahyuzero.replyforge.ui.rule.MatchType
import java.text.Normalizer

object PatternMatcher {

    data class MatchResult(
        val matched: Boolean,
        val groups: List<String> = emptyList()
    )

    fun matchPattern(
        pattern: String,
        message: String,
        matchType: MatchType,
        caseSensitive: Boolean,
        caseInsensitive: Boolean = true,
        ignoreAccents: Boolean = false,
        similarityThreshold: Int = 0
    ): Boolean {
        return matchPatternWithGroups(pattern, message, matchType, caseSensitive, caseInsensitive, ignoreAccents, similarityThreshold).matched
    }

    fun matchPatternWithGroups(
        pattern: String,
        message: String,
        matchType: MatchType,
        caseSensitive: Boolean,
        caseInsensitive: Boolean = true,
        ignoreAccents: Boolean = false,
        similarityThreshold: Int = 0
    ): MatchResult {
        if (pattern.isBlank() || message.isBlank()) return MatchResult(false)

        // Fuzzy matching via similarity threshold
        if (similarityThreshold > 0) {
            val p = preprocessText(pattern, caseSensitive, caseInsensitive, ignoreAccents)
            val m = preprocessText(message, caseSensitive, caseInsensitive, ignoreAccents)
            val similarity = calculateSimilarity(p, m)
            return MatchResult(similarity >= similarityThreshold / 100.0)
        }

        // caseSensitive takes precedence when explicitly true
        val useCaseInsensitive = !caseSensitive

        // For regex, handle groups
        if (matchType == MatchType.REGEX) {
            return matchRegexWithGroups(pattern, message, useCaseInsensitive)
        }

        val p = preprocessText(pattern, caseSensitive, caseInsensitive, ignoreAccents)
        val m = preprocessText(message, caseSensitive, caseInsensitive, ignoreAccents)

        val matched = when (matchType) {
            MatchType.EXACT -> matchExact(p, m)
            MatchType.CONTAINS -> matchContains(p, m)
            MatchType.STARTS_WITH -> matchStartsWith(p, m)
            MatchType.ENDS_WITH -> matchEndsWith(p, m)
            MatchType.REGEX -> false // handled above
            MatchType.MATCH_ALL -> matchAllWords(p, m)
            MatchType.MATCH_ANY -> matchAnyWord(p, m)
        }

        return MatchResult(matched)
    }

    private fun preprocessText(
        text: String,
        caseSensitive: Boolean,
        caseInsensitive: Boolean,
        ignoreAccents: Boolean
    ): String {
        var result = text
        // Apply case folding — caseSensitive flag is the single source of truth
        if (!caseSensitive) {
            result = result.lowercase()
        }
        // Strip accents/diacritics
        if (ignoreAccents) {
            result = stripAccents(result)
        }
        return result
    }

    private fun stripAccents(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    // Levenshtein distance based similarity
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val distance = levenshteinDistance(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[m][n]
    }

    private fun matchExact(pattern: String, message: String): Boolean {
        return pattern == message
    }

    private fun matchContains(pattern: String, message: String): Boolean {
        if (pattern.contains("*") || pattern.contains("?")) {
            return matchWildcard(pattern, message)
        }
        return message.contains(pattern)
    }

    private fun matchStartsWith(pattern: String, message: String): Boolean {
        return message.startsWith(pattern)
    }

    private fun matchEndsWith(pattern: String, message: String): Boolean {
        return message.endsWith(pattern)
    }

    private fun matchRegexWithGroups(pattern: String, message: String, caseInsensitive: Boolean): MatchResult {
        return try {
            val options = if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
            val regex = Regex(pattern, options)
            val matchResult = regex.find(message)
            if (matchResult != null) {
                val groups = matchResult.groupValues.drop(1) // drop full match at index 0
                MatchResult(true, groups)
            } else {
                MatchResult(false)
            }
        } catch (e: Exception) {
            MatchResult(false)
        }
    }

    private fun matchRegex(pattern: String, message: String, caseSensitive: Boolean): Boolean {
        return try {
            val options = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            val regex = Regex(pattern, options)
            regex.containsMatchIn(message)
        } catch (e: Exception) {
            false
        }
    }

    private fun matchWildcard(pattern: String, message: String): Boolean {
        val regexPattern = buildString {
            append('^')
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    '.', '^', '$', '+', '[', ']', '(', ')', '{', '}', '\\', '|' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
            append('$')
        }
        return try {
            val regex = Regex(regexPattern)
            regex.matches(message)
        } catch (e: Exception) {
            false
        }
    }

    private fun matchAllWords(pattern: String, message: String): Boolean {
        val words = pattern.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return words.all { message.contains(it) }
    }

    private fun matchAnyWord(pattern: String, message: String): Boolean {
        val words = pattern.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return words.any { message.contains(it) }
    }
}
