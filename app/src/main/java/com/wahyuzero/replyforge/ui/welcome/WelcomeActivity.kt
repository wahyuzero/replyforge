package com.wahyuzero.replyforge.ui.welcome

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.prefs.AppPrefs
import com.wahyuzero.replyforge.databinding.ActivityWelcomeBinding
import com.wahyuzero.replyforge.service.WANotificationListener
import com.wahyuzero.replyforge.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var appPrefs: AppPrefs
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPrefs = AppPrefs(this)

        checkIfWelcomeDone()

        setupViewPager()
        setupDots()
        setupButton()
    }

    private fun checkIfWelcomeDone() {
        CoroutineScope(Dispatchers.Main).launch {
            appPrefs.welcomeDone.collect { done ->
                if (done) {
                    navigateToMain()
                }
            }
        }
    }

    private fun setupViewPager() {
        val pages = listOf(
            layoutInflater.inflate(R.layout.welcome_page_1, binding.viewPager as ViewGroup, false),
            layoutInflater.inflate(R.layout.welcome_page_2, binding.viewPager as ViewGroup, false),
            layoutInflater.inflate(R.layout.welcome_page_3, binding.viewPager as ViewGroup, false)
        )

        val adapter = WelcomePagerAdapter(pages)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateDots(position)
                updateButton(position)
            }
        })
    }

    private fun setupDots() {
        val dotsLayout = binding.dotsLayout
        dotsLayout.removeAllViews()
        for (i in 0 until 3) {
            val dot = View(this)
            val size = (8 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(4, 0, 4, 0)
            dot.layoutParams = params
            dot.background = getDrawable(R.drawable.dot_indicator)
            dotsLayout.addView(dot)
        }
        updateDots(0)
    }

    private fun updateDots(position: Int) {
        val dotsLayout = binding.dotsLayout
        for (i in 0 until dotsLayout.childCount) {
            val dot = dotsLayout.getChildAt(i)
            val size = if (i == position) (16 * resources.displayMetrics.density).toInt()
            else (8 * resources.displayMetrics.density).toInt()
            dot.layoutParams = LinearLayout.LayoutParams(size, size)
            dot.alpha = if (i == position) 1.0f else 0.4f
        }
    }

    private fun setupButton() {
        binding.buttonNext.setOnClickListener {
            if (currentPage == 0) {
                binding.viewPager.currentItem = 1
            } else if (currentPage == 1) {
                openNotificationSettings()
            } else {
                finishWelcome()
            }
        }
        updateButton(0)
    }

    private fun updateButton(position: Int) {
        binding.buttonNext.text = when (position) {
            0 -> "Next"
            1 -> "Grant Notification Access"
            2 -> "Get Started"
            else -> "Next"
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            binding.viewPager.currentItem = 2
        } catch (e: Exception) {
            binding.viewPager.currentItem = 2
        }
    }

    private fun finishWelcome() {
        CoroutineScope(Dispatchers.IO).launch {
            appPrefs.setWelcomeDone(true)
        }
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private inner class WelcomePagerAdapter(
        private val pages: List<View>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<WelcomePagerAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = pages[viewType]
            (view.parent as? ViewGroup)?.removeView(view)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {}

        override fun getItemCount(): Int = pages.size

        override fun getItemViewType(position: Int): Int = position

        inner class PageViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
    }
}
