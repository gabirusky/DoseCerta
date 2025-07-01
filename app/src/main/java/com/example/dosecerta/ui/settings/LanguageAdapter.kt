package com.example.dosecerta.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.databinding.ListItemLanguageBinding
import com.example.dosecerta.util.LanguageManager

class LanguageAdapter(
    private val languages: List<LanguageManager.Language>,
    private var selectedLanguage: LanguageManager.Language,
    private val onLanguageSelected: (LanguageManager.Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ListItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val language = languages[position]
        holder.bind(language, language == selectedLanguage) { selectedLang ->
            val oldSelected = selectedLanguage
            selectedLanguage = selectedLang
            onLanguageSelected(selectedLang)
            
            // Update UI
            notifyItemChanged(languages.indexOf(oldSelected))
            notifyItemChanged(languages.indexOf(selectedLang))
        }
    }

    override fun getItemCount(): Int = languages.size

    class LanguageViewHolder(
        private val binding: ListItemLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            language: LanguageManager.Language,
            isSelected: Boolean,
            onLanguageClick: (LanguageManager.Language) -> Unit
        ) {
            binding.textLanguageName.text = language.displayName
            binding.radioLanguage.isChecked = isSelected
            
            binding.root.setOnClickListener {
                onLanguageClick(language)
            }
            
            binding.radioLanguage.setOnClickListener {
                onLanguageClick(language)
            }
        }
    }
}