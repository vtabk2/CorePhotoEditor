package com.core.adjust.ui.filter.child.filter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.databinding.ItemFilterBinding
import com.core.adjust.model.lut.LutFilter
import com.core.adjust.utils.loadLutThumb

class FilterAdapter(
    private val context: Context,
    private val onFilterSelected: (LutFilter) -> Unit,
    private val callbackScroll: (index: Int) -> Unit,
) : RecyclerView.Adapter<FilterAdapter.FilterVH>() {

    private val filterList = mutableListOf<LutFilter>()
    private var selectedPos = RecyclerView.NO_POSITION

    inner class FilterVH(val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filter: LutFilter, isSelected: Boolean) {
            binding.tvFilter.text = filter.name
            binding.tvFilter.isSelected = isSelected

            binding.imageFilter.loadLutThumb(context, filter.thumbPath)

            binding.root.setOnClickListener {
                val old = selectedPos
                selectedPos = bindingAdapterPosition
                notifyItemChanged(old)
                notifyItemChanged(selectedPos)
                onFilterSelected(filter)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterVH {
        val binding = ItemFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilterVH(binding)
    }

    override fun onBindViewHolder(holder: FilterVH, position: Int) {
        val filter = filterList[position]
        holder.bind(filter, position == selectedPos)
    }

    override fun getItemCount() = filterList.size

    fun submitList(list: MutableList<LutFilter>) {
        filterList.clear()
        filterList.addAll(list)

        notifyDataSetChanged()
    }

    fun unSelectedAll() {
        selectedPos = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }
}
