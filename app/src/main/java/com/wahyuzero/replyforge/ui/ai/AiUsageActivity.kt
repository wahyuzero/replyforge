package com.wahyuzero.replyforge.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.databinding.FragmentAiUsageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AiUsageActivity : AppCompatActivity() {

    private lateinit var binding: FragmentAiUsageBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentAiUsageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)

        setupToolbar()
        loadUsageStats()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Usage Stats"
    }

    private fun loadUsageStats() {
        lifecycleScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())

            // Get first day of current month
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

            val stats = withContext(Dispatchers.IO) {
                val todayUsage = db.aiUsageDao().getUsageForDate(today)
                val monthUsage = db.aiUsageDao().getUsageForDateRange(monthStart, today)
                val totalCost = db.aiUsageDao().getTotalCost() ?: 0.0
                val totalTokens = db.aiUsageDao().getTotalTokens() ?: 0L

                Stats(
                    todayTokens = todayUsage.sumOf { it.totalTokens },
                    todayCost = todayUsage.sumOf { it.estimatedCost },
                    todayCalls = todayUsage.size,
                    monthTokens = monthUsage.sumOf { it.totalTokens },
                    monthCost = monthUsage.sumOf { it.estimatedCost },
                    monthCalls = monthUsage.size,
                    totalCost = totalCost,
                    totalTokens = totalTokens
                )
            }

            displayStats(stats)
        }
    }

    private fun displayStats(stats: Stats) {
        binding.textTodayTokens.text = "Tokens: ${stats.todayTokens}"
        binding.textTodayCost.text = "Cost: \$${String.format("%.4f", stats.todayCost)}"
        binding.textTodayCalls.text = "API Calls: ${stats.todayCalls}"

        binding.textMonthTokens.text = "Tokens: ${stats.monthTokens}"
        binding.textMonthCost.text = "Cost: \$${String.format("%.4f", stats.monthCost)}"
        binding.textMonthCalls.text = "API Calls: ${stats.monthCalls}"

        binding.textTotalTokens.text = "Total Tokens: ${stats.totalTokens}"
        binding.textTotalCost.text = "Total Estimated Cost: \$${String.format("%.4f", stats.totalCost)}"
    }

    data class Stats(
        val todayTokens: Int,
        val todayCost: Double,
        val todayCalls: Int,
        val monthTokens: Int,
        val monthCost: Double,
        val monthCalls: Int,
        val totalCost: Double,
        val totalTokens: Long
    )

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
