package com.dosecerta.ui.addmedication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.databinding.ListItemScheduleTimeBinding

/**
 * Adapter for schedule time list.
 */
class ScheduleTimeAdapter(
    private val onDeleteClick: (Int) -> Unit
) : ListAdapter<Int, ScheduleTimeAdapter.TimeViewHolder>(TimeDiffCallback()) {
    
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
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(timeInMinutes: Int) {
            val hours = timeInMinutes / 60
            val minutes = timeInMinutes % 60
            binding.textTime.text = String.format("%02d:%02d", hours, minutes)
            
            binding.buttonDelete.setOnClickListener {
                onDeleteClick(timeInMinutes)
            }
        }
    }
    
    class TimeDiffCallback : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }
        
        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean {
            return oldItem == newItem
        }
    }
}
