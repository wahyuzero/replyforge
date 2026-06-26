package com.wahyuzero.replyforge.ui.settings

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.model.Holiday
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.databinding.ActivitySettingsBinding
import com.wahyuzero.replyforge.service.WANotificationListener
import com.wahyuzero.replyforge.ui.about.AboutActivity
import com.wahyuzero.replyforge.ui.ai.AiProviderActivity
import com.wahyuzero.replyforge.ui.ai.AiUsageActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appPrefs: AppPrefs
    private lateinit var db: AppDatabase
    private lateinit var holidayAdapter: HolidayAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPrefs = AppPrefs(this)
        db = AppDatabase.getInstance(this)

        setupToolbar()
        setupAutoReplyToggle()
        setupNotificationListenerStatus()
        setupNotificationSettingsLink()
        setupClearHistory()
        setupAbout()
        setupAwayMode()
        setupHolidayManagement()
        setupAiManagement()
        observeSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_settings)
    }

    private fun setupAutoReplyToggle() {
        binding.switchAutoReply.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch(Dispatchers.IO) {
                appPrefs.setAutoReplyEnabled(isChecked)
            }
        }
    }

    private fun setupNotificationListenerStatus() {
        val componentName = ComponentName(this, WANotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val hasPermission = enabledListeners?.contains(componentName.flattenToString()) == true

        binding.textServiceStatus.text = if (hasPermission) {
            getString(R.string.notification_access_granted)
        } else {
            getString(R.string.notification_access_denied)
        }
        binding.textServiceStatus.setTextColor(
            if (hasPermission) {
                android.graphics.Color.parseColor("#4CAF50")
            } else {
                android.graphics.Color.parseColor("#F44336")
            }
        )
    }

    private fun setupNotificationSettingsLink() {
        binding.itemNotificationSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.could_not_open_settings), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClearHistory() {
        binding.itemClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_clear_history))
                .setMessage(getString(R.string.clear_history_message))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.getInstance(this@SettingsActivity).historyDao().deleteAll()
                    }
                    Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun setupAbout() {
        binding.textVersion.text = getString(R.string.version_info, com.wahyuzero.replyforge.BuildConfig.VERSION_NAME)
        binding.textAbout.text = getString(R.string.about_description)
        binding.cardAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    // Phase 3: Away Mode
    private fun setupAwayMode() {
        binding.switchAwayMode.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutAwayMessage.visibility = if (isChecked) View.VISIBLE else View.GONE
            lifecycleScope.launch(Dispatchers.IO) {
                appPrefs.setAwayMode(isChecked)
            }
        }

        // Save away message on focus lost
        binding.editAwayMessage.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val msg = binding.editAwayMessage.text.toString()
                lifecycleScope.launch(Dispatchers.IO) {
                    appPrefs.setAwayMessage(msg)
                }
            }
        }
    }

    // Phase 3: Holiday Management
    private fun setupHolidayManagement() {
        holidayAdapter = HolidayAdapter(
            onDelete = { holiday ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.holidayDao().delete(holiday)
                }
            }
        )
        binding.recyclerHolidays.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = holidayAdapter
        }

        binding.btnAddHoliday.setOnClickListener {
            val name = binding.editHolidayName.text.toString().trim()
            val date = binding.editHolidayDate.text.toString().trim()
            if (name.isBlank() || date.isBlank()) {
                Toast.makeText(this, getString(R.string.name_date_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                Toast.makeText(this, getString(R.string.date_format_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val recurring = binding.switchHolidayRecurring.isChecked
            lifecycleScope.launch(Dispatchers.IO) {
                db.holidayDao().insert(Holiday(name = name, date = date, isRecurringAnnual = recurring))
            }
            binding.editHolidayName.text?.clear()
            binding.editHolidayDate.text?.clear()
            binding.switchHolidayRecurring.isChecked = false
            Toast.makeText(this, getString(R.string.holiday_added), Toast.LENGTH_SHORT).show()
        }

        // Observe holidays
        lifecycleScope.launch {
            db.holidayDao().getAllHolidays().collectLatest { holidays ->
                holidayAdapter.submitList(holidays)
            }
        }
    }

    // Phase 4: AI Management
    private fun setupAiManagement() {
        binding.itemAiProviders.setOnClickListener {
            startActivity(Intent(this, AiProviderActivity::class.java))
        }

        binding.itemAiUsage.setOnClickListener {
            startActivity(Intent(this, AiUsageActivity::class.java))
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            appPrefs.autoReplyEnabled.collect { enabled ->
                binding.switchAutoReply.isChecked = enabled
            }
        }

        // Observe away mode
        lifecycleScope.launch {
            appPrefs.awayMode.collect { isAway ->
                binding.switchAwayMode.isChecked = isAway
                binding.layoutAwayMessage.visibility = if (isAway) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            appPrefs.awayMessage.collect { msg ->
                if (binding.editAwayMessage.text.toString() != msg) {
                    binding.editAwayMessage.setText(msg)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupNotificationListenerStatus()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Simple RecyclerView adapter for holidays
    inner class HolidayAdapter(
        private val onDelete: (Holiday) -> Unit
    ) : RecyclerView.Adapter<HolidayAdapter.HolidayViewHolder>() {

        private var items: List<Holiday> = emptyList()

        fun submitList(newItems: List<Holiday>) {
            val oldSize = items.size
            items = newItems
            val newSize = items.size
            when {
                oldSize == 0 && newSize > 0 -> notifyItemRangeInserted(0, newSize)
                newSize == 0 && oldSize > 0 -> notifyItemRangeRemoved(0, oldSize)
                newSize == oldSize -> notifyItemRangeChanged(0, newSize)
                else -> {
                    if (newSize > oldSize) {
                        notifyItemRangeChanged(0, oldSize)
                        notifyItemRangeInserted(oldSize, newSize - oldSize)
                    } else {
                        notifyItemRangeChanged(0, newSize)
                        notifyItemRangeRemoved(newSize, oldSize - newSize)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolidayViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_holiday, parent, false)
            return HolidayViewHolder(view)
        }

        override fun onBindViewHolder(holder: HolidayViewHolder, position: Int) {
            val holiday = items[position]
            holder.nameText.text = holiday.name
            val suffix = if (holiday.isRecurringAnnual) " (Annual)" else ""
            holder.dateText.text = "${holiday.date}$suffix"
            holder.deleteChip.setOnClickListener { onDelete(holiday) }
        }

        override fun getItemCount() = items.size

        inner class HolidayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.textHolidayName)
            val dateText: TextView = view.findViewById(R.id.textHolidayDate)
            val deleteChip: View = view.findViewById(R.id.chipDeleteHoliday)
        }
    }
}
