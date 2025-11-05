package com.core.adjust.ui.filter.child.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.databinding.ItemFilterCategoryBinding
import com.core.adjust.model.lut.LutGroup

class FilterCategoryAdapter(
    private val onCategorySelected: (group: LutGroup) -> Unit,
    private val callbackScroll: (position: Int) -> Unit
) : RecyclerView.Adapter<FilterCategoryAdapter.CategoryVH>() {
    private val filterCategoryList = mutableListOf<LutGroup>()

    private var selectedPos = 0

    inner class CategoryVH(val binding: ItemFilterCategoryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: LutGroup, isSelected: Boolean) {
            binding.tvCategory.text = group.name
            binding.tvCategory.isSelected = isSelected

            binding.root.setOnClickListener {
                val old = selectedPos
                selectedPos = bindingAdapterPosition
                notifyItemChanged(old)
                notifyItemChanged(selectedPos)
                onCategorySelected(group)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryVH {
        val binding = ItemFilterCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryVH(binding)
    }

    override fun onBindViewHolder(holder: CategoryVH, position: Int) {
        holder.bind(filterCategoryList[position], position == selectedPos)
    }

    override fun getItemCount() = filterCategoryList.size

    fun setSelected(index: Int) {
        if (index == selectedPos) return
        val old = selectedPos
        selectedPos = index
        notifyItemChanged(old)
        notifyItemChanged(selectedPos)
    }

    fun submitList(list: MutableList<LutGroup>) {
        filterCategoryList.clear()
        filterCategoryList.addAll(list)
        notifyDataSetChanged()
    }

    fun checkScroll(position: Int) {
        var lastIndex = 0
        filterCategoryList.map { it.index }.let { indexList ->
            indexList.findLast {
                position >= it
            }?.let {
                lastIndex = indexList.indexOf(it)
            }
        }

        setSelected(lastIndex)
        callbackScroll.invoke(lastIndex)
    }
}