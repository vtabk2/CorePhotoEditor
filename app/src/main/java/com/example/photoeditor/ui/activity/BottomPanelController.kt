package com.example.photoeditor.ui.activity

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photoeditor.model.AdjustSlider
import com.example.photoeditor.model.AdjustTab
import com.example.photoeditor.ui.adapter.AdjustSliderAdapter
import com.example.photoeditor.ui.adapter.AdjustTabAdapter

class BottomPanelController(
    private val context: Context,
    private val rcvTabs: RecyclerView,
    private val rcvSliders: RecyclerView,
    private val onSliderChanged: (AdjustSlider) -> Unit
) {

    private val tabAdapter = AdjustTabAdapter { pos, tab ->
        currentTabIndex = pos
        sliderAdapter.submitList(tab.sliders.map { it.copy() }) // copy để UI độc lập nếu cần
    }
    private val sliderAdapter = AdjustSliderAdapter { slider ->
        onSliderChanged(slider) // callback ra ngoài để apply preview
    }

    private var tabs: List<AdjustTab> = AdjustRepository.defaultTabs()
    private var currentTabIndex = 0

    fun bind() {
        rcvTabs.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = tabAdapter
        }
        rcvSliders.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sliderAdapter
        }
        tabAdapter.submitList(tabs)
        tabAdapter.selectedPos = 0
        sliderAdapter.submitList(tabs.first().sliders.map { it.copy() })
    }

    fun getCurrentTabId(): String = tabs[currentTabIndex].id
    fun getCurrentSliders(): List<AdjustSlider> = sliderAdapter.currentList
}
