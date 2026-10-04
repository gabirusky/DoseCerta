package com.dosecerta.ui.setup

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.dosecerta.R
import com.dosecerta.databinding.ActivitySetupBinding
import com.dosecerta.ui.WindowInsetsHelper
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Consent, permission choices and tutorial form one resumable navigation flow. */
class SetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.apply(binding.root)
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_setup) as NavHostFragment).navController
        val progress = getPreferences(MODE_PRIVATE)
        lifecycleScope.launch {
            if (savedInstanceState == null && SettingsPreferences(this@SetupActivity).hasAcceptedTerms.first()) {
                nav.navigate(if (progress.getInt("setup_destination", 0) == R.id.setupTutorialFragment) R.id.setupTutorialFragment else R.id.setupNotificationsFragment,
                    null, androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.setupTermsFragment, true).build())
            }
            nav.addOnDestinationChangedListener { _, destination, _ ->
                progress.edit().putInt("setup_destination", destination.id).apply()
            }
        }
    }
}
