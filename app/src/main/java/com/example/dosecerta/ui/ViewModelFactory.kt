package com.example.dosecerta.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.dosecerta.DoseCertaApplication
import com.example.dosecerta.data.repository.MedicationRepository
import com.example.dosecerta.ui.history.HistoryViewModel
import com.example.dosecerta.ui.home.HomeViewModel
import com.example.dosecerta.ui.meds.MedsViewModel

/**
 * General ViewModel Factory using CreationExtras.
 * Handles ViewModels requiring MedicationRepository and potentially Application.
 */
object ViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val repository = (application as DoseCertaApplication).repository

        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(repository) as T
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(repository) as T
            }
            modelClass.isAssignableFrom(MedsViewModel::class.java) -> {
                // MedsViewModel now needs Application
                MedsViewModel(application, repository) as T
            }
            // Add checks for other ViewModels here
            else -> throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
} 