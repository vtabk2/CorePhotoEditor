package com.core.adjust.ui.filter.child.adjust

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.databinding.FItemAdjustBinding
import com.core.adjust.model.AdjustSlider

class AdjustAdapter(
    private val onClick: (AdjustSlider, required: Boolean) -> Unit
) : ListAdapter<AdjustSlider, AdjustAdapter.AdjustViewHolder>(DiffCallback) {

    private var selectedKey: String? = null
    private var recyclerView: RecyclerView? = null

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<AdjustSlider>() {
            override fun areItemsTheSame(oldItem: AdjustSlider, newItem: AdjustSlider) = oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: AdjustSlider, newItem: AdjustSlider) = oldItem == newItem
        }
    }

    inner class AdjustViewHolder(private val binding: FItemAdjustBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AdjustSlider, isSelected: Boolean) {
            binding.imgIcon.setImageResource(item.iconRes)
            binding.imgIcon.isSelected = isSelected
            binding.tvName.text = item.label
            binding.tvName.isSelected = isSelected
            binding.dotChanged.isSelected = isSelected

            binding.dotChanged.visibility = if (item.value != 0) View.VISIBLE else View.INVISIBLE

            binding.root.setOnClickListener {
                val oldSelected = selectedKey
                selectedKey = item.key

                // Refresh item cũ và mới
                notifyItemChanged(currentList.indexOfFirst { it.key == oldSelected })
                notifyItemChanged(bindingAdapterPosition)

                // 🔹 Auto-scroll giữa màn hình (chỉ khi thực sự cần)
                recyclerView?.let { rv ->
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return@let
                    val view = lm.findViewByPosition(bindingAdapterPosition) ?: return@let

                    val parentWidth = rv.width
                    val childCenter = view.left + view.width / 2
                    val parentCenter = parentWidth / 2
                    val scrollX = childCenter - parentCenter

                    // Xác định vị trí hiện tại trong danh sách
                    val firstVisible = lm.findFirstVisibleItemPosition()
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val lastIndex = currentList.lastIndex

                    // ✅ Không scroll nếu ở đầu/cuối danh sách
                    val isAtStart = firstVisible <= 0 && scrollX < 0
                    val isAtEnd = lastVisible >= lastIndex && scrollX > 0

                    if (!isAtStart && !isAtEnd && scrollX != 0) {
                        rv.stopScroll() // ngắt animation cũ (nếu có)
                        rv.smoothScrollBy(scrollX, 0)
                    }
                }

                onClick(item, true)
            }
        }
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        recyclerView = null
        super.onDetachedFromRecyclerView(rv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdjustViewHolder {
        val binding = FItemAdjustBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdjustViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdjustViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.key == selectedKey)
    }

    /** 🔹 Cho phép chọn mặc định từ ngoài (ví dụ item đầu tiên) */
    fun setSelectedKey(key: String? = null, autoScroll: Boolean = true) {
        val old = selectedKey
        selectedKey = key
        val newPos = currentList.indexOfFirst { it.key == key }

        currentList.getOrNull(newPos)?.let {
            onClick.invoke(it, true)
        }

        notifyItemChanged(currentList.indexOfFirst { it.key == old })
        if (newPos != -1) {
            notifyItemChanged(newPos)
            if (autoScroll) recyclerView?.smoothScrollToPosition(newPos)
        }
    }

    fun updateValue(progress: Int, callback: (value: Int) -> Unit) {
        currentList.find { it.key == selectedKey }?.let {
            it.value = it.min + progress
            callback.invoke(it.value)
        }
    }

    fun applyAdjust() {
        val newPos = currentList.indexOfFirst { it.key == selectedKey }
        notifyItemChanged(newPos)
        currentList.getOrNull(newPos)?.let {
            onClick.invoke(it, false)
        }
    }

    fun resetAdjust() {
        currentList.forEach {
            it.value = 0
        }
        val newPos = currentList.indexOfFirst { it.key == selectedKey }
        currentList.getOrNull(newPos)?.let {
            onClick.invoke(it, true)
        }
        notifyDataSetChanged()
    }
}