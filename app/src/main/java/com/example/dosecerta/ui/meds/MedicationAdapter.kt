package com.example.dosecerta.ui.meds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.databinding.ListItemMedicationBinding

class MedicationAdapter(
    private val onEditClick: (Medication) -> Unit,
    private val onDeleteClick: (Medication) -> Unit
) : ListAdapter<Medication, MedicationAdapter.MedicationViewHolder>(MedicationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationViewHolder {
        val binding = ListItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MedicationViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: MedicationViewHolder, position: Int) {
        val medication = getItem(position)
        holder.bind(medication)
    }

    class MedicationViewHolder(
        private val binding: ListItemMedicationBinding,
        private val onEditClick: (Medication) -> Unit,
        private val onDeleteClick: (Medication) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentMedication: Medication? = null

        init {
            binding.buttonEditMed.setOnClickListener {
                currentMedication?.let { onEditClick(it) }
            }
            binding.buttonDeleteMed.setOnClickListener {
                currentMedication?.let { onDeleteClick(it) }
            }
            // Optional: Make the whole item clickable for editing
            // itemView.setOnClickListener {
            //     currentMedication?.let { onEditClick(it) }
            // }
        }

        fun bind(medication: Medication) {
            currentMedication = medication
            binding.textMedicationName.text = medication.name
            // Construct details string (Example)
            val strengthPart = if (medication.strength != null && medication.strengthUnit != null) {
                "${medication.strength} ${medication.strengthUnit.name}"
            } else ""
            val dosageFormPart = medication.dosageForm.name.lowercase().replaceFirstChar { it.titlecase() }
            val details = listOfNotNull(medication.dosage, strengthPart, dosageFormPart, medication.frequency)
                .filter { it.isNotEmpty() }
                .joinToString(" - ")
            binding.textMedicationDetails.text = details
        }
    }
}

// DiffUtil helps ListAdapter determine changes efficiently
class MedicationDiffCallback : DiffUtil.ItemCallback<Medication>() {
    override fun areItemsTheSame(oldItem: Medication, newItem: Medication): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Medication, newItem: Medication): Boolean {
        return oldItem == newItem // Data class checks all fields
    }
} 