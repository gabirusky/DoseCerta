package com.example.dosecerta.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.R
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.model.Reminder
import com.example.dosecerta.databinding.ListItemScheduleBinding
import java.text.SimpleDateFormat
import java.util.*

// Removed data class ScheduleItem from here, assumed moved to its own file

class ScheduleAdapter(
    private val onTakeClick: (ScheduleItem) -> Unit,
    private val onSkipClick: (ScheduleItem) -> Unit,
    private val onUndoSkipClick: (ScheduleItem) -> Unit
) : ListAdapter<ScheduleItem, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val binding = ListItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScheduleViewHolder(binding, timeFormatter, onTakeClick, onSkipClick, onUndoSkipClick)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ScheduleViewHolder(
        private val binding: ListItemScheduleBinding,
        private val formatter: SimpleDateFormat,
        private val onTakeClick: (ScheduleItem) -> Unit,
        private val onSkipClick: (ScheduleItem) -> Unit,
        private val onUndoSkipClick: (ScheduleItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var currentItem: ScheduleItem? = null
        
        init {
             // Take button handles both initial take and marking skipped as taken
             binding.buttonScheduleTake.setOnClickListener {
                 currentItem?.let { onTakeClick(it) } 
             }
             // Skip button is only for initial skip
             binding.buttonScheduleSkip.setOnClickListener {
                 currentItem?.let { onSkipClick(it) }
             }
             // Add listener for Undo button
             binding.buttonScheduleUndoSkip.setOnClickListener {
                 currentItem?.let { onUndoSkipClick(it) }
             }
        }

        fun bind(item: ScheduleItem) {
            currentItem = item
            binding.textScheduleMedName.text = item.medicationName
            binding.textScheduleDosage.text = item.dosage

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, item.hour)
                set(Calendar.MINUTE, item.minute)
            }
            binding.textScheduleTime.text = formatter.format(calendar.time)
            
            // Default visibility states
            binding.buttonScheduleUndoSkip.visibility = View.GONE
            binding.groupPendingActions.visibility = View.GONE
            binding.buttonScheduleTake.visibility = View.GONE
            binding.buttonScheduleSkip.visibility = View.GONE

            // Update UI based on status
            when (item.status) {
                LogStatus.SKIPPED -> {
                    binding.buttonScheduleUndoSkip.visibility = View.VISIBLE // Show Undo button
                    // Ensure others are hidden (Take/Skip group)
                    binding.groupPendingActions.visibility = View.GONE 
                }
                LogStatus.TAKEN -> {
                    // Show appropriate state for TAKEN
                    binding.buttonScheduleUndoSkip.visibility = View.GONE // Hide Undo
                    binding.groupPendingActions.visibility = View.GONE // Hide Skip group
                     
                     // Only show "Take Again" for AS_NEEDED
                     if(item.frequencyType == FrequencyType.AS_NEEDED) {
                         binding.buttonScheduleTake.visibility = View.VISIBLE
                         binding.buttonScheduleTake.text = "Take Again"
                     } else {
                         binding.buttonScheduleTake.visibility = View.INVISIBLE // Or GONE
                     }
                }
                else -> { // null (Pending)
                    binding.root.visibility = View.VISIBLE // Ensure visible if reused
                    binding.root.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    binding.buttonScheduleUndoSkip.visibility = View.GONE // Hide Undo button
                    binding.groupPendingActions.visibility = View.VISIBLE // Show Take/Skip group
                    binding.buttonScheduleTake.visibility = View.VISIBLE 
                    binding.buttonScheduleSkip.visibility = View.VISIBLE
                    binding.buttonScheduleTake.text = "Take" 
                }
            }
        }
    }
}

class ScheduleDiffCallback : DiffUtil.ItemCallback<ScheduleItem>() {
    override fun areItemsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean {
        return oldItem.reminderId == newItem.reminderId 
    }

    override fun areContentsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean {
        return oldItem == newItem
    }
} 