package com.example.dosecerta.ui.history

import android.content.Context
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.R
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.databinding.ListItemLogBinding
import java.text.SimpleDateFormat
import java.util.*

// Listener Interface for options menu clicks
interface OnLogOptionsClickListener {
    fun onMarkTakenClick(log: MedicationLog)
    fun onMarkSkippedClick(log: MedicationLog)
    fun onMarkMissedClick(log: MedicationLog)
    fun onRemoveLogClick(log: MedicationLog)
}

class MedicationLogAdapter(
    private val listener: OnLogOptionsClickListener // Add listener to constructor
) : ListAdapter<MedicationLog, MedicationLogAdapter.LogViewHolder>(LogDiffCallback()) {

    // Formatters for date and time display
    private val dateTimeFormatter = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ListItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Pass listener to ViewHolder
        return LogViewHolder(binding, parent.context, dateTimeFormatter, listener)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = getItem(position)
        holder.bind(log)
    }

    class LogViewHolder(
        private val binding: ListItemLogBinding,
        private val context: Context,
        private val formatter: SimpleDateFormat,
        private val listener: OnLogOptionsClickListener // Receive listener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(log: MedicationLog) {
            binding.textLogMedName.text = log.medicationName
            binding.textLogDosage.text = log.dosage
            binding.textLogTime.text = formatter.format(log.logTimestamp)

            // Set status text and color
            val statusText: String
            val statusColorRes: Int
            val indicatorColorRes: Int

            when (log.status) {
                LogStatus.TAKEN -> {
                    statusText = context.getString(R.string.status_taken)
                    statusColorRes = R.color.status_taken_on_container
                    indicatorColorRes = R.color.status_taken_on_container
                }
                LogStatus.MISSED -> {
                    statusText = context.getString(R.string.status_missed)
                    statusColorRes = R.color.status_missed_on_container
                    indicatorColorRes = R.color.status_missed_on_container
                }
                LogStatus.SKIPPED -> {
                    statusText = context.getString(R.string.status_skipped)
                    statusColorRes = R.color.status_skipped_on_container
                    indicatorColorRes = R.color.status_skipped_on_container
                }
            }

            binding.textLogStatus.text = statusText
            binding.textLogStatus.setTextColor(ContextCompat.getColor(context, statusColorRes))
            binding.imageLogStatus.setColorFilter(ContextCompat.getColor(context, indicatorColorRes))

            // Set click listener on the entire item view
            itemView.setOnClickListener { view ->
                showPopupMenu(view, log)
            }
        }

        private fun showPopupMenu(anchorView: View, log: MedicationLog) {
            val popup = PopupMenu(context, anchorView)
            popup.menuInflater.inflate(R.menu.menu_log_options, popup.menu)

            // Hide options that match the current status
            when (log.status) {
                LogStatus.TAKEN -> popup.menu.findItem(R.id.action_mark_taken)?.isVisible = false
                LogStatus.SKIPPED -> popup.menu.findItem(R.id.action_mark_skipped)?.isVisible = false
                LogStatus.MISSED -> popup.menu.findItem(R.id.action_mark_missed)?.isVisible = false
            }

            popup.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_mark_taken -> {
                        listener.onMarkTakenClick(log)
                        true
                    }
                    R.id.action_mark_skipped -> {
                        listener.onMarkSkippedClick(log)
                        true
                    }
                    R.id.action_mark_missed -> {
                        listener.onMarkMissedClick(log)
                        true
                    }
                    R.id.action_remove_log -> {
                        listener.onRemoveLogClick(log)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}

class LogDiffCallback : DiffUtil.ItemCallback<MedicationLog>() {
    override fun areItemsTheSame(oldItem: MedicationLog, newItem: MedicationLog): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MedicationLog, newItem: MedicationLog): Boolean {
        // Compare relevant fields, not just object identity if data can change
        return oldItem.status == newItem.status &&
               oldItem.logTimestamp == newItem.logTimestamp &&
               oldItem.medicationName == newItem.medicationName // Add other fields if necessary
    }
}