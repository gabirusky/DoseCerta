package com.example.dosecerta.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.R
import com.example.dosecerta.databinding.FragmentLanguageSettingsBinding
import com.example.dosecerta.util.LanguageManager
import com.google.android.material.snackbar.Snackbar

class LanguageSettingsFragment : Fragment() {

    private var _binding: FragmentLanguageSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var languageAdapter: LanguageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        val currentLanguage = LanguageManager.getCurrentLanguage(requireContext())
        
        languageAdapter = LanguageAdapter(
            languages = LanguageManager.getAllLanguages(),
            selectedLanguage = currentLanguage,
            onLanguageSelected = { language ->
                if (language != currentLanguage) {
                    LanguageManager.setLanguage(requireContext(), language)
                    showLanguageChangedMessage()
                }
            }
        )
        
        binding.recyclerViewLanguages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = languageAdapter
        }
    }

    private fun showLanguageChangedMessage() {
        Snackbar.make(
            binding.root,
            getString(R.string.restart_app_language),
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}