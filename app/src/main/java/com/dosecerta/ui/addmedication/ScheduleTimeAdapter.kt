package com.dosecerta.ui.addmedication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.databinding.ListItemScheduleTimeBinding

/**
 * Adapter for schedule time list.
 */
class ScheduleTimeAdapter(
    private val onDeleteClick: (ScheduleTime) -> Unit
) : ListAdapter<ScheduleTime, ScheduleTimeAdapter.TimeViewHolder>(TimeDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val binding = ListItemScheduleTimeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimeViewHolder(binding, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class TimeViewHolder(
        private val binding: ListItemScheduleTimeBinding,
        private val onDeleteClick: (ScheduleTime) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(scheduleTime: ScheduleTime) {
            val timeInMinutes = scheduleTime.timeInMinutes
            val hours = timeInMinutes / 60
            val minutes = timeInMinutes % 60
            binding.textTime.text = String.format("%02d:%02d", hours, minutes)
            
            binding.buttonDelete.setOnClickListener {
                onDeleteClick(scheduleTime)
            }
        }
    }
    
    class TimeDiffCallback : DiffUtil.ItemCallback<ScheduleTime>() {
        override fun areItemsTheSame(oldItem: ScheduleTime, newItem: ScheduleTime): Boolean {
            // If IDs are 0 (new items), check reference or assume they are different if added separately?
            // Actually for new items, we can rely on object identity or time if unique.
            // But if user adds 8:00 twice, we want to distinguish.
            // For now, ID check is good for existing. For new, maybe time check?
            return if (oldItem.id != 0L && newItem.id != 0L) {
                oldItem.id == newItem.id
            } else {
                oldItem === newItem // Reference equality for new items? Or just time?
                // Let's use time for simplicity, assuming user won't add duplicate times.
                oldItem.timeInMinutes == newItem.timeInMinutes
            }
        }
        
        override fun areContentsTheSame(oldItem: ScheduleTime, newItem: ScheduleTime): Boolean {
            return oldItem == newItem
        }
    }
}
