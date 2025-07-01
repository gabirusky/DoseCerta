package com.example.dosecerta.ui.add_edit_med

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.databinding.ListItemReminderBinding
import java.text.SimpleDateFormat
import java.util.*

// Simple data class to hold reminder time for the adapter
data class ReminderItem(val hour: Int, val minute: Int)

class ReminderAdapter(
    private val onDeleteClick: (ReminderItem) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private val reminders = mutableListOf<ReminderItem>()
    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Format time like 09:00 AM

    fun submitList(newReminders: List<ReminderItem>) {
        reminders.clear()
        reminders.addAll(newReminders.sortedWith(compareBy({ it.hour }, { it.minute })))
        notifyDataSetChanged() // Use DiffUtil for larger lists if needed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ListItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position], timeFormatter)
    }

    override fun getItemCount(): Int = reminders.size

    class ReminderViewHolder(
        private val binding: ListItemReminderBinding,
        private val onDeleteClick: (ReminderItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentReminder: ReminderItem? = null

        init {
            binding.buttonDeleteReminder.setOnClickListener {
                currentReminder?.let { onDeleteClick(it) }
            }
        }

        fun bind(reminder: ReminderItem, formatter: SimpleDateFormat) {
            currentReminder = reminder
            // Format the time for display
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
            }
            binding.textReminderTime.text = formatter.format(calendar.time)
        }
    }
} 