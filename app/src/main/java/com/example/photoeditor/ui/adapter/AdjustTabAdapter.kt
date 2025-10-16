package com.example.photoeditor.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.photoeditor.R
import com.example.photoeditor.model.AdjustTab

class AdjustTabAdapter(
    private val onClick: (position: Int, tab: AdjustTab) -> Unit
) : ListAdapter<AdjustTab, AdjustTabAdapter.TabVH>(DIFF) {

    var selectedPos = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_adjust_tab, parent, false)
        return TabVH(v)
    }

    override fun onBindViewHolder(holder: TabVH, position: Int) {
        holder.bind(getItem(position), position == selectedPos) {
            selectedPos = holder.bindingAdapterPosition
            onClick(selectedPos, getItem(selectedPos))
        }
    }

    class TabVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imv: ImageView = itemView.findViewById(R.id.imvIcon)
        private val tv: TextView = itemView.findViewById(R.id.tvLabel)
        private val indicator: View = itemView.findViewById(R.id.viewIndicator)

        fun bind(tab: AdjustTab, selected: Boolean, onClick: () -> Unit) {
            imv.setImageResource(tab.iconRes)
            tv.text = tab.label
            val color = if (selected) Color.WHITE else "#DDDDDD".toColorInt()
            imv.setColorFilter(color)
            tv.setTextColor(if (selected) Color.WHITE else "#BEBEBE".toColorInt())
            indicator.setBackgroundColor(if (selected) Color.WHITE else Color.TRANSPARENT)
            itemView.setOnClickListener { onClick() }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AdjustTab>() {
            override fun areItemsTheSame(oldItem: AdjustTab, newItem: AdjustTab) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: AdjustTab, newItem: AdjustTab) = oldItem == newItem
        }
    }
}
