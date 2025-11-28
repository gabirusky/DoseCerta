package com.dosecerta.ui.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dosecerta.databinding.FragmentPrivacyPolicyBinding

/**
 * Privacy policy fragment displaying LGPD compliance information.
 */
class PrivacyPolicyFragment : Fragment() {
    
    private var _binding: FragmentPrivacyPolicyBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivacyPolicyBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Privacy policy text is set in the layout XML from strings.xml
        //binding.textPrivacyPolicy is already populated with @string/privacy_policy_full_text
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
