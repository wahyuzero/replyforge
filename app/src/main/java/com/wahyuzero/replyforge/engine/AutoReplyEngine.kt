package com.wahyuzero.replyforge.engine

import android.util.Log
import com.wahyuzero.replyforge.data.db.AiProviderDao
import com.wahyuzero.replyforge.data.db.AiUsageDao
import com.wahyuzero.replyforge.data.db.ConversationDao
import com.wahyuzero.replyforge.data.model.ContactFilter
import com.wahyuzero.replyforge.data.model.ResponseMode
import com.wahyuzero.replyforge.data.model.Rule
import com.wahyuzero.replyforge.data.db.HolidayDao
import com.wahyuzero.replyforge.data.db.HistoryDao
import com.wahyuzero.replyforge.data.db.RateLimitDao
import com.wahyuzero.replyforge.data.db.RateLimitEntry
import com.wahyuzero.replyforge.data.db.RuleDao
import com.wahyuzero.replyforge.data.model.ReplyHistory
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.network.AiService
import com.wahyuzero.replyforge.ui.rule.MatchType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class ReplyResult(
    val replyText: String,
    val delayMs: Long,
    val matchedRule: Rule,
    val sender: String,
    val originalText: String,
    val isGroup: Boolean,
    val groupName: String?,
    val processTimeMs: Long
)

class AutoReplyEngine(
    private val ruleDao: RuleDao,
    private val historyDao: HistoryDao,
    private val appPrefs: AppPrefs,
    private val holidayDao: HolidayDao,
    private val rateLimitDao: RateLimitDao,
    private val aiProviderDao: AiProviderDao? = null,
    private val aiService: AiService? = null
) {

    companion object {
        private const val TAG = "AutoReplyEngine"
        const val WHATSAPP_MSG_LIMIT = 1000
        private const val RANDOM_STRING_LENGTH = 8
        // Thread-safe sequential index tracking per rule to prevent race conditions
        private val sequentialCounters = ConcurrentHashMap<Long, AtomicInteger>()
    }

    suspend fun processIncomingMessage(
        sender: String,
        text: String,
        isGroup: Boolean,
        groupName: String?
    ): ReplyResult? {
        val autoReplyEnabled = appPrefs.autoReplyEnabled.first()
        if (!autoReplyEnabled) return null

        // Phase 3: Away mode — still fire rules, but append away message if set
        val isAway = appPrefs.awayMode.first()
        val awayMsg = appPrefs.awayMessage.first()

        val rules = ruleDao.getEnabledRules().first()
        if (rules.isEmpty()) return null

        val now = Calendar.getInstance()
        val currentTime = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, ..., 7=Sat
        // Convert to our format: 1=Mon..7=Sun
        val activeDay = if (currentDayOfWeek == 1) 7 else currentDayOfWeek - 1
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)

        val processStartTime = System.currentTimeMillis()

        val matchResult = findMatchingRule(rules, sender, text, isGroup, groupName)
            ?: return null

        val matchedRule = matchResult.first

        // Phase 3: Time-based scheduling check
        if (!isWithinActiveHours(matchedRule, currentTime)) return null

        // Phase 3: Active days check
        if (!isActiveOnDay(matchedRule, activeDay)) return null

        // Phase 3: Holiday check
        if (matchedRule.ignoreHolidays && isTodayHoliday(todayDate)) return null

        // Phase 3: Rate limiting check
        if (!passesRateLimit(matchedRule, sender, todayDate, now.timeInMillis)) return null

        // Gap fill: Probability check
        if (matchedRule.probability < 100) {
            if (Random.nextInt(100) >= matchedRule.probability) return null
        }

        // Gap fill: Receiver type check
        if (matchedRule.receiverType != Rule.RECEIVER_BOTH) {
            if (matchedRule.receiverType == Rule.RECEIVER_CONTACTS && isGroup) return null
            if (matchedRule.receiverType == Rule.RECEIVER_GROUPS && !isGroup) return null
        }

        // Gap fill: Specific contacts allowlist
        if (!matchedRule.specificContacts.isNullOrBlank()) {
            val allowedContacts = matchedRule.specificContacts.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (allowedContacts.isNotEmpty() && !allowedContacts.contains(sender.lowercase())) return null
        }

        // Gap fill: Specific groups allowlist
        if (isGroup && !matchedRule.specificGroups.isNullOrBlank()) {
            val allowedGroups = matchedRule.specificGroups.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (allowedGroups.isNotEmpty() && groupName != null && !allowedGroups.contains(groupName.lowercase())) return null
        }

        // Gap fill: Daily reply limit
        if (matchedRule.dailyReplyLimit > 0) {
            val startOfDay = getStartOfDayMillis(now)
            val endOfDay = getEndOfDayMillis(now)
            val countToday = historyDao.getRuleRepliesToday(matchedRule.id, startOfDay, endOfDay)
            if (countToday >= matchedRule.dailyReplyLimit) return null
        }

        // Gap fill: Prevent repeating to same contact
        if (matchedRule.preventRepeatingMs > 0) {
            val lastTime = historyDao.getLastReplyTime(matchedRule.id, sender)
            if (lastTime != null && (now.timeInMillis - lastTime) < matchedRule.preventRepeatingMs) return null
        }

        // Gap fill: Previous rule timeout
        if (matchedRule.prevRuleTimeoutMs > 0) {
            val lastAnyTime = historyDao.getLastReplyForContact(sender)
            if (lastAnyTime != null && (now.timeInMillis - lastAnyTime) < matchedRule.prevRuleTimeoutMs) return null
        }

        // Phase 4: AI integration
        var finalResponse: String
        val selectedResponse = selectResponse(matchedRule)

        if (matchedRule.useAi && aiProviderDao != null && aiService != null) {
            finalResponse = getAiReply(matchedRule, sender, text, selectedResponse)
        } else {
            finalResponse = selectedResponse
        }

        val delayMs = calculateDelay(matchedRule.delayMin, matchedRule.delayMax) + matchedRule.replyDelayMs

        val regexGroups = matchResult.second
        finalResponse = applyPlaceholders(finalResponse, sender, text, isGroup, groupName, regexGroups)

        // Gap fill: Line breaks
        if (matchedRule.lineBreaks) {
            finalResponse = finalResponse.replace("\\n", "\n")
        }

        // Gap fill: Apply prefix
        if (!matchedRule.replyPrefix.isNullOrBlank()) {
            finalResponse = matchedRule.replyPrefix + finalResponse
        }

        // Gap fill: Apply header/footer
        if (!matchedRule.replyHeader.isNullOrBlank()) {
            finalResponse = matchedRule.replyHeader + "\n" + finalResponse
        }
        if (!matchedRule.replyFooter.isNullOrBlank()) {
            finalResponse = finalResponse + "\n" + matchedRule.replyFooter
        }

        // Phase 3: Append away message if away mode is enabled and message is set
        if (isAway && awayMsg.isNotBlank()) {
            finalResponse = "$finalResponse\n\n$awayMsg"
        }

        // Phase 3: Update rate limit tracking after successful reply
        recordRateLimit(matchedRule.id, sender, todayDate, now.timeInMillis)

        val processTimeMs = System.currentTimeMillis() - processStartTime

        return ReplyResult(
            replyText = finalResponse,
            delayMs = delayMs,
            matchedRule = matchedRule,
            sender = sender,
            originalText = text,
            isGroup = isGroup,
            groupName = groupName,
            processTimeMs = processTimeMs
        )
    }

    // Phase 4: Get AI reply with fallback to static response
    private suspend fun getAiReply(
        rule: Rule,
        sender: String,
        text: String,
        fallbackResponse: String
    ): String {
        val providerId = rule.aiProviderId ?: return fallbackResponse

        try {
            val provider = aiProviderDao?.getProviderById(providerId)
            if (provider == null || !provider.isActive) {
                Log.w(TAG, "AI provider $providerId not found or inactive, using fallback")
                return fallbackResponse
            }

            val result = aiService?.getAiReply(
                provider = provider,
                contactName = sender,
                incomingMessage = text,
                systemPrompt = rule.systemPrompt,
                temperature = rule.aiTemperature
            )

            if (result != null) {
                Log.d(TAG, "AI reply success: ${result.replyText.take(50)}... (tokens: ${result.totalTokens})")
                return result.replyText
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI reply failed, using fallback", e)
        }

        return fallbackResponse
    }

    // Phase 3: Check if current time is within active hours
    private fun isWithinActiveHours(rule: Rule, currentTime: String): Boolean {
        if (rule.startTime == null || rule.endTime == null) return true
        if (rule.startTime.isBlank() || rule.endTime.isBlank()) return true

        // Handle cross-midnight ranges (e.g. 22:00 - 06:00)
        return if (rule.startTime <= rule.endTime) {
            // Same day: e.g. 08:00 - 22:00
            currentTime in rule.startTime..rule.endTime
        } else {
            // Cross midnight: e.g. 22:00 - 06:00
            currentTime >= rule.startTime || currentTime <= rule.endTime
        }
    }

    // Phase 3: Check if current day is in active days
    private fun isActiveOnDay(rule: Rule, activeDay: Int): Boolean {
        if (rule.activeDays.isBlank()) return true
        val days = rule.activeDays.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (days.isEmpty()) return true
        return activeDay in days
    }

    // Phase 3: Check if today is a holiday
    private suspend fun isTodayHoliday(todayDate: String): Boolean {
        return holidayDao.isHoliday(todayDate)
    }

    // Phase 3: Check rate limit
    private suspend fun passesRateLimit(rule: Rule, sender: String, todayDate: String, nowMs: Long): Boolean {
        if (rule.minDelaySeconds <= 0 && rule.maxRepliesPerContact <= 0) return true

        val entry = rateLimitDao.getEntry(rule.id, sender)

        if (entry != null) {
            // Check minDelaySeconds
            if (rule.minDelaySeconds > 0) {
                val elapsed = (nowMs - entry.lastReplyTime) / 1000
                if (elapsed < rule.minDelaySeconds) return false
            }

            // Check maxRepliesPerContact
            if (rule.maxRepliesPerContact > 0) {
                val countToday = if (entry.lastResetDate == todayDate) entry.replyCountToday else 0
                if (countToday >= rule.maxRepliesPerContact) return false
            }
        }

        return true
    }

    // Phase 3: Record rate limit entry
    private suspend fun recordRateLimit(ruleId: Long, sender: String, todayDate: String, nowMs: Long) {
        val existing = rateLimitDao.getEntry(ruleId, sender)
        if (existing != null) {
            val countToday = if (existing.lastResetDate == todayDate) existing.replyCountToday + 1 else 1
            rateLimitDao.upsert(
                RateLimitEntry(
                    ruleId = ruleId,
                    contactName = sender,
                    lastReplyTime = nowMs,
                    replyCountToday = countToday,
                    lastResetDate = todayDate
                )
            )
        } else {
            rateLimitDao.upsert(
                RateLimitEntry(
                    ruleId = ruleId,
                    contactName = sender,
                    lastReplyTime = nowMs,
                    replyCountToday = 1,
                    lastResetDate = todayDate
                )
            )
        }
    }

    private suspend fun selectResponse(rule: Rule): String {
        return when (rule.responseMode) {
            ResponseMode.SINGLE -> rule.response
            ResponseMode.RANDOM -> {
                val responses = rule.response.split("|||").map { it.trim() }.filter { it.isNotBlank() }
                if (responses.size <= 1) rule.response
                else responses.random()
            }
            ResponseMode.SEQUENTIAL -> {
                val responses = rule.response.split("|||").map { it.trim() }.filter { it.isNotBlank() }
                if (responses.size <= 1) return rule.response
                // Use atomic counter to prevent race condition on concurrent messages
                val counter = sequentialCounters.getOrPut(rule.id) {
                    AtomicInteger(rule.sequentialIndex.coerceIn(0, responses.lastIndex))
                }
                val index = counter.getAndAccumulate(responses.size) { curr, size ->
                    (curr + 1) % size
                }.coerceIn(0, responses.lastIndex)
                val reply = responses[index]
                // Persist the next index back to DB (best-effort)
                val nextIndex = (index + 1) % responses.size
                val updated = rule.copy(sequentialIndex = nextIndex)
                ruleDao.update(updated)
                reply
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun applyPlaceholders(
        response: String,
        sender: String,
        text: String,
        isGroup: Boolean,
        groupName: String?,
        regexGroups: List<String> = emptyList()
    ): String {
        val now = Date()
        val cal = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dayFmt = SimpleDateFormat("EEEE", Locale.getDefault())
        val dateShortFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

        val nameWords = sender.trim().split("\\s+".toRegex())
        val firstName = nameWords.firstOrNull() ?: sender
        val lastName = nameWords.drop(1).joinToString(" ")

        var result = response
            // Existing placeholders
            .replace("%name%", sender)
            .replace("%message%", text)
            .replace("%time%", timeFmt.format(now))
            .replace("%date%", dateFmt.format(now))
            .replace("%day%", dayFmt.format(now))
            // URL-encoded variants
            .replace("%name_url%", URLEncoder.encode(sender, "UTF-8"))
            .replace("%message_url%", URLEncoder.encode(text, "UTF-8"))
            // Name parts
            .replace("%first_name%", firstName)
            .replace("%last_name%", lastName)
            .replace("%username%", sender) // compatibility
            // Short date/time
            .replace("%date_short%", dateShortFmt.format(now))
            .replace("%time_short%", timeFmt.format(now))
            // Individual date/time parts
            .replace("%year%", cal.get(Calendar.YEAR).toString())
            .replace("%month%", String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1))
            .replace("%day%", String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH)))
            .replace("%hour%", String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY)))
            .replace("%minute%", String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE)))
            .replace("%second%", String.format(Locale.US, "%02d", cal.get(Calendar.SECOND)))
            // Week and day of year
            .replace("%week%", cal.get(Calendar.WEEK_OF_YEAR).toString())
            .replace("%day_of_year%", cal.get(Calendar.DAY_OF_YEAR).toString())
            // Random strings
            .replace("%rndm_abc%", generateRandomString(RANDOM_STRING_LENGTH, ('a'..'z').toList()))
            .replace("%rndm_abc_upper%", generateRandomString(RANDOM_STRING_LENGTH, ('A'..'Z').toList()))
            .replace("%rndm_num%", generateRandomString(RANDOM_STRING_LENGTH, ('0'..'9').toList()))
            .replace("%rndm_abcnum%", generateRandomString(RANDOM_STRING_LENGTH, ('a'..'z') + ('A'..'Z') + ('0'..'9')))
            .replace("%rndm_symbol%", generateRandomString(RANDOM_STRING_LENGTH, "!@#\\$%^&*".toList()))
            .replace("%rndm_grawlix%", generateRandomString(RANDOM_STRING_LENGTH, "!@#\\$%^&*".toList()))
            .replace("%rndm_ascii%", generateRandomString(RANDOM_STRING_LENGTH, (33..126).map { it.toChar() }))
            // Line break placeholder
            .replace("%line_break%", "\n")

        // Regex capturing group results: %1%, %2%, %3%...
        for (i in regexGroups.indices) {
            result = result.replace("%${i + 1}%", regexGroups[i])
        }
        // Also clean up any remaining numeric placeholders that weren't matched
        result = result.replace(Regex("%\\d+%")) { matchResult ->
            val groupIndex = matchResult.value.removeSurrounding("%").toIntOrNull()
            if (groupIndex != null && groupIndex > 0 && groupIndex <= regexGroups.size) {
                regexGroups[groupIndex - 1]
            } else {
                matchResult.value // leave as-is if no matching group
            }
        }

        return result
    }

    private fun generateRandomString(length: Int, chars: List<Char>): String {
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun findMatchingRule(
        rules: List<Rule>,
        sender: String,
        text: String,
        isGroup: Boolean,
        groupName: String?
    ): Pair<Rule, List<String>>? {
        for (rule in rules) {
            if (!rule.enabled) continue

            // Ignore pattern check
            if (rule.ignorePattern.isNotBlank() && text.contains(rule.ignorePattern)) continue
            if (isGroup && rule.ignoreGroups) continue
            if (!isGroup && rule.ignoreIndividuals) continue

            if (!passesContactFilter(rule, sender)) continue

            if (isGroup && !passesGroupFilter(rule, groupName)) continue
            if (!isGroup && rule.groupFilter != ContactFilter.ALL) {
                if (rule.contactFilter == ContactFilter.ALL) {
                    // fine, rule applies to individual messages too
                }
            }

            val matchResult = PatternMatcher.matchPatternWithGroups(
                pattern = rule.pattern,
                message = text,
                matchType = rule.matchType,
                caseSensitive = rule.caseSensitive,
                ignoreAccents = rule.ignoreAccents,
                similarityThreshold = rule.similarityThreshold
            )

            if (matchResult.matched) {
                return Pair(rule, matchResult.groups)
            }
        }
        return null
    }

    private fun passesContactFilter(rule: Rule, sender: String): Boolean {
        return when (rule.contactFilter) {
            ContactFilter.ALL -> true
            ContactFilter.SPECIFIC -> {
                val contacts = rule.contactList.split(",").map { it.trim().lowercase() }
                contacts.contains(sender.lowercase())
            }
            ContactFilter.EXCLUDE -> {
                val contacts = rule.contactList.split(",").map { it.trim().lowercase() }
                !contacts.contains(sender.lowercase())
            }
        }
    }

    private fun passesGroupFilter(rule: Rule, groupName: String?): Boolean {
        if (rule.groupFilter == ContactFilter.ALL) return true
        if (groupName.isNullOrBlank()) return false

        return when (rule.groupFilter) {
            ContactFilter.ALL -> true
            ContactFilter.SPECIFIC -> {
                val groups = rule.groupList.split(",").map { it.trim().lowercase() }
                groups.contains(groupName.lowercase())
            }
            ContactFilter.EXCLUDE -> {
                val groups = rule.groupList.split(",").map { it.trim().lowercase() }
                !groups.contains(groupName.lowercase())
            }
        }
    }

    private fun calculateDelay(delayMin: Int, delayMax: Int): Long {
        if (delayMin <= 0 && delayMax <= 0) return 0L
        val min = delayMin.coerceAtLeast(0)
        val max = if (delayMax <= 0) min else delayMax.coerceAtLeast(min)
        // Guard against Int overflow when max == Int.MAX_VALUE
        val maxPlusOne = if (max == Int.MAX_VALUE) max.toLong() + 1L else (max + 1).toLong()
        return Random.nextLong(min.toLong(), maxPlusOne)
    }

    suspend fun logReply(
        ruleId: Long?,
        sender: String,
        message: String,
        response: String,
        isGroup: Boolean,
        groupName: String?,
        processTimeMs: Long
    ) {
        val history = ReplyHistory(
            ruleId = ruleId,
            sender = sender,
            message = message,
            response = response,
            timestamp = System.currentTimeMillis(),
            isGroup = isGroup,
            groupName = groupName,
            processTimeMs = processTimeMs
        )
        historyDao.insert(history)
    }

    private fun getStartOfDayMillis(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun getEndOfDayMillis(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}
