package com.wahyuzero.replyforge.engine

import android.util.Log
import com.wahyuzero.replyforge.ui.rule.MatchType
import java.text.Normalizer

object PatternMatcher {

    data class MatchResult(
        val matched: Boolean,
        val groups: List<String> = emptyList()
    )

    // M5: Cache compiled Regex objects to avoid re-compilation on every notification
    private val regexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private const val REGEX_CACHE_MAX = 64

    private fun getCachedRegex(pattern: String, options: Set<RegexOption>): Regex {
        val key = "${options}:${pattern}"
        return regexCache.computeIfAbsent(key) {
            Regex(pattern, options)
        }.also {
            if (regexCache.size > REGEX_CACHE_MAX) {
                val iter = regexCache.entries.iterator()
                var removed = 0
                while (iter.hasNext() && removed < REGEX_CACHE_MAX / 2) {
                    iter.next()
                    iter.remove()
                    removed++
                }
            }
        }
    }

    fun matchPattern(
        pattern: String,
        message: String,
        matchType: MatchType,
        caseSensitive: Boolean,
        ignoreAccents: Boolean = false,
        similarityThreshold: Int = 0
    ): Boolean {
        return matchPatternWithGroups(pattern, message, matchType, caseSensitive, ignoreAccents, similarityThreshold).matched
    }

    fun matchPatternWithGroups(
        pattern: String,
        message: String,
        matchType: MatchType,
        caseSensitive: Boolean,
        ignoreAccents: Boolean = false,
        similarityThreshold: Int = 0
    ): MatchResult {
        if (pattern.isBlank() || message.isBlank()) return MatchResult(false)

        // Fuzzy matching via similarity threshold
        if (similarityThreshold > 0) {
            val p = preprocessText(pattern, caseSensitive, ignoreAccents)
            val m = preprocessText(message, caseSensitive, ignoreAccents)
            val similarity = calculateSimilarity(p, m)
            return MatchResult(similarity >= similarityThreshold / 100.0)
        }

        // caseSensitive takes precedence when explicitly true
        val useCaseInsensitive = !caseSensitive

        // For regex, handle groups
        if (matchType == MatchType.REGEX) {
            return matchRegexWithGroups(pattern, message, useCaseInsensitive)
        }

        val p = preprocessText(pattern, caseSensitive, ignoreAccents)
        val m = preprocessText(message, caseSensitive, ignoreAccents)

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

    // Two-row DP optimization — O(min(m,n)) memory instead of O(m*n)
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0) return n
        if (n == 0) return m

        // Use shorter string as inner loop for less memory
        val (longer, shorter) = if (m >= n) s1 to s2 else s2 to s1
        val longLen = maxOf(m, n)
        val shortLen = minOf(m, n)

        var prev = IntArray(shortLen + 1) { it }
        var curr = IntArray(shortLen + 1)

        for (i in 1..longLen) {
            curr[0] = i
            for (j in 1..shortLen) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost // substitution
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[shortLen]
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
        if (pattern.contains("*") || pattern.contains("?")) {
            return matchWildcard(pattern, message)
        }
        return message.startsWith(pattern)
    }

    private fun matchEndsWith(pattern: String, message: String): Boolean {
        if (pattern.contains("*") || pattern.contains("?")) {
            return matchWildcard(pattern, message)
        }
        return message.endsWith(pattern)
    }

    private fun matchRegexWithGroups(pattern: String, message: String, caseInsensitive: Boolean): MatchResult {
        return try {
            val options = if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
            val regex = getCachedRegex(pattern, options)
            val matchResult = regex.find(message)
            if (matchResult != null) {
                val groups = matchResult.groupValues.drop(1) // drop full match at index 0
                MatchResult(true, groups)
            } else {
                MatchResult(false)
            }
        } catch (e: Exception) {
            Log.w("PatternMatcher", "Invalid regex pattern: $pattern", e)
            MatchResult(false)
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
            Log.w("PatternMatcher", "Invalid wildcard pattern: $pattern", e)
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
