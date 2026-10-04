package com.dosecerta.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.ui.stackForReadingSize
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
            binding.logDetails.stackForReadingSize()
            binding.root.setOnClickListener { view -> currentItem?.let { onLongClick(view, it) } }
            binding.root.isFocusable = true
            binding.root.contentDescription = binding.root.context.getString(R.string.report_edit_record)
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
                listOfNotNull(logWithDetails.medicationName, listOfNotNull(logWithDetails.dosage, logWithDetails.unit).joinToString(" ").takeIf { it.isNotBlank() }).joinToString(" • ")
            }
            binding.textMedicationName.text = displayName
            
            // Show actual time taken, or scheduled time if not taken yet
            val displayTime = if (log.status == MedicationStatus.TAKEN) log.actualTime ?: log.originalDueAt else log.originalDueAt
            binding.textDateTime.text = binding.root.context.getString(
                if (log.status == MedicationStatus.TAKEN) R.string.report_actual_time else R.string.report_scheduled_time,
                DateTimeUtils.formatDateTime(displayTime, binding.root.resources.configuration.locales[0]))
            
            // Set medication icon color (use default color for custom medications)
            val iconColor = logWithDetails.color ?: 0xFF757575.toInt() // Default gray
            binding.imageStatusIcon.setColorFilter(com.dosecerta.ui.MedicationIcon.color(binding.root.context, iconColor))
            binding.textStatus.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, when (log.status) {
                MedicationStatus.TAKEN -> R.color.ui_success
                MedicationStatus.MISSED -> R.color.ui_error
                MedicationStatus.SKIPPED -> R.color.ui_warning
                else -> R.color.ui_on_secondary_container
            }))
            
            // Status badge
            when (log.status) {
                MedicationStatus.TAKEN -> {
                    binding.textStatus.setText(R.string.history_taken)
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_taken)
                }
                MedicationStatus.MISSED -> {
                    binding.textStatus.setText(R.string.history_missed)
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_missed)
                }
                MedicationStatus.SKIPPED -> {
                    binding.textStatus.setText(R.string.history_skipped)
                    binding.textStatus.setBackgroundResource(R.drawable.status_badge_skipped)
                }
                else -> {
                    binding.textStatus.setText(R.string.report_status_pending)
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
