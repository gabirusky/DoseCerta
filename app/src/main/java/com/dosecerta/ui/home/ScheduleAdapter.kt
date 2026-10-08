package com.dosecerta.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.ScheduleItem
import com.dosecerta.databinding.ItemScheduleBinding
import com.dosecerta.ui.UiLabels
import com.dosecerta.ui.stackForReadingSize

class ScheduleAdapter(private val onTakeClick: (ScheduleItem) -> Unit, private val onSkipClick: (ScheduleItem) -> Unit) :
    ListAdapter<ScheduleItem, ScheduleAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = ViewHolder(ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
    inner class ViewHolder(private val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.scheduleHeading.stackForReadingSize()
            binding.scheduleDetails.stackForReadingSize()
            binding.scheduleActions.stackForReadingSize()
        }
        fun bind(item: ScheduleItem) {
            val ctx = binding.root.context
            binding.imageMedicationIcon.setColorFilter(com.dosecerta.ui.MedicationIcon.color(ctx, item.medication.color))
            binding.textMedicationName.text = item.medication.name
            binding.textDosage.text = "${item.medication.dosage} ${item.medication.unit} · ${UiLabels.form(ctx, item.medication.pharmaceuticalForm)}"
            binding.textTime.text = com.dosecerta.ui.UiDateTime.time(ctx, item.scheduledTime)
            binding.textTimeUntil.setText(when (item.status) {
                MedicationStatus.TAKEN -> R.string.ui_taken
                MedicationStatus.SKIPPED -> R.string.ui_skipped
                MedicationStatus.MISSED -> R.string.ui_missed
                MedicationStatus.PENDING -> if (item.isPastDue) R.string.design_due_short else R.string.ui_pending
            })
            // Historical corrections are explicit in History; a closed occurrence cannot be acted on again here.
            val pending = item.status == MedicationStatus.PENDING
            binding.scheduleActions.visibility = if (pending) View.VISIBLE else View.GONE
            binding.buttonTake.visibility = if (pending) View.VISIBLE else View.GONE
            binding.buttonSnooze.visibility = if (pending) View.VISIBLE else View.GONE
            binding.buttonTake.contentDescription = ctx.getString(R.string.ui_take_for, item.medication.name)
            binding.buttonSnooze.contentDescription = ctx.getString(R.string.ui_skip_for, item.medication.name)
            binding.buttonTake.setOnClickListener { onTakeClick(item) }
            binding.buttonSnooze.setOnClickListener { onSkipClick(item) }
        }
    }
    class DiffCallback : DiffUtil.ItemCallback<ScheduleItem>() {
        override fun areItemsTheSame(a: ScheduleItem, b: ScheduleItem) = a.schedule.id == b.schedule.id && a.scheduledTime == b.scheduledTime
        override fun areContentsTheSame(a: ScheduleItem, b: ScheduleItem) = a == b
    }
}
