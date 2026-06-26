package com.wahyuzero.replyforge.ui.rule

import com.wahyuzero.replyforge.data.model.ResponseMode
import com.wahyuzero.replyforge.data.model.Rule
import com.wahyuzero.replyforge.databinding.ActivityRuleEditBinding

/**
 * Extracts the form-field ↔ Rule mapping logic from RuleEditActivity.
 *
 * This was previously ~180 lines of duplicated code inside saveRule() (one path
 * for update, one for insert — both with identical field lists). Now there is a
 * single source of truth in [buildRuleFromForm], and [populateFormFromRule]
 * centralises the reverse direction.
 *
 * RuleEditActivity keeps lifecycle / DB / navigation concerns only.
 */
object RuleFormMapper {

    /**
     * Read all form fields from [binding] and produce a [Rule].
     *
     * If [existing] is non-null, the result is a copy of the existing rule
     * (preserving id, createdAt) — i.e. an update. Otherwise a fresh Rule
     * is constructed (insert path).
     *
     * @param startTime  "HH:mm" or null
     * @param endTime    "HH:mm" or null
     * @param selectedDays  1-based day numbers (1=Mon..7=Sun)
     * @param aiProviderId  resolved provider id or null
     */
    fun buildRuleFromForm(
        binding: ActivityRuleEditBinding,
        existing: Rule?,
        startTime: String?,
        endTime: String?,
        selectedDays: Set<Int>,
        aiProviderId: Long?
    ): Rule {
        val name = binding.editName.text.toString().trim()
        val pattern = binding.editPattern.text.toString().trim()
        val response = binding.editResponse.text.toString().trim()

        val matchType = MatchType.values()
            .find { it.displayName == binding.spinnerMatchType.text.toString() }
            ?: MatchType.CONTAINS

        val responseMode = ResponseMode.values()
            .find { it.name == binding.spinnerResponseMode.text.toString() }
            ?: ResponseMode.SINGLE

        val delayMin = binding.editDelayMin.text.toString().toIntOrNull() ?: 0
        val delayMax = binding.editDelayMax.text.toString().toIntOrNull() ?: 0
        val priority = binding.editPriority.text.toString().toIntOrNull() ?: 0
        val ignorePattern = binding.editIgnorePattern.text.toString().trim()
        val activeDaysStr = selectedDays.sorted().joinToString(",")
        val minDelaySec = binding.editMinDelaySeconds.text.toString().toIntOrNull() ?: 0
        val maxReplies = binding.editMaxReplies.text.toString().toIntOrNull() ?: 0
        val replyDelayMs = binding.editReplyDelayMs.text.toString().toLongOrNull() ?: 0L
        val dailyReplyLimit = binding.editDailyReplyLimit.text.toString().toIntOrNull() ?: 0
        val preventRepeatingMs = binding.editPreventRepeatingMs.text.toString().toLongOrNull() ?: 0L
        val prevRuleTimeoutMs = binding.editPrevRuleTimeoutMs.text.toString().toLongOrNull() ?: 0L

        // AI
        val useAi = binding.switchUseAi.isChecked
        val systemPrompt = binding.editSystemPrompt.text.toString().trim().ifBlank { null }
        val aiTemperature = binding.sliderAiTemperature.value.let {
            if (Math.abs(it - 0.7f) < 0.05f) null else it
        }

        // Common field values — used for both insert and update
        val fieldValues = RuleFieldValues(
            name = name,
            pattern = pattern,
            matchType = matchType,
            response = response,
            isRegex = matchType == MatchType.REGEX,
            caseSensitive = binding.switchCaseSensitive.isChecked,
            enabled = binding.switchEnabled.isChecked,
            delayMin = delayMin,
            delayMax = delayMax,
            priority = priority,
            responseMode = responseMode,
            ignorePattern = ignorePattern,
            ignoreGroups = binding.switchIgnoreGroups.isChecked,
            ignoreIndividuals = binding.switchIgnoreIndividuals.isChecked,
            startTime = startTime,
            endTime = endTime,
            activeDays = activeDaysStr,
            minDelaySeconds = minDelaySec,
            maxRepliesPerContact = maxReplies,
            ignoreHolidays = binding.switchIgnoreHolidays.isChecked,
            useAi = useAi,
            aiProviderId = aiProviderId,
            systemPrompt = systemPrompt,
            aiTemperature = aiTemperature,
            replyDelayMs = replyDelayMs,
            replyHeader = binding.editReplyHeader.text.toString().trim().ifBlank { null },
            replyFooter = binding.editReplyFooter.text.toString().trim().ifBlank { null },
            replyPrefix = binding.editReplyPrefix.text.toString().trim().ifBlank { null },
            probability = binding.sliderProbability.value.toInt(),
            lineBreaks = binding.switchLineBreaks.isChecked,
            caseInsensitive = binding.switchCaseInsensitive.isChecked,
            ignoreAccents = binding.switchIgnoreAccents.isChecked,
            similarityThreshold = binding.sliderSimilarity.value.toInt(),
            receiverType = when {
                binding.radioContactsOnly.isChecked -> Rule.RECEIVER_CONTACTS
                binding.radioGroupsOnly.isChecked -> Rule.RECEIVER_GROUPS
                else -> Rule.RECEIVER_BOTH
            },
            specificContacts = binding.editSpecificContacts.text.toString().trim().ifBlank { null },
            specificGroups = binding.editSpecificGroups.text.toString().trim().ifBlank { null },
            dailyReplyLimit = dailyReplyLimit,
            preventRepeatingMs = preventRepeatingMs,
            prevRuleTimeoutMs = prevRuleTimeoutMs
        )

        return if (existing != null) {
            existing.copy(
                name = fieldValues.name,
                pattern = fieldValues.pattern,
                matchType = fieldValues.matchType,
                response = fieldValues.response,
                isRegex = fieldValues.isRegex,
                caseSensitive = fieldValues.caseSensitive,
                enabled = fieldValues.enabled,
                delayMin = fieldValues.delayMin,
                delayMax = fieldValues.delayMax,
                priority = fieldValues.priority,
                responseMode = fieldValues.responseMode,
                ignorePattern = fieldValues.ignorePattern,
                ignoreGroups = fieldValues.ignoreGroups,
                ignoreIndividuals = fieldValues.ignoreIndividuals,
                updatedAt = System.currentTimeMillis(),
                startTime = fieldValues.startTime,
                endTime = fieldValues.endTime,
                activeDays = fieldValues.activeDays,
                minDelaySeconds = fieldValues.minDelaySeconds,
                maxRepliesPerContact = fieldValues.maxRepliesPerContact,
                ignoreHolidays = fieldValues.ignoreHolidays,
                useAi = fieldValues.useAi,
                aiProviderId = fieldValues.aiProviderId,
                systemPrompt = fieldValues.systemPrompt,
                aiTemperature = fieldValues.aiTemperature,
                replyDelayMs = fieldValues.replyDelayMs,
                replyHeader = fieldValues.replyHeader,
                replyFooter = fieldValues.replyFooter,
                replyPrefix = fieldValues.replyPrefix,
                probability = fieldValues.probability,
                lineBreaks = fieldValues.lineBreaks,
                caseInsensitive = fieldValues.caseInsensitive,
                ignoreAccents = fieldValues.ignoreAccents,
                similarityThreshold = fieldValues.similarityThreshold,
                receiverType = fieldValues.receiverType,
                specificContacts = fieldValues.specificContacts,
                specificGroups = fieldValues.specificGroups,
                dailyReplyLimit = fieldValues.dailyReplyLimit,
                preventRepeatingMs = fieldValues.preventRepeatingMs,
                prevRuleTimeoutMs = fieldValues.prevRuleTimeoutMs
            )
        } else {
            Rule(
                name = fieldValues.name,
                pattern = fieldValues.pattern,
                matchType = fieldValues.matchType,
                response = fieldValues.response,
                isRegex = fieldValues.isRegex,
                caseSensitive = fieldValues.caseSensitive,
                enabled = fieldValues.enabled,
                delayMin = fieldValues.delayMin,
                delayMax = fieldValues.delayMax,
                priority = fieldValues.priority,
                responseMode = fieldValues.responseMode,
                ignorePattern = fieldValues.ignorePattern,
                ignoreGroups = fieldValues.ignoreGroups,
                ignoreIndividuals = fieldValues.ignoreIndividuals,
                startTime = fieldValues.startTime,
                endTime = fieldValues.endTime,
                activeDays = fieldValues.activeDays,
                minDelaySeconds = fieldValues.minDelaySeconds,
                maxRepliesPerContact = fieldValues.maxRepliesPerContact,
                ignoreHolidays = fieldValues.ignoreHolidays,
                useAi = fieldValues.useAi,
                aiProviderId = fieldValues.aiProviderId,
                systemPrompt = fieldValues.systemPrompt,
                aiTemperature = fieldValues.aiTemperature,
                replyDelayMs = fieldValues.replyDelayMs,
                replyHeader = fieldValues.replyHeader,
                replyFooter = fieldValues.replyFooter,
                replyPrefix = fieldValues.replyPrefix,
                probability = fieldValues.probability,
                lineBreaks = fieldValues.lineBreaks,
                caseInsensitive = fieldValues.caseInsensitive,
                ignoreAccents = fieldValues.ignoreAccents,
                similarityThreshold = fieldValues.similarityThreshold,
                receiverType = fieldValues.receiverType,
                specificContacts = fieldValues.specificContacts,
                specificGroups = fieldValues.specificGroups,
                dailyReplyLimit = fieldValues.dailyReplyLimit,
                preventRepeatingMs = fieldValues.preventRepeatingMs,
                prevRuleTimeoutMs = fieldValues.prevRuleTimeoutMs
            )
        }
    }

    /**
     * Validate the three required fields. Returns an error message resource id
     * or null if valid.
     */
    fun validateForm(binding: ActivityRuleEditBinding): Boolean {
        if (binding.editName.text.toString().trim().isBlank()) return false
        if (binding.editPattern.text.toString().trim().isBlank()) return false
        if (binding.editResponse.text.toString().trim().isBlank()) return false
        return true
    }

    /**
     * Intermediate holder so we don't duplicate 40+ fields twice.
     */
    private data class RuleFieldValues(
        val name: String,
        val pattern: String,
        val matchType: MatchType,
        val response: String,
        val isRegex: Boolean,
        val caseSensitive: Boolean,
        val enabled: Boolean,
        val delayMin: Int,
        val delayMax: Int,
        val priority: Int,
        val responseMode: ResponseMode,
        val ignorePattern: String,
        val ignoreGroups: Boolean,
        val ignoreIndividuals: Boolean,
        val startTime: String?,
        val endTime: String?,
        val activeDays: String,
        val minDelaySeconds: Int,
        val maxRepliesPerContact: Int,
        val ignoreHolidays: Boolean,
        val useAi: Boolean,
        val aiProviderId: Long?,
        val systemPrompt: String?,
        val aiTemperature: Float?,
        val replyDelayMs: Long,
        val replyHeader: String?,
        val replyFooter: String?,
        val replyPrefix: String?,
        val probability: Int,
        val lineBreaks: Boolean,
        val caseInsensitive: Boolean,
        val ignoreAccents: Boolean,
        val similarityThreshold: Int,
        val receiverType: Int,
        val specificContacts: String?,
        val specificGroups: String?,
        val dailyReplyLimit: Int,
        val preventRepeatingMs: Long,
        val prevRuleTimeoutMs: Long
    )
}
