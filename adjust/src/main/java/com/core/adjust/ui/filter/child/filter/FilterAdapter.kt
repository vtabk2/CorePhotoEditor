package com.core.adjust.ui.filter.child.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.databinding.ItemFilterBinding
import com.core.adjust.model.lut.LutFilter
import com.core.adjust.model.lut.LutGroup

class FilterAdapter(
    private val onGroupVisible: (Int) -> Unit,
    private val onFilterSelected: (LutFilter) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterVH>() {

    private val groups = mutableListOf<LutGroup>()
    private val items = mutableListOf<Pair<Int, LutFilter>>()
    private var selectedPos = RecyclerView.NO_POSITION

    inner class FilterVH(val binding: ItemFilterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(filter: LutFilter, isSelected: Boolean) {
            binding.tvFilter.text = filter.name
            binding.tvFilter.isSelected = isSelected

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
        val (_, filter) = items[position]
        holder.bind(filter, position == selectedPos)
    }

    override fun getItemCount() = items.size

    fun getPositionForGroup(groupIndex: Int): Int {
        return items.indexOfFirst { it.first == groupIndex }.coerceAtLeast(0)
    }

    fun attachScrollSync(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val first = lm.findFirstVisibleItemPosition()
                if (first != RecyclerView.NO_POSITION) {
                    val gIndex = items[first].first
                    onGroupVisible(gIndex)
                }
            }
        })
    }

    fun submitList(list: MutableList<LutGroup>) {
        groups.clear()
        groups.addAll(list)

        items.clear()
        groups.forEachIndexed { i, g ->
            g.filters.forEach { f -> items.add(i to f) }
        }

        notifyDataSetChanged()
    }

    fun setSelectedByName(name: String) {
        val index = items.indexOfFirst { it.second.name == name }
        if (index != -1) {
            val old = selectedPos
            selectedPos = index
            notifyItemChanged(old)
            notifyItemChanged(selectedPos)
        }
    }
}
