package com.dosecerta.ui.addmedication

import com.dosecerta.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.databinding.ListItemScheduleTimeBinding

/** Each slot has direct edit/remove actions; sorting never changes the action's identity. */
class ScheduleTimeAdapter(
    private val onEdit: (ScheduleTime) -> Unit,
    private val onRemove: (ScheduleTime) -> Unit
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
        return TimeViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    inner class TimeViewHolder(
        private val binding: ListItemScheduleTimeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.buttonTime.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEdit(items[position])
                }
            }
            binding.buttonRemoveTime.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onRemove(items[position])
            }
        }
        
        fun bind(scheduleTime: ScheduleTime) {
            val context = binding.root.context
            val time = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, scheduleTime.timeInMinutes / 60)
                set(java.util.Calendar.MINUTE, scheduleTime.timeInMinutes % 60)
            }
            val label = android.text.format.DateFormat.getTimeFormat(context).format(time.time)
            binding.buttonTime.text = label
            binding.buttonTime.contentDescription = context.getString(R.string.ui_edit_time, label)
            binding.buttonRemoveTime.contentDescription = context.getString(R.string.ui_remove_time, label)
        }
    }
}
