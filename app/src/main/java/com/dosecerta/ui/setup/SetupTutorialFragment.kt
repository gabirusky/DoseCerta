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
import com.dosecerta.databinding.ItemTutorialCardBinding
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
    
    private lateinit var cardBinding: ItemTutorialCardBinding
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
        currentStep = savedInstanceState?.getInt("tutorial_step", 0)?.coerceIn(0, tutorialSteps.lastIndex) ?: requireActivity().getPreferences(android.content.Context.MODE_PRIVATE).getInt("tutorial_step", 0).coerceIn(0, tutorialSteps.lastIndex)
        
        // The included card layout is already bound
        cardBinding = binding.tutorialCard
        
        updateCardContent()
        setupButton()
    }
    
    private fun updateCardContent() {
        val step = tutorialSteps[currentStep]
        
        cardBinding.textTitle.setText(step.titleRes)
        cardBinding.textDescription.setText(step.descriptionRes)
        cardBinding.imageTutorialIcon.setImageResource(when (currentStep) {
            0 -> R.drawable.ic_home
            1 -> R.drawable.ic_medications
            else -> R.drawable.ic_notifications
        })
        
        cardBinding.textProgress.text = getString(R.string.ui_setup_progress, currentStep + 1, tutorialSteps.size)
        updateIndicators()
        updateButtonText()
    }
    
    private fun updateIndicators() {
        val indicators = listOf(
            cardBinding.indicator1,
            cardBinding.indicator2,
            cardBinding.indicator3
        )
        indicators.forEachIndexed { index, view ->
            val diameter = resources.getDimensionPixelSize(R.dimen.setup_indicator_size)
            view.layoutParams = view.layoutParams.apply { width = diameter; height = diameter }
            view.minimumWidth = 0
            view.minimumHeight = 0
            view.isSelected = index == currentStep
            view.setBackgroundResource(
                if (index == currentStep) {
                    R.drawable.indicator_dot_selected
                } else {
                    R.drawable.indicator_dot_unselected
                }
            )
        }
    }
    
    private fun updateButtonText() {
        val isLastStep = currentStep == tutorialSteps.size - 1
        cardBinding.buttonAction.text = if (isLastStep) {
            getString(R.string.setup_tutorial_button_start)
        } else {
            getString(R.string.setup_tutorial_button_next)
        }
    }
    
    private fun setupButton() {
        cardBinding.buttonAction.setOnClickListener {
            val isLastStep = currentStep == tutorialSteps.size - 1
            
            if (isLastStep) {
                completeSetup()
            } else {
                currentStep++
                requireActivity().getPreferences(android.content.Context.MODE_PRIVATE).edit().putInt("tutorial_step", currentStep).apply()
                updateCardContent()
            }
        }
    }
    
    private fun completeSetup() {
        cardBinding.buttonAction.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            // Mark setup as completed
            settingsPreferences.setSetupCompleted()
            
            // Launch MainActivity and finish SetupActivity
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tutorial_step", currentStep)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
