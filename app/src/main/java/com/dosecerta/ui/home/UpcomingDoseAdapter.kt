package com.dosecerta.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.databinding.ItemUpcomingBinding

/** A small, readable agenda using the same upcoming occurrences as the medication list. */
class UpcomingDoseAdapter : ListAdapter<Pair<String, Long>, UpcomingDoseAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        ViewHolder(ItemUpcomingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemUpcomingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dose: Pair<String, Long>) {
            binding.textName.text = dose.first
            binding.textDate.text = com.dosecerta.ui.UiDateTime.dateLabel(binding.root.context, dose.second)
            binding.textTime.text = com.dosecerta.ui.UiDateTime.time(binding.root.context, dose.second)
        }
    }
    private class DiffCallback : DiffUtil.ItemCallback<Pair<String, Long>>() {
        override fun areItemsTheSame(old: Pair<String, Long>, new: Pair<String, Long>) = old == new
        override fun areContentsTheSame(old: Pair<String, Long>, new: Pair<String, Long>) = old == new
    }
}
