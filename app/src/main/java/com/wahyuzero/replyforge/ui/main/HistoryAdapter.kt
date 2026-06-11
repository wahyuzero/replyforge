package com.wahyuzero.replyforge.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.model.ReplyHistory
import com.wahyuzero.replyforge.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<ReplyHistory, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

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
            val ctx = itemView.context
            binding.textSender.text = history.sender
            binding.textMessage.text = "${ctx.getString(R.string.label_message)}${history.message}"
            binding.textResponse.text = "${ctx.getString(R.string.label_reply)}${history.response}"

            binding.textTimestamp.text = dateFormat.format(Date(history.timestamp))

            if (history.isGroup) {
                binding.textGroupInfo.text = "${ctx.getString(R.string.label_group)}${history.groupName ?: ctx.getString(R.string.label_unknown)}"
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
