package com.core.adjust.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.databinding.ItemColorPillBinding
import com.core.adjust.model.ColorChannel
import com.core.adjust.model.ColorPillUi

class ColorPillAdapter(
    private val onClick: (ColorChannel) -> Unit
) : ListAdapter<ColorPillUi, ColorPillVH>(Diff) {

    object Diff : DiffUtil.ItemCallback<ColorPillUi>() {
        override fun areItemsTheSame(a: ColorPillUi, b: ColorPillUi) = a.channel == b.channel
        override fun areContentsTheSame(a: ColorPillUi, b: ColorPillUi) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorPillVH {
        val binding = ItemColorPillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorPillVH(binding)
    }

    override fun onBindViewHolder(holder: ColorPillVH, position: Int) {
        holder.bind(getItem(position), onClick)
    }
}

class ColorPillVH(private val vb: ItemColorPillBinding) : RecyclerView.ViewHolder(vb.root) {
    fun bind(item: ColorPillUi, onClick: (ColorChannel) -> Unit) = with(vb) {
        ivCircle.imageTintList = ColorStateList.valueOf(item.color)
        ivCircle.isSelected = item.selected
        root.setOnClickListener { onClick(item.channel) }
    }
}