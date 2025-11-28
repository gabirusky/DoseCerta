package com.dosecerta.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Medications screen.
 */
class MedicationsViewModel(private val repository: MedicationRepository) : ViewModel() {
    
    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Selected filter
    private val _selectedFilter = MutableStateFlow<Frequency?>(null)
    val selectedFilter: StateFlow<Frequency?> = _selectedFilter.asStateFlow()
    
    // All medications
    private val allMedications = repository.getAllActiveMedications()
    
    // Filtered medications based on search and filter
    val medications: StateFlow<List<Medication>> = combine(
        allMedications,
        searchQuery,
        selectedFilter
    ) { meds, query, filter ->
        var filtered = meds
        
        // Apply search filter
        if (query.isNotBlank()) {
            filtered = filtered.filter { 
                it.name.contains(query, ignoreCase = true)
            }
        }
        
        // Apply frequency filter
        if (filter != null) {
            filtered = filtered.filter { it.frequency == filter }
        }
        
        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun updateFilter(filter: Frequency?) {
        _selectedFilter.value = filter
    }
    
    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            repository.deleteMedication(medication)
        }
    }
}
