package com.wahyuzero.replyforge.ui.ai

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wahyuzero.replyforge.R
import com.wahyuzero.replyforge.data.db.AppDatabase
import com.wahyuzero.replyforge.data.model.AiProvider
import com.wahyuzero.replyforge.data.model.AiProviderType
import com.wahyuzero.replyforge.databinding.ActivityAiProviderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiProviderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiProviderBinding
    private lateinit var db: AppDatabase
    private lateinit var providerAdapter: ProviderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiProviderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        setupAddButton()
        observeProviders()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_ai_providers)
    }

    private fun setupRecyclerView() {
        providerAdapter = ProviderAdapter(
            onEdit = { provider -> showEditDialog(provider) },
            onDelete = { provider -> showDeleteConfirm(provider) },
            onToggleActive = { provider ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.aiProviderDao().setActive(provider.id, !provider.isActive)
                }
            }
        )
        binding.recyclerProviders.apply {
            layoutManager = LinearLayoutManager(this@AiProviderActivity)
            adapter = providerAdapter
        }
    }

    private fun setupAddButton() {
        binding.fabAddProvider.setOnClickListener {
            showEditDialog(null)
        }
    }

    private fun observeProviders() {
        lifecycleScope.launch {
            db.aiProviderDao().getAllProviders().collectLatest { providers ->
                providerAdapter.submitList(providers)
                binding.textEmpty.visibility = if (providers.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showEditDialog(existingProvider: AiProvider?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_provider_edit, null)

        val editName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editProviderName)
        val spinnerType = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.spinnerProviderType)
        val editBaseUrl = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editBaseUrl)
        val editApiKey = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editApiKey)
        val editModel = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editModelName)
        val editMaxTokens = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editMaxTokens)
        val editTemperature = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editTemperature)

        // Setup type dropdown
        val types = AiProviderType.values().map { it.name }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        spinnerType.setAdapter(typeAdapter)

        // Pre-fill if editing
        if (existingProvider != null) {
            editName.setText(existingProvider.name)
            spinnerType.setText(existingProvider.type.name, false)
            editBaseUrl.setText(existingProvider.baseUrl)
            editApiKey.setText(existingProvider.apiKey)
            editModel.setText(existingProvider.modelName)
            editMaxTokens.setText(existingProvider.maxTokens.toString())
            editTemperature.setText(existingProvider.temperature.toString())
        } else {
            spinnerType.setText(AiProviderType.OPENAI.name, false)
            editBaseUrl.setText("https://api.openai.com")
            editModel.setText("gpt-4o-mini")
            editMaxTokens.setText("1024")
            editTemperature.setText("0.7")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existingProvider != null) getString(R.string.title_edit_provider) else getString(R.string.title_add_provider))
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val name = editName.text.toString().trim()
                val typeName = spinnerType.text.toString()
                val baseUrl = editBaseUrl.text.toString().trim()
                val apiKey = editApiKey.text.toString().trim()
                val modelName = editModel.text.toString().trim()
                val maxTokens = editMaxTokens.text.toString().toIntOrNull() ?: 1024
                val temperature = editTemperature.text.toString().toFloatOrNull() ?: 0.7f

                if (name.isBlank() || baseUrl.isBlank() || apiKey.isBlank() || modelName.isBlank()) {
                    Toast.makeText(this, getString(R.string.all_fields_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val type = try { AiProviderType.valueOf(typeName) } catch (e: Exception) { AiProviderType.OPENAI }

                lifecycleScope.launch(Dispatchers.IO) {
                    if (existingProvider != null) {
                        db.aiProviderDao().update(
                            existingProvider.copy(
                                name = name,
                                type = type,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                modelName = modelName,
                                maxTokens = maxTokens,
                                temperature = temperature
                            )
                        )
                    } else {
                        db.aiProviderDao().insert(
                            AiProvider(
                                name = name,
                                type = type,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                modelName = modelName,
                                maxTokens = maxTokens,
                                temperature = temperature
                            )
                        )
                    }
                }

                Toast.makeText(this, if (existingProvider != null) getString(R.string.provider_updated) else getString(R.string.provider_added), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirm(provider: AiProvider) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_delete_provider))
            .setMessage(getString(R.string.delete_provider_message, provider.name))
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.aiProviderDao().delete(provider)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    inner class ProviderAdapter(
        private val onEdit: (AiProvider) -> Unit,
        private val onDelete: (AiProvider) -> Unit,
        private val onToggleActive: (AiProvider) -> Unit
    ) : RecyclerView.Adapter<ProviderAdapter.ProviderViewHolder>() {

        private var items: List<AiProvider> = emptyList()

        fun submitList(newItems: List<AiProvider>) {
            val oldSize = items.size
            items = newItems
            val newSize = items.size
            when {
                oldSize == 0 && newSize > 0 -> notifyItemRangeInserted(0, newSize)
                newSize == 0 && oldSize > 0 -> notifyItemRangeRemoved(0, oldSize)
                newSize == oldSize -> notifyItemRangeChanged(0, newSize)
                else -> {
                    if (newSize > oldSize) {
                        notifyItemRangeChanged(0, oldSize)
                        notifyItemRangeInserted(oldSize, newSize - oldSize)
                    } else {
                        notifyItemRangeChanged(0, newSize)
                        notifyItemRangeRemoved(newSize, oldSize - newSize)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_provider, parent, false)
            return ProviderViewHolder(view)
        }

        override fun onBindViewHolder(holder: ProviderViewHolder, position: Int) {
            val provider = items[position]
            holder.nameText.text = provider.name
            holder.typeText.text = "${provider.type.name} · ${provider.modelName}"
            holder.baseUrlText.text = provider.baseUrl

            holder.activeSwitch.setOnCheckedChangeListener(null)
            holder.activeSwitch.isChecked = provider.isActive
            holder.activeSwitch.setOnCheckedChangeListener { _, _ ->
                onToggleActive(provider)
            }

            holder.editButton.setOnClickListener { onEdit(provider) }
            holder.deleteButton.setOnClickListener { onDelete(provider) }
        }

        override fun getItemCount() = items.size

        inner class ProviderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.textProviderName)
            val typeText: TextView = view.findViewById(R.id.textProviderType)
            val baseUrlText: TextView = view.findViewById(R.id.textProviderBaseUrl)
            val activeSwitch: com.google.android.material.switchmaterial.SwitchMaterial = view.findViewById(R.id.switchProviderActive)
            val editButton: View = view.findViewById(R.id.btnEditProvider)
            val deleteButton: View = view.findViewById(R.id.btnDeleteProvider)
        }
    }
}
