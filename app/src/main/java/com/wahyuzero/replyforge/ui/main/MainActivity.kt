package com.wahyuzero.replyforge.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import com.wahyuzero.replyforge.service.WANotificationListener
import com.wahyuzero.replyforge.ui.rule.RuleEditActivity
import com.wahyuzero.replyforge.ui.settings.SettingsActivity
import com.wahyuzero.replyforge.ui.welcome.WelcomeActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RF_DEBUG"
        private const val REQUEST_POST_NOTIFICATIONS = 1001
    }

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
            
            // Request POST_NOTIFICATIONS permission (Android 13+)
            requestNotificationPermission()
            
            // Log service status
            logServiceStatus()
        } catch (e: Exception) {
            Log.e(TAG, "CRASH in onCreate", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationListenerPermission()
        logServiceStatus()
    }

    private fun logServiceStatus() {
        val componentName = ComponentName(this, WANotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val hasNotifAccess = enabledListeners?.contains(componentName.flattenToString()) == true
        
        val hasPostNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        
        val isNotifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        
        Log.d(TAG, "=== SERVICE STATUS ===")
        Log.d(TAG, "  Notification Listener enabled: $hasNotifAccess")
        Log.d(TAG, "  POST_NOTIFICATIONS permission: $hasPostNotif")
        Log.d(TAG, "  Notifications enabled: $isNotifEnabled")
        
        if (!hasNotifAccess) {
            Log.w(TAG, "  ⚠️ Notification Listener NOT enabled! App won't work!")
        }
        if (!isNotifEnabled) {
            Log.w(TAG, "  ⚠️ App notifications disabled in system settings!")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted")
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                Toast.makeText(this, "Notification permission needed for auto-reply to work!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun setupViewPager() {
        val pagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = getString(R.string.tab_rules)
                    tab.setIcon(R.drawable.ic_rules)
                }
                1 -> {
                    tab.text = getString(R.string.tab_history)
                    tab.setIcon(R.drawable.ic_history)
                }
                2 -> {
                    tab.text = getString(R.string.tab_stats)
                    tab.setIcon(R.drawable.ic_stats)
                }
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
                Log.d(TAG, "Auto-reply pref changed: $enabled")
                invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val toggleItem = menu.findItem(R.id.action_toggle)
        toggleItem.isChecked = autoReplyEnabled
        toggleItem.setIcon(
            if (autoReplyEnabled) R.drawable.ic_power_on
            else R.drawable.ic_power_off
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
        val componentName = ComponentName(this, WANotificationListener::class.java)
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
