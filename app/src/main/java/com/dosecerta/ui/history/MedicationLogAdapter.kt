package com.dosecerta.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.databinding.ItemMedicationLogBinding
import com.dosecerta.util.DateTimeUtils

/**
 * Adapter for displaying medication logs.
 */
class MedicationLogAdapter : ListAdapter<MedicationLog, MedicationLogAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemMedicationLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(log: MedicationLog) {
            // TODO: Load medication name from repository
            binding.textMedicationName.text = "Medication #${log.medicationId}"
            binding.textDateTime.text = DateTimeUtils.formatDateTime(log.scheduledTime)
            
            // Status badge
            when (log.status) {
                MedicationStatus.TAKEN -> {
                    binding.textStatus.text = "TOMADO"
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_taken)
                }
                MedicationStatus.MISSED -> {
                    binding.textStatus.text = "PERDIDO"
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_missed)
                }
                MedicationStatus.SKIPPED -> {
                    binding.textStatus.text = "PULADO"
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_skipped)
                }
                else -> {
                    binding.textStatus.text = "PENDENTE"
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_pending)
                }
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<MedicationLog>() {
        override fun areItemsTheSame(oldItem: MedicationLog, newItem: MedicationLog): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: MedicationLog, newItem: MedicationLog): Boolean {
            return oldItem == newItem
        }
    }
}
