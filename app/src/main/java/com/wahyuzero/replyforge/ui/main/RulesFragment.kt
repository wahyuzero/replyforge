package com.wahyuzero.replyforge.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.model.Rule
import com.wahyuzero.replyforge.databinding.FragmentRulesBinding
import com.wahyuzero.replyforge.ui.rule.RuleEditActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RulesFragment : Fragment() {

    private var _binding: FragmentRulesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RulesAdapter
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getInstance(requireContext())

        adapter = RulesAdapter(
            onRuleClick = { rule ->
                val intent = Intent(requireContext(), RuleEditActivity::class.java)
                intent.putExtra("rule_id", rule.id)
                startActivity(intent)
            },
            onToggleClick = { rule, enabled ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.ruleDao().update(rule.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
                }
            },
            onLongClick = { rule ->
                showDeleteDialog(rule)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        observeRules()
    }

    private fun observeRules() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.ruleDao().getAllRules().collect { rules ->
                adapter.submitList(rules)
                binding.textEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showDeleteDialog(rule: Rule) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Rule")
            .setMessage("Are you sure you want to delete \"${rule.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.ruleDao().delete(rule)
                }
                Toast.makeText(requireContext(), "Rule deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
