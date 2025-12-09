package com.dosecerta.ui.setup

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.dosecerta.R
import com.dosecerta.databinding.ItemTutorialCardBinding

/**
 * Data class representing a tutorial page.
 */
data class TutorialPage(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
)

/**
 * Adapter for ViewPager2 displaying tutorial cards.
 */
class TutorialPagerAdapter : RecyclerView.Adapter<TutorialPagerAdapter.TutorialViewHolder>() {
    
    private val pages = listOf(
        TutorialPage(
            iconRes = R.drawable.ic_tutorial_overview,
            titleRes = R.string.setup_tutorial_title_1,
            descriptionRes = R.string.setup_tutorial_desc_1
        ),
        TutorialPage(
            iconRes = R.drawable.ic_tutorial_add,
            titleRes = R.string.setup_tutorial_title_2,
            descriptionRes = R.string.setup_tutorial_desc_2
        ),
        TutorialPage(
            iconRes = R.drawable.ic_tutorial_reminder,
            titleRes = R.string.setup_tutorial_title_3,
            descriptionRes = R.string.setup_tutorial_desc_3
        )
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TutorialViewHolder {
        val binding = ItemTutorialCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TutorialViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TutorialViewHolder, position: Int) {
        holder.bind(pages[position])
    }
    
    override fun getItemCount(): Int = pages.size
    
    class TutorialViewHolder(
        private val binding: ItemTutorialCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(page: TutorialPage) {
            binding.imageIcon.setImageResource(page.iconRes)
            binding.textTitle.setText(page.titleRes)
            binding.textDescription.setText(page.descriptionRes)
        }
    }
}
