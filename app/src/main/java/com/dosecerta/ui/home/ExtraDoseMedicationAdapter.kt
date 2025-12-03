package com.dosecerta.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.databinding.ItemExtraDoseMedicationBinding

/**
 * Adapter for displaying medications in the extra dose dialog.
 */
class ExtraDoseMedicationAdapter(
    private val onAddDoseClick: (Medication) -> Unit
) : ListAdapter<Medication, ExtraDoseMedicationAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExtraDoseMedicationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onAddDoseClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemExtraDoseMedicationBinding,
        private val onAddDoseClick: (Medication) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(medication: Medication) {
            binding.textMedicationName.text = medication.name
            binding.textMedicationDosage.text = "${medication.dosage} ${medication.unit}"
            
            // Set medication color
            binding.imageMedicationIcon.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(medication.color)
            
            // Handle +1 button click
            binding.buttonAddDose.setOnClickListener {
                onAddDoseClick(medication)
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
