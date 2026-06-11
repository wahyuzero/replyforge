package com.wahyuzero.replyforge.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.databinding.FragmentStatsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        val db = AppDatabase.getInstance(requireContext())
        val historyDao = db.historyDao()

        viewLifecycleOwner.lifecycleScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endOfDay = cal.timeInMillis

            val totalReplies = withContext(Dispatchers.IO) { historyDao.getTotalReplies() }
            val repliesToday = withContext(Dispatchers.IO) { historyDao.getRepliesToday(startOfDay, endOfDay) }
            val repliesContacts = withContext(Dispatchers.IO) { historyDao.getRepliesToContacts() }
            val repliesGroups = withContext(Dispatchers.IO) { historyDao.getRepliesToGroups() }

            binding.textTotalReplies.text = totalReplies.toString()
            binding.textRepliesToday.text = repliesToday.toString()
            binding.textRepliesContacts.text = repliesContacts.toString()
            binding.textRepliesGroups.text = repliesGroups.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
