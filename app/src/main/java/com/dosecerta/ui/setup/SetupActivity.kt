package com.dosecerta.ui.setup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dosecerta.databinding.ActivitySetupBinding

/**
 * Activity hosting the first-time setup/onboarding flow.
 * Displays terms acceptance, notification permission request, and tutorial.
 */
class SetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySetupBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
