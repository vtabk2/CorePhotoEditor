package com.example.photoeditor.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.AdjustParams
import com.core.adjust.module.AdjustSlider
import com.example.photoeditor.R
import com.example.photoeditor.ui.activity.AdjustRepository

class AdjustSliderAdapter(
    private val onValueChanged: (slider: AdjustSlider) -> Unit
) : ListAdapter<AdjustSlider, AdjustSliderAdapter.SliderVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_adjust_slider, parent, false)
        return SliderVH(v)
    }

    override fun onBindViewHolder(holder: SliderVH, position: Int) {
        holder.bind(getItem(position), onValueChanged)
    }

    class SliderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvValue: TextView = itemView.findViewById(R.id.tvValue)
        private val seek: SeekBar = itemView.findViewById(R.id.seekBar)

        fun bind(slider: AdjustSlider, onValueChanged: (AdjustSlider) -> Unit) {
            tvName.text = slider.label
            val span = (slider.max - slider.min)
            seek.max = span
            seek.progress = slider.value - slider.min
            tvValue.text = slider.value.toString()

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    slider.value = slider.min + progress
                    tvValue.text = slider.value.toString()
                    if (fromUser) onValueChanged(slider)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    fun updateFromParams(adjustParams: AdjustParams) {
        val newList = currentList.map { it.copy() }
        AdjustRepository.map(newList, adjustParams)
        submitList(newList)
        notifyItemRangeChanged(0, newList.size)
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AdjustSlider>() {
            override fun areItemsTheSame(oldItem: AdjustSlider, newItem: AdjustSlider) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: AdjustSlider, newItem: AdjustSlider) = oldItem.value == newItem.value
        }
    }
}
