package com.wahyuzero.replyforge.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wahyuzero.replyforge.data.model.ReplyHistory
import com.wahyuzero.replyforge.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<ReplyHistory, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(history: ReplyHistory) {
            binding.textSender.text = history.sender
            binding.textMessage.text = "Message: ${history.message}"
            binding.textResponse.text = "Reply: ${history.response}"

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.textTimestamp.text = sdf.format(Date(history.timestamp))

            if (history.isGroup) {
                binding.textGroupInfo.text = "Group: ${history.groupName ?: "Unknown"}"
                binding.textGroupInfo.visibility = android.view.View.VISIBLE
            } else {
                binding.textGroupInfo.visibility = android.view.View.GONE
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<ReplyHistory>() {
        override fun areItemsTheSame(oldItem: ReplyHistory, newItem: ReplyHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReplyHistory, newItem: ReplyHistory): Boolean {
            return oldItem == newItem
        }
    }
}
