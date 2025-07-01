package com.example.dosecerta.ui.add_edit_med

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.dosecerta.DoseCertaApplication
import com.example.dosecerta.data.repository.MedicationRepository

/**
 * Factory for providing the Application, MedicationRepository and SavedStateHandle to AddEditMedViewModel.
 * Uses CreationExtras for modern ViewModel creation.
 */
object AddEditMedViewModelFactory : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val savedStateHandle = extras.createSavedStateHandle()
        // Get repository from Application instance (assuming it's available there)
        val repository = (application as DoseCertaApplication).repository
        
        if (modelClass.isAssignableFrom(AddEditMedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddEditMedViewModel(application, repository, savedStateHandle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 