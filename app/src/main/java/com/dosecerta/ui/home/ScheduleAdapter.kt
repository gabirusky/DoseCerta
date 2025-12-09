package com.dosecerta.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.ScheduleItem
import com.dosecerta.databinding.ItemScheduleBinding
import com.dosecerta.util.DateTimeUtils

/**
 * Adapter for displaying today's medication schedule.
 */
class ScheduleAdapter(
    private val onTakeClick: (ScheduleItem) -> Unit,
    private val onSkipClick: (ScheduleItem) -> Unit
) : ListAdapter<ScheduleItem, ScheduleAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onTakeClick, onSkipClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemScheduleBinding,
        private val onTakeClick: (ScheduleItem) -> Unit,
        private val onSkipClick: (ScheduleItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: ScheduleItem) {
            // Set icon color
            binding.imageMedicationIcon.setColorFilter(
                item.medication.color,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            
            binding.textMedicationName.text = item.medication.name
            binding.textDosage.text = "${item.medication.dosage} ${item.medication.unit} - " +
                    "${item.medication.pharmaceuticalForm.name.lowercase().capitalize()}"
            binding.textTime.text = DateTimeUtils.formatTimestamp(item.scheduledTime)
            binding.textTimeUntil.text = DateTimeUtils.getTimeUntilString(item.scheduledTime)
            
            // Set status-based UI
            when (item.status) {
                MedicationStatus.TAKEN -> {
                    binding.buttonTake.isEnabled = false
                    binding.buttonTake.text = "Tomado"
                    binding.buttonTake.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.mint_leaf)
                    )
                    binding.buttonSnooze.visibility = android.view.View.GONE
                }
                MedicationStatus.SKIPPED -> {
                    binding.buttonTake.isEnabled = false
                    binding.buttonTake.text = "Pulado"
                    binding.buttonTake.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.status_skipped)
                    )
                    binding.buttonSnooze.visibility = android.view.View.GONE
                }
                MedicationStatus.MISSED -> {
                    binding.buttonTake.isEnabled = true
                    binding.buttonTake.text = "Tomei"
                    binding.buttonTake.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.mint_leaf)
                    )
                    binding.buttonSnooze.visibility = android.view.View.VISIBLE
                }
                else -> {
                    binding.buttonTake.isEnabled = true
                    binding.buttonTake.text = "Tomei"
                    binding.buttonTake.setBackgroundColor(
                        ContextCompat.getColor(binding.root.context, R.color.mint_leaf)
                    )
                    binding.buttonSnooze.visibility = android.view.View.VISIBLE
                }
            }
            
            // Set click listeners
            binding.buttonTake.setOnClickListener {
                onTakeClick(item)
            }
            
            binding.buttonSnooze.setOnClickListener {
                onSkipClick(item)
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<ScheduleItem>() {
        override fun areItemsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean {
            return oldItem.medication.id == newItem.medication.id &&
                    oldItem.scheduledTime == newItem.scheduledTime
        }
        
        override fun areContentsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean {
            return oldItem == newItem
        }
    }
}
