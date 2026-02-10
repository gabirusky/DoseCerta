package com.dosecerta.ui.addmedication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.databinding.ListItemScheduleTimeBinding

/**
 * Adapter for schedule time list with selection support.
 * Times are displayed as smaller pill buttons in a grid layout.
 */
class ScheduleTimeAdapter(
    private val onSelectionChanged: (ScheduleTime?) -> Unit
) : RecyclerView.Adapter<ScheduleTimeAdapter.TimeViewHolder>() {
    
    private var items: List<ScheduleTime> = emptyList()
    private var selectedPosition: Int = -1
    
    fun submitList(newItems: List<ScheduleTime>) {
        items = newItems.toList()
        // Clear selection if selected item was removed
        if (selectedPosition >= items.size) {
            selectedPosition = -1
            onSelectionChanged(null)
        }
        notifyDataSetChanged()
    }
    
    fun getSelectedItem(): ScheduleTime? {
        return if (selectedPosition in items.indices) items[selectedPosition] else null
    }
    
    fun clearSelection() {
        val previousPosition = selectedPosition
        selectedPosition = -1
        if (previousPosition in items.indices) {
            notifyItemChanged(previousPosition)
        }
        onSelectionChanged(null)
    }
    
    override fun getItemCount(): Int = items.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val binding = ListItemScheduleTimeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimeViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }
    
    inner class TimeViewHolder(
        private val binding: ListItemScheduleTimeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.buttonTime.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousPosition = selectedPosition
                    
                    // Toggle selection
                    selectedPosition = if (selectedPosition == position) -1 else position
                    
                    // Update UI
                    if (previousPosition in items.indices) {
                        notifyItemChanged(previousPosition)
                    }
                    if (selectedPosition in items.indices) {
                        notifyItemChanged(selectedPosition)
                    }
                    
                    // Notify listener
                    onSelectionChanged(getSelectedItem())
                }
            }
        }
        
        fun bind(scheduleTime: ScheduleTime, isSelected: Boolean) {
            val timeInMinutes = scheduleTime.timeInMinutes
            val hours = timeInMinutes / 60
            val minutes = timeInMinutes % 60
            binding.buttonTime.text = String.format("%02d:%02d", hours, minutes)
            binding.buttonTime.isChecked = isSelected
        }
    }
}
