package com.wahyuzero.replyforge.data.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wahyuzero.replyforge.ui.rule.MatchType

@Entity(
    tableName = "rules",
    indices = [
        Index("enabled"),
        Index("priority")
    ]
)
@Keep
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pattern: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val response: String,
    val isRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    val contactFilter: ContactFilter = ContactFilter.ALL,
    val contactList: String = "",
    val groupFilter: ContactFilter = ContactFilter.ALL,
    val groupList: String = "",
    val delayMin: Int = 0,
    val delayMax: Int = 0,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val responseMode: ResponseMode = ResponseMode.SINGLE,
    val sequentialIndex: Int = 0,
    val ignorePattern: String = "",
    val ignoreGroups: Boolean = false,
    val ignoreIndividuals: Boolean = false,
    // Phase 3: Time-based scheduling
    val startTime: String? = null,
    val endTime: String? = null,
    val activeDays: String = "1,2,3,4,5,6,7",
    // Phase 3: Rate limiting
    val minDelaySeconds: Int = 0,
    val maxRepliesPerContact: Int = 0,
    // Phase 3: Holiday rules
    val ignoreHolidays: Boolean = false,
    // Phase 4: AI integration
    val useAi: Boolean = false,
    val aiProviderId: Long? = null,
    val systemPrompt: String? = null,
    val aiTemperature: Float? = null,
    // Gap fill: Reply system
    val replyDelayMs: Long = 0L,
    val replyHeader: String? = null,
    val replyFooter: String? = null,
    val replyPrefix: String? = null,
    val probability: Int = 100,
    val lineBreaks: Boolean = true,
    // Gap fill: Contacts/Groups
    val specificContacts: String? = null,
    val specificGroups: String? = null,
    val receiverType: Int = 0, // 0=both, 1=contacts only, 2=groups only
    // Gap fill: Pattern matching
    val caseInsensitive: Boolean = true,
    val ignoreAccents: Boolean = false,
    val similarityThreshold: Int = 0, // 0=disabled, >0=fuzzy match
    // Gap fill: Rate limiting
    val dailyReplyLimit: Int = 0, // 0=unlimited
    val preventRepeatingMs: Long = 0L,
    val prevRuleTimeoutMs: Long = 0L
) {
    companion object {
        const val RECEIVER_BOTH = 0
        const val RECEIVER_CONTACTS = 1
        const val RECEIVER_GROUPS = 2
    }
}

enum class ContactFilter {
    ALL,
    SPECIFIC,
    EXCLUDE
}

enum class ResponseMode {
    SINGLE,
    RANDOM,
    SEQUENTIAL
}
