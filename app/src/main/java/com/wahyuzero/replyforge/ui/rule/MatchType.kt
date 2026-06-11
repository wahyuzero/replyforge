package com.wahyuzero.replyforge.ui.rule

enum class MatchType(val displayName: String) {
    EXACT("Exact Match"),
    CONTAINS("Contains"),
    STARTS_WITH("Starts With"),
    ENDS_WITH("Ends With"),
    REGEX("Regular Expression"),
    MATCH_ALL("Match All Words"),
    MATCH_ANY("Match Any Word")
}
