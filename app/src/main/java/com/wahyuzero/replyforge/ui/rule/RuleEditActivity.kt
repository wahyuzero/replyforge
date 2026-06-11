package com.wahyuzero.replyforge.ui.rule

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.model.AiProvider
import com.wahyuzero.replyforge.data.model.ResponseMode
import com.wahyuzero.replyforge.data.model.Rule
import com.wahyuzero.replyforge.databinding.ActivityRuleEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuleEditBinding
    private lateinit var db: AppDatabase
    private var ruleId: Long = -1L
    private var existingRule: Rule? = null

    // Phase 3: Time scheduling state
    private var startTime: String? = null
    private var endTime: String? = null

    // Phase 3: Active days (1=Mon..7=Sun)
    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val selectedDays = mutableSetOf(1, 2, 3, 4, 5, 6, 7)

    // Phase 4: AI providers for dropdown
    private var aiProviders: List<AiProvider> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        ruleId = intent.getLongExtra("rule_id", -1L)

        setupToolbar()
        setupMatchTypeDropdown()
        setupResponseModeDropdown()
        setupButtons()
        setupTimePickers()
        setupDayChips()
        setupAiSection()
        setupNewFields()

        if (ruleId > 0) {
            loadRule()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (ruleId > 0) "Edit Rule" else "New Rule"
    }

    private fun setupMatchTypeDropdown() {
        val matchTypes = MatchType.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, matchTypes)
        binding.spinnerMatchType.setAdapter(adapter)
        binding.spinnerMatchType.setText(MatchType.CONTAINS.displayName, false)
    }

    private fun setupResponseModeDropdown() {
        val modes = ResponseMode.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modes)
        binding.spinnerResponseMode.setAdapter(adapter)
        binding.spinnerResponseMode.setText(ResponseMode.SINGLE.name, false)
    }

    private fun setupButtons() {
        binding.buttonSave.setOnClickListener {
            saveRule()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    // Phase 3: Time picker setup
    private fun setupTimePickers() {
        binding.btnStartTime.setOnClickListener {
            val initialHour = startTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 8
            val initialMin = startTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(this, { _, hour, minute ->
                startTime = String.format("%02d:%02d", hour, minute)
                binding.btnStartTime.text = "Start: $startTime"
            }, initialHour, initialMin, true).show()
        }

        binding.btnEndTime.setOnClickListener {
            val initialHour = endTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 22
            val initialMin = endTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(this, { _, hour, minute ->
                endTime = String.format("%02d:%02d", hour, minute)
                binding.btnEndTime.text = "End: $endTime"
            }, initialHour, initialMin, true).show()
        }
    }

    // Phase 3: Day chips setup
    private fun setupDayChips() {
        binding.chipGroupDays.removeAllViews()
        for (i in dayNames.indices) {
            val dayNum = i + 1 // 1=Mon..7=Sun
            val chip = Chip(this).apply {
                text = dayNames[i]
                isCheckable = true
                isChecked = dayNum in selectedDays
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedDays.add(dayNum) else selectedDays.remove(dayNum)
                }
            }
            binding.chipGroupDays.addView(chip)
        }
    }

    // Phase 4: AI section setup
    private fun setupAiSection() {
        binding.switchUseAi.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutAiConfig.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Load AI providers for the dropdown
        lifecycleScope.launch {
            aiProviders = withContext(Dispatchers.IO) {
                db.aiProviderDao().getAllProviders().first()
            }
            val providerNames = aiProviders.map { it.name }
            val adapter = ArrayAdapter(
                this@RuleEditActivity,
                android.R.layout.simple_dropdown_item_1line,
                providerNames
            )
            binding.spinnerAiProvider.setAdapter(adapter)

            // If editing and there's a provider set, select it
            if (existingRule?.aiProviderId != null) {
                val provider = aiProviders.find { it.id == existingRule!!.aiProviderId }
                if (provider != null) {
                    binding.spinnerAiProvider.setText(provider.name, false)
                }
            }
        }

        // Temperature slider
        binding.sliderAiTemperature.addOnChangeListener { _, value, _ ->
            binding.textAiTemperatureValue.text = String.format("%.1f", value)
        }
    }

    private fun setupNewFields() {
        // Probability slider
        binding.sliderProbability.addOnChangeListener { _, value, _ ->
            binding.textProbabilityValue.text = "${value.toInt()}%"
        }

        // Similarity slider
        binding.sliderSimilarity.addOnChangeListener { _, value, _ ->
            binding.textSimilarityValue.text = "${value.toInt()}%"
        }
    }

    private fun loadRule() {
        lifecycleScope.launch {
            existingRule = withContext(Dispatchers.IO) {
                db.ruleDao().getRuleById(ruleId)
            }

            existingRule?.let { rule ->
                binding.editName.setText(rule.name)
                binding.editPattern.setText(rule.pattern)
                binding.spinnerMatchType.setText(rule.matchType.displayName, false)
                binding.editResponse.setText(rule.response)
                binding.spinnerResponseMode.setText(rule.responseMode.name, false)
                binding.editIgnorePattern.setText(rule.ignorePattern)
                binding.switchIgnoreGroups.isChecked = rule.ignoreGroups
                binding.switchIgnoreIndividuals.isChecked = rule.ignoreIndividuals
                binding.switchCaseSensitive.isChecked = rule.caseSensitive
                binding.switchEnabled.isChecked = rule.enabled
                binding.editDelayMin.setText(rule.delayMin.toString())
                binding.editDelayMax.setText(rule.delayMax.toString())
                binding.editPriority.setText(rule.priority.toString())

                // Phase 3: Load time scheduling
                startTime = rule.startTime
                endTime = rule.endTime
                binding.btnStartTime.text = if (startTime != null) "Start: $startTime" else "Start: --:--"
                binding.btnEndTime.text = if (endTime != null) "End: $endTime" else "End: --:--"

                // Phase 3: Load active days
                selectedDays.clear()
                if (rule.activeDays.isNotBlank()) {
                    selectedDays.addAll(rule.activeDays.split(",").mapNotNull { it.trim().toIntOrNull() })
                }
                setupDayChips()

                // Phase 3: Rate limiting
                binding.editMinDelaySeconds.setText(rule.minDelaySeconds.toString())
                binding.editMaxReplies.setText(rule.maxRepliesPerContact.toString())

                // Phase 3: Holiday
                binding.switchIgnoreHolidays.isChecked = rule.ignoreHolidays

                // Phase 4: Load AI settings
                binding.switchUseAi.isChecked = rule.useAi
                binding.layoutAiConfig.visibility = if (rule.useAi) View.VISIBLE else View.GONE

                rule.systemPrompt?.let {
                    binding.editSystemPrompt.setText(it)
                }
                rule.aiTemperature?.let {
                    binding.sliderAiTemperature.value = it
                }

                // Load AI provider name for dropdown
                if (rule.aiProviderId != null) {
                    lifecycleScope.launch {
                        val providers = withContext(Dispatchers.IO) {
                            db.aiProviderDao().getAllProviders().first()
                        }
                        val provider = providers.find { it.id == rule.aiProviderId }
                        if (provider != null) {
                            binding.spinnerAiProvider.setText(provider.name, false)
                        }
                    }
                }

                // Gap fill: Load reply system fields
                binding.editReplyDelayMs.setText(rule.replyDelayMs.toString())
                binding.editReplyHeader.setText(rule.replyHeader ?: "")
                binding.editReplyFooter.setText(rule.replyFooter ?: "")
                binding.editReplyPrefix.setText(rule.replyPrefix ?: "")
                binding.sliderProbability.value = rule.probability.toFloat()
                binding.switchLineBreaks.isChecked = rule.lineBreaks

                // Gap fill: Pattern matching
                binding.switchCaseInsensitive.isChecked = rule.caseInsensitive
                binding.switchIgnoreAccents.isChecked = rule.ignoreAccents
                binding.sliderSimilarity.value = rule.similarityThreshold.toFloat()

                // Gap fill: Receiver type
                when (rule.receiverType) {
                    0 -> binding.radioBoth.isChecked = true
                    1 -> binding.radioContactsOnly.isChecked = true
                    2 -> binding.radioGroupsOnly.isChecked = true
                }

                // Gap fill: Specific contacts/groups
                binding.editSpecificContacts.setText(rule.specificContacts ?: "")
                binding.editSpecificGroups.setText(rule.specificGroups ?: "")

                // Gap fill: Rate limiting
                binding.editDailyReplyLimit.setText(rule.dailyReplyLimit.toString())
                binding.editPreventRepeatingMs.setText(rule.preventRepeatingMs.toString())
                binding.editPrevRuleTimeoutMs.setText(rule.prevRuleTimeoutMs.toString())
            }
        }
    }

    private fun saveRule() {
        val name = binding.editName.text.toString().trim()
        val pattern = binding.editPattern.text.toString().trim()
        val response = binding.editResponse.text.toString().trim()

        if (name.isBlank()) {
            binding.editName.error = "Name is required"
            return
        }

        if (pattern.isBlank()) {
            binding.editPattern.error = "Pattern is required"
            return
        }

        if (response.isBlank()) {
            binding.editResponse.error = "Response is required (also used as AI fallback)"
            return
        }

        val matchTypeDisplayName = binding.spinnerMatchType.text.toString()
        val matchType = MatchType.values().find { it.displayName == matchTypeDisplayName }
            ?: MatchType.CONTAINS

        val responseModeName = binding.spinnerResponseMode.text.toString()
        val responseMode = ResponseMode.values().find { it.name == responseModeName }
            ?: ResponseMode.SINGLE

        val delayMin = binding.editDelayMin.text.toString().toIntOrNull() ?: 0
        val delayMax = binding.editDelayMax.text.toString().toIntOrNull() ?: 0
        val priority = binding.editPriority.text.toString().toIntOrNull() ?: 0
        val caseSensitive = binding.switchCaseSensitive.isChecked
        val enabled = binding.switchEnabled.isChecked
        val ignorePattern = binding.editIgnorePattern.text.toString().trim()
        val ignoreGroups = binding.switchIgnoreGroups.isChecked
        val ignoreIndividuals = binding.switchIgnoreIndividuals.isChecked
        val now = System.currentTimeMillis()

        // Phase 3: New fields
        val activeDaysStr = selectedDays.sorted().joinToString(",")
        val minDelaySec = binding.editMinDelaySeconds.text.toString().toIntOrNull() ?: 0
        val maxReplies = binding.editMaxReplies.text.toString().toIntOrNull() ?: 0
        val ignoreHolidays = binding.switchIgnoreHolidays.isChecked

        // Phase 4: AI fields
        val useAi = binding.switchUseAi.isChecked
        val providerName = binding.spinnerAiProvider.text.toString().trim()
        val aiProviderId = aiProviders.find { it.name == providerName }?.id
        val systemPrompt = binding.editSystemPrompt.text.toString().trim().ifBlank { null }
        val aiTemperature = binding.sliderAiTemperature.value.let {
            // Only store if significantly different from 0.7 (default)
            if (Math.abs(it - 0.7f) < 0.05f) null else it
        }

        // Gap fill: Reply system fields
        val replyDelayMs = binding.editReplyDelayMs.text.toString().toLongOrNull() ?: 0L
        val replyHeader = binding.editReplyHeader.text.toString().trim().ifBlank { null }
        val replyFooter = binding.editReplyFooter.text.toString().trim().ifBlank { null }
        val replyPrefix = binding.editReplyPrefix.text.toString().trim().ifBlank { null }
        val probability = binding.sliderProbability.value.toInt()
        val lineBreaks = binding.switchLineBreaks.isChecked

        // Gap fill: Pattern matching
        val caseInsensitive = binding.switchCaseInsensitive.isChecked
        val ignoreAccents = binding.switchIgnoreAccents.isChecked
        val similarityThreshold = binding.sliderSimilarity.value.toInt()

        // Gap fill: Receiver type
        val receiverType = when {
            binding.radioContactsOnly.isChecked -> 1
            binding.radioGroupsOnly.isChecked -> 2
            else -> 0
        }

        // Gap fill: Specific contacts/groups
        val specificContacts = binding.editSpecificContacts.text.toString().trim().ifBlank { null }
        val specificGroups = binding.editSpecificGroups.text.toString().trim().ifBlank { null }

        // Gap fill: Rate limiting
        val dailyReplyLimit = binding.editDailyReplyLimit.text.toString().toIntOrNull() ?: 0
        val preventRepeatingMs = binding.editPreventRepeatingMs.text.toString().toLongOrNull() ?: 0L
        val prevRuleTimeoutMs = binding.editPrevRuleTimeoutMs.text.toString().toLongOrNull() ?: 0L

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (existingRule != null) {
                    val updated = existingRule!!.copy(
                        name = name,
                        pattern = pattern,
                        matchType = matchType,
                        response = response,
                        isRegex = matchType == MatchType.REGEX,
                        caseSensitive = caseSensitive,
                        enabled = enabled,
                        delayMin = delayMin,
                        delayMax = delayMax,
                        priority = priority,
                        responseMode = responseMode,
                        ignorePattern = ignorePattern,
                        ignoreGroups = ignoreGroups,
                        ignoreIndividuals = ignoreIndividuals,
                        updatedAt = now,
                        startTime = startTime,
                        endTime = endTime,
                        activeDays = activeDaysStr,
                        minDelaySeconds = minDelaySec,
                        maxRepliesPerContact = maxReplies,
                        ignoreHolidays = ignoreHolidays,
                        useAi = useAi,
                        aiProviderId = aiProviderId,
                        systemPrompt = systemPrompt,
                        aiTemperature = aiTemperature,
                        replyDelayMs = replyDelayMs,
                        replyHeader = replyHeader,
                        replyFooter = replyFooter,
                        replyPrefix = replyPrefix,
                        probability = probability,
                        lineBreaks = lineBreaks,
                        caseInsensitive = caseInsensitive,
                        ignoreAccents = ignoreAccents,
                        similarityThreshold = similarityThreshold,
                        receiverType = receiverType,
                        specificContacts = specificContacts,
                        specificGroups = specificGroups,
                        dailyReplyLimit = dailyReplyLimit,
                        preventRepeatingMs = preventRepeatingMs,
                        prevRuleTimeoutMs = prevRuleTimeoutMs
                    )
                    db.ruleDao().update(updated)
                } else {
                    val newRule = Rule(
                        name = name,
                        pattern = pattern,
                        matchType = matchType,
                        response = response,
                        isRegex = matchType == MatchType.REGEX,
                        caseSensitive = caseSensitive,
                        enabled = enabled,
                        delayMin = delayMin,
                        delayMax = delayMax,
                        priority = priority,
                        responseMode = responseMode,
                        ignorePattern = ignorePattern,
                        ignoreGroups = ignoreGroups,
                        ignoreIndividuals = ignoreIndividuals,
                        createdAt = now,
                        updatedAt = now,
                        startTime = startTime,
                        endTime = endTime,
                        activeDays = activeDaysStr,
                        minDelaySeconds = minDelaySec,
                        maxRepliesPerContact = maxReplies,
                        ignoreHolidays = ignoreHolidays,
                        useAi = useAi,
                        aiProviderId = aiProviderId,
                        systemPrompt = systemPrompt,
                        aiTemperature = aiTemperature,
                        replyDelayMs = replyDelayMs,
                        replyHeader = replyHeader,
                        replyFooter = replyFooter,
                        replyPrefix = replyPrefix,
                        probability = probability,
                        lineBreaks = lineBreaks,
                        caseInsensitive = caseInsensitive,
                        ignoreAccents = ignoreAccents,
                        similarityThreshold = similarityThreshold,
                        receiverType = receiverType,
                        specificContacts = specificContacts,
                        specificGroups = specificGroups,
                        dailyReplyLimit = dailyReplyLimit,
                        preventRepeatingMs = preventRepeatingMs,
                        prevRuleTimeoutMs = prevRuleTimeoutMs
                    )
                    db.ruleDao().insert(newRule)
                }
            }

            Toast.makeText(this@RuleEditActivity, "Rule saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
