package com.wahyuzero.replyforge.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.appcompat.widget.Toolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.databinding.ActivityMainBinding
import com.wahyuzero.replyforge.ui.rule.RuleEditActivity
import com.wahyuzero.replyforge.ui.settings.SettingsActivity
import com.wahyuzero.replyforge.ui.welcome.WelcomeActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appPrefs: AppPrefs
    private var autoReplyEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            appPrefs = AppPrefs(this)

            setupToolbar()
            setupViewPager()
            setupFAB()
            observeSettings()
        } catch (e: Exception) {
            Log.e("MainActivity", "CRASH in onCreate", e)
            // Write crash info to a toast so user can see
            Toast.makeText(this, "Error: ${e.message}\n${e.cause?.message}", Toast.LENGTH_LONG).show()
            // Write to crash log file
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                openFileOutput("main_crash.log", MODE_APPEND).use { fos ->
                    fos.write("=== ${java.util.Date()} ===\n${sw}\n\n".toByteArray())
                }
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationListenerPermission()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun setupViewPager() {
        val pagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_rules)
                1 -> getString(R.string.tab_history)
                2 -> getString(R.string.tab_stats)
                else -> ""
            }
        }.attach()
    }

    private fun setupFAB() {
        binding.fabAddRule.setOnClickListener {
            val intent = Intent(this, RuleEditActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            appPrefs.autoReplyEnabled.collect { enabled ->
                autoReplyEnabled = enabled
                invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val toggleItem = menu.findItem(R.id.action_toggle)
        toggleItem.isChecked = autoReplyEnabled
        toggleItem.setIcon(
            if (autoReplyEnabled) R.drawable.ic_launcher_foreground
            else android.R.drawable.ic_menu_close_clear_cancel
        )

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle -> {
                val newValue = !autoReplyEnabled
                lifecycleScope.launch(Dispatchers.IO) {
                    appPrefs.setAutoReplyEnabled(newValue)
                }
                item.isChecked = newValue
                Toast.makeText(
                    this,
                    if (newValue) getString(R.string.auto_reply_enabled) else getString(R.string.auto_reply_disabled),
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkNotificationListenerPermission() {
        val componentName = ComponentName(this, com.wahyuzero.replyforge.service.WANotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val hasPermission = enabledListeners?.contains(componentName.flattenToString()) == true

        if (!hasPermission) {
            Toast.makeText(this, getString(R.string.notification_access_required), Toast.LENGTH_LONG).show()
        }
    }

    private inner class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> RulesFragment()
                1 -> HistoryFragment()
                2 -> StatsFragment()
                else -> RulesFragment()
            }
        }
    }
}
