package com.dosecerta.ui.addmedication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.databinding.ListItemScheduleTimeBinding

/**
 * Adapter for schedule time list.
 */
class ScheduleTimeAdapter(
    private val onDeleteClick: (ScheduleTime) -> Unit
) : RecyclerView.Adapter<ScheduleTimeAdapter.TimeViewHolder>() {
    
    private var items: List<ScheduleTime> = emptyList()
    
    fun submitList(newItems: List<ScheduleTime>) {
        items = newItems.toList()
        notifyDataSetChanged()
    }
    
    override fun getItemCount(): Int = items.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val binding = ListItemScheduleTimeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimeViewHolder(binding, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        holder.bind(items[position])
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
}
