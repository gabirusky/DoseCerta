package com.dosecerta.ui.medications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.model.Frequency
import com.dosecerta.databinding.ItemMedicationBinding
import com.dosecerta.ui.UiLabels

class MedicationAdapter(private val onEditClick: (Medication) -> Unit, private val onDeleteClick: (Medication) -> Unit) :
    ListAdapter<Medication, MedicationAdapter.ViewHolder>(DiffCallback()) {
    private var upcoming: Map<Long, Long?> = emptyMap()
    fun updateUpcoming(value: Map<Long, Long?>) { if (upcoming != value) { upcoming = value; notifyItemRangeChanged(0, itemCount) } }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = ViewHolder(ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
    inner class ViewHolder(private val binding: ItemMedicationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(medication: Medication) {
            val context = binding.root.context
            binding.imageMedicationIcon.setColorFilter(com.dosecerta.ui.MedicationIcon.color(context, medication.color))
            binding.textMedicationName.text = medication.name
            binding.textDosageInfo.text = "${medication.dosage} ${medication.unit} · ${UiLabels.form(context, medication.pharmaceuticalForm)}"
            val frequency = if (medication.frequency == Frequency.DAILY) context.getString(R.string.medications_filter_daily)
                else UiLabels.frequency(context, medication.frequency)
            val next = if (medication.frequency == Frequency.AS_NEEDED) context.getString(R.string.design_no_reminders)
                else upcoming[medication.id]?.let { context.getString(R.string.design_next_dose, com.dosecerta.ui.UiDateTime.upcoming(context, it)) }
                    ?: context.getString(R.string.ui_upcoming_empty)
            binding.textNextDose.text = context.getString(R.string.ui_next_dose_summary, frequency, next)
            binding.buttonDelete.contentDescription = context.getString(R.string.ui_manage_medication, medication.name)
            binding.root.setOnClickListener { onEditClick(medication) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(medication) }
        }
    }
    class DiffCallback : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(a: Medication, b: Medication) = a.id == b.id
        override fun areContentsTheSame(a: Medication, b: Medication) = a == b
    }
}
