package com.wahyuzero.replyforge.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.wahyuzero.replyforge.CrashHandler
import com.wahyuzero.replyforge.CrashLogEntry
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVersion()
        setupCrashLogs()
        setupGitHubCard()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_about)
    }

    private fun setupVersion() {
        try {
            val pm = packageManager
            val info = pm.getPackageInfo(packageName, 0)
            binding.textVersion.text = "Version ${info.versionName}"
        } catch (e: Exception) {
            binding.textVersion.text = "Version 1.0.0"
        }
    }

    private fun setupCrashLogs() {
        val logs = CrashHandler.getCrashLogs(this)

        if (logs.isEmpty()) {
            binding.textCrashCount.visibility = View.GONE
            binding.textCrashStatus.text = "No crashes recorded ✅"
        } else {
            binding.textCrashCount.visibility = View.VISIBLE
            binding.textCrashCount.text = logs.size.toString()
            val lastCrash = logs.first()
            binding.textCrashStatus.text = "${logs.size} crash(es) — Last: ${lastCrash.summary}"
        }

        binding.cardCrashLogs.setOnClickListener {
            if (logs.isEmpty()) {
                Toast.makeText(this, "No crash logs to show", Toast.LENGTH_SHORT).show()
            } else {
                showCrashLogDialog(logs)
            }
        }
    }

    private fun showCrashLogDialog(logs: List<CrashLogEntry>) {
        val items = logs.map { "${it.timeString} — ${it.summary}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Crash Logs (${logs.size})")
            .setItems(items) { _, which ->
                showCrashDetail(logs[which])
            }
            .setPositiveButton("Clear All") { _, _ ->
                val count = CrashHandler.clearAllCrashLogs(this)
                Toast.makeText(this, "Cleared $count crash log(s)", Toast.LENGTH_SHORT).show()
                setupCrashLogs()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCrashDetail(entry: CrashLogEntry) {
        val view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = entry.fullLog
        textView.textSize = 11f
        textView.setPadding(48, 32, 48, 32)

        AlertDialog.Builder(this)
            .setTitle(entry.fileName)
            .setView(view)
            .setPositiveButton("Share") { _, _ ->
                shareCrashLog(entry)
            }
            .setNegativeButton("Delete") { _, _ ->
                CrashHandler.deleteCrashLog(this, entry.fileName)
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                setupCrashLogs()
            }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun shareCrashLog(entry: CrashLogEntry) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ReplyForge Crash: ${entry.fileName}")
            putExtra(Intent.EXTRA_TEXT, entry.fullLog)
        }
        startActivity(Intent.createChooser(intent, "Share Crash Log"))
    }

    private fun setupGitHubCard() {
        // Card GitHub click - open repo URL
        val cardGitHub = findViewById<View>(R.id.cardGitHub)
        cardGitHub?.setOnClickListener {
            // Try to open GitHub repo
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wahyuzero/replyforge"))
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
