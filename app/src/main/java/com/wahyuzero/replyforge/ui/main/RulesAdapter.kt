package com.wahyuzero.replyforge.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.model.Rule
import com.wahyuzero.replyforge.databinding.ItemRuleBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RulesAdapter(
    private val onRuleClick: (Rule) -> Unit,
    private val onToggleClick: (Rule, Boolean) -> Unit,
    private val onLongClick: (Rule) -> Unit
) : ListAdapter<Rule, RulesAdapter.RuleViewHolder>(RuleDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RuleViewHolder(
        private val binding: ItemRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: Rule) {
            val ctx = itemView.context
            binding.textRuleName.text = rule.name
            binding.textPattern.text = "${ctx.getString(R.string.label_pattern)}${rule.pattern}"
            binding.textResponse.text = "${ctx.getString(R.string.label_reply)}${rule.response}"
            binding.textMatchType.text = rule.matchType.displayName

            binding.textDate.text = dateFormat.format(Date(rule.updatedAt))

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = rule.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleClick(rule, isChecked)
            }

            binding.root.setOnClickListener {
                onRuleClick(rule)
            }

            binding.root.setOnLongClickListener {
                onLongClick(rule)
                true
            }

            val alpha = if (rule.enabled) 1.0f else 0.5f
            binding.root.alpha = alpha
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<Rule>() {
        override fun areItemsTheSame(oldItem: Rule, newItem: Rule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Rule, newItem: Rule): Boolean {
            return oldItem == newItem
        }
    }
}
