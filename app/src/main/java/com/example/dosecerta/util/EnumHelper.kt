package com.example.dosecerta.util

import android.content.Context
import com.example.dosecerta.R
import com.example.dosecerta.data.model.DosageForm
import com.example.dosecerta.data.model.FrequencyType

object EnumHelper {
    
    fun formatDosageForm(context: Context, dosageForm: DosageForm): String {
        return when (dosageForm) {
            DosageForm.TABLET -> context.getString(R.string.dosage_form_tablet)
            DosageForm.CAPSULE -> context.getString(R.string.dosage_form_capsule)
            DosageForm.LIQUID -> context.getString(R.string.dosage_form_liquid)
            DosageForm.INJECTION -> context.getString(R.string.dosage_form_injection)
            DosageForm.CREAM -> context.getString(R.string.dosage_form_cream)
            DosageForm.OINTMENT -> context.getString(R.string.dosage_form_ointment)
            DosageForm.DROPS -> context.getString(R.string.dosage_form_drops)
            DosageForm.SPRAY -> context.getString(R.string.dosage_form_spray)
            DosageForm.OTHER -> context.getString(R.string.dosage_form_other)
        }
    }
    
    fun formatFrequencyType(context: Context, frequencyType: FrequencyType): String {
        return when (frequencyType) {
            FrequencyType.DAILY -> context.getString(R.string.frequency_daily)
            FrequencyType.EVERY_X_DAYS -> context.getString(R.string.frequency_every_x_days)
            FrequencyType.SPECIFIC_DAYS_OF_WEEK -> context.getString(R.string.frequency_specific_days)
            FrequencyType.AS_NEEDED -> context.getString(R.string.frequency_as_needed)
        }
    }
    
    fun getAllDosageFormsDisplayNames(context: Context): List<String> {
        return DosageForm.entries.map { formatDosageForm(context, it) }
    }
    
    fun getAllFrequencyTypesDisplayNames(context: Context): List<String> {
        return FrequencyType.entries.map { formatFrequencyType(context, it) }
    }
    
    fun getDosageFormByDisplayName(context: Context, displayName: String): DosageForm? {
        return DosageForm.entries.find { formatDosageForm(context, it) == displayName }
    }
    
    fun getFrequencyTypeByDisplayName(context: Context, displayName: String): FrequencyType? {
        return FrequencyType.entries.find { formatFrequencyType(context, it) == displayName }
    }
}