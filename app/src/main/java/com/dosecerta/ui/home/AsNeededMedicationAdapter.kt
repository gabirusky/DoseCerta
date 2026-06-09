package com.dosecerta.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.databinding.ItemAsNeededBinding

/**
 * Adapter for displaying AS_NEEDED medications on the home screen.
 * Each card shows a prominent "Tomei" button the user can tap unlimited times.
 */
class AsNeededMedicationAdapter(
    private val onTakeClick: (Medication) -> Unit
) : ListAdapter<Medication, AsNeededMedicationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAsNeededBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onTakeClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAsNeededBinding,
        private val onTakeClick: (Medication) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(medication: Medication) {
            binding.textMedicationName.text = medication.name
            binding.textMedicationDosage.text = "${medication.dosage} ${medication.unit}"

            // Apply medication color to the pill icon
            binding.imageMedicationIcon.setColorFilter(
                medication.color,
                android.graphics.PorterDuff.Mode.SRC_IN
            )

            // Tapping the card or the button records a TAKEN dose
            binding.buttonTakeAsNeeded.setOnClickListener {
                onTakeClick(medication)
            }
            binding.root.setOnClickListener {
                onTakeClick(medication)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Medication>() {
        override fun areItemsTheSame(oldItem: Medication, newItem: Medication): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Medication, newItem: Medication): Boolean {
            return oldItem == newItem
        }
    }
}
