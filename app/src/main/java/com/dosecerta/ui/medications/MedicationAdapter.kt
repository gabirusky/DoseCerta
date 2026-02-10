package com.dosecerta.ui.medications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.databinding.ItemMedicationBinding

/**
 * Adapter for displaying medications list.
 */
class MedicationAdapter(
    private val onEditClick: (Medication) -> Unit,
    private val onDeleteClick: (Medication) -> Unit
) : ListAdapter<Medication, MedicationAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onEditClick, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemMedicationBinding,
        private val onEditClick: (Medication) -> Unit,
        private val onDeleteClick: (Medication) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(medication: Medication) {
            // Set icon color
            binding.imageMedicationIcon.setColorFilter(
                medication.color,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            
            binding.textMedicationName.text = medication.name
            binding.textDosageInfo.text = "${medication.dosage} ${medication.unit} - " +
                    getFormString(medication.pharmaceuticalForm.name)
            
            // Frequency badge
            binding.textFrequency.text = when (medication.frequency) {
                com.dosecerta.data.model.Frequency.DAILY -> "Diário"
                com.dosecerta.data.model.Frequency.EVERY_4_HOURS -> "4 em 4h"
                com.dosecerta.data.model.Frequency.EVERY_6_HOURS -> "6 em 6h"
                com.dosecerta.data.model.Frequency.EVERY_8_HOURS -> "8 em 8h"
                com.dosecerta.data.model.Frequency.EVERY_12_HOURS -> "12 em 12h"
                com.dosecerta.data.model.Frequency.AS_NEEDED -> "S/N"
            }
            
            // Click listeners
            binding.buttonEdit.setOnClickListener {
                onEditClick(medication)
            }
            
            binding.buttonDelete.setOnClickListener {
                onDeleteClick(medication)
            }
        }
        
        private fun getFormString(form: String): String {
            return when (form) {
                "TABLET" -> "Comprimido"
                "CAPSULE" -> "Cápsula"
                "SYRUP" -> "Xarope"
                "DROPS" -> "Gotas"
                "INJECTION" -> "Injetável"
                "CREAM" -> "Pomada"
                "SPRAY" -> "Spray"
                else -> "Outro"
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(oldItem: Medication, newItem: Medication): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Medication, newItem: Medication): Boolean {
            return oldItem == newItem
        }
    }
}
