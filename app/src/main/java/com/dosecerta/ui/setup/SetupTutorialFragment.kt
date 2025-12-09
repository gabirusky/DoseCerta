package com.dosecerta.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dosecerta.R
import com.dosecerta.databinding.FragmentSetupTutorialBinding
import com.dosecerta.ui.MainActivity
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.launch

/**
 * Data class representing a tutorial step.
 */
data class TutorialStep(
    val titleRes: Int,
    val descriptionRes: Int
)

/**
 * Step 3 of setup: Tutorial with compact overlay cards.
 * Shows a small card at the bottom with title, description, dots, and next button.
 */
class SetupTutorialFragment : Fragment() {
    
    private var _binding: FragmentSetupTutorialBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settingsPreferences: SettingsPreferences
    
    private var currentStep = 0
    
    private val tutorialSteps = listOf(
        TutorialStep(
            titleRes = R.string.setup_tutorial_title_1,
            descriptionRes = R.string.setup_tutorial_desc_1
        ),
        TutorialStep(
            titleRes = R.string.setup_tutorial_title_2,
            descriptionRes = R.string.setup_tutorial_desc_2
        ),
        TutorialStep(
            titleRes = R.string.setup_tutorial_title_3,
            descriptionRes = R.string.setup_tutorial_desc_3
        )
    )
    
    private val indicators by lazy {
        listOf(
            binding.tutorialCard.findViewById<View>(R.id.indicator_1),
            binding.tutorialCard.findViewById<View>(R.id.indicator_2),
            binding.tutorialCard.findViewById<View>(R.id.indicator_3)
        )
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupTutorialBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsPreferences = SettingsPreferences(requireContext())
        
        updateCardContent()
        setupButton()
    }
    
    private fun updateCardContent() {
        val step = tutorialSteps[currentStep]
        
        binding.tutorialCard.findViewById<android.widget.TextView>(R.id.text_title)
            .setText(step.titleRes)
        binding.tutorialCard.findViewById<android.widget.TextView>(R.id.text_description)
            .setText(step.descriptionRes)
        
        updateIndicators()
        updateButtonText()
    }
    
    private fun updateIndicators() {
        indicators.forEachIndexed { index, view ->
            view.setBackgroundResource(
                if (index == currentStep) {
                    R.drawable.indicator_dot_white_selected
                } else {
                    R.drawable.indicator_dot_white_unselected
                }
            )
        }
    }
    
    private fun updateButtonText() {
        val isLastStep = currentStep == tutorialSteps.size - 1
        binding.tutorialCard.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_action).text =
            if (isLastStep) {
                getString(R.string.setup_tutorial_button_start)
            } else {
                getString(R.string.setup_tutorial_button_next)
            }
    }
    
    private fun setupButton() {
        binding.tutorialCard.findViewById<com.google.android.material.button.MaterialButton>(R.id.button_action)
            .setOnClickListener {
                val isLastStep = currentStep == tutorialSteps.size - 1
                
                if (isLastStep) {
                    completeSetup()
                } else {
                    currentStep++
                    updateCardContent()
                }
            }
    }
    
    private fun completeSetup() {
        lifecycleScope.launch {
            // Mark setup as completed
            settingsPreferences.setSetupCompleted()
            
            // Launch MainActivity and finish SetupActivity
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
