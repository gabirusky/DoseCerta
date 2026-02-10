package com.dosecerta.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.data.local.dao.MedicationLogWithDetails
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.databinding.ItemMedicationLogBinding
import com.dosecerta.util.DateTimeUtils

/**
 * Adapter for displaying medication logs with details.
 */
class MedicationLogAdapter(
    private val onLongClick: (View, MedicationLogWithDetails) -> Unit = { _, _ -> }
) : ListAdapter<MedicationLogWithDetails, MedicationLogAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onLongClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemMedicationLogBinding,
        private val onLongClick: (View, MedicationLogWithDetails) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var currentItem: MedicationLogWithDetails? = null
        
        init {
            binding.root.setOnLongClickListener { view ->
                currentItem?.let { onLongClick(view, it) }
                true
            }
        }
        
        fun bind(logWithDetails: MedicationLogWithDetails) {
            currentItem = logWithDetails
            val log = logWithDetails.log
            
            // Display medication name and dosage
            // For custom medications, use customMedicationName and show no dosage
            val displayName = if (log.customMedicationName != null) {
                log.customMedicationName
            } else {
                "${logWithDetails.medicationName} (${logWithDetails.dosage} ${logWithDetails.unit})"
            }
            binding.textMedicationName.text = displayName
            
            // Show actual time taken, or scheduled time if not taken yet
            val displayTime = log.actualTime ?: log.scheduledTime
            binding.textDateTime.text = DateTimeUtils.formatDateTime(displayTime)
            
            // Set medication icon color (use default color for custom medications)
            val iconColor = logWithDetails.color ?: 0xFF757575.toInt() // Default gray
            binding.imageStatusIcon.backgroundTintList = 
                android.content.res.ColorStateList.valueOf(iconColor)
            
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
    
    class DiffCallback : DiffUtil.ItemCallback<MedicationLogWithDetails>() {
        override fun areItemsTheSame(
            oldItem: MedicationLogWithDetails, 
            newItem: MedicationLogWithDetails
        ): Boolean {
            return oldItem.log.id == newItem.log.id
        }
        
        override fun areContentsTheSame(
            oldItem: MedicationLogWithDetails, 
            newItem: MedicationLogWithDetails
        ): Boolean {
            return oldItem == newItem
        }
    }
}

