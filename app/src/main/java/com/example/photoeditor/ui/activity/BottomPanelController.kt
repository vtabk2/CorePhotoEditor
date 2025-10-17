package com.example.photoeditor.ui.activity

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.core.adjust.AdjustManager
import com.core.adjust.module.AdjustSlider
import com.core.adjust.module.AdjustTab
import com.example.photoeditor.ui.adapter.AdjustSliderAdapter
import com.example.photoeditor.ui.adapter.AdjustTabAdapter

class BottomPanelController(
    private val rcvTabs: RecyclerView,
    private val rcvSliders: RecyclerView,
    private val adjustManager: AdjustManager,
    private val onSliderChanged: (AdjustSlider) -> Unit
) {

    private val tabAdapter = AdjustTabAdapter { pos, tab ->
        currentTabIndex = pos
        // Tạo list mới từ tab
        val newList = tab.sliders.map { it.copy() }
        // Áp giá trị hiện tại từ AdjustParams vào list
        AdjustRepository.map(newList, adjustManager.params)
        // Gửi list mới vào adapter
        sliderAdapter.submitList(newList)
    }

    private val sliderAdapter = AdjustSliderAdapter { slider ->
        onSliderChanged(slider)
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
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            itemAnimator = null
        }
        tabAdapter.submitList(tabs)
        tabAdapter.selectedPos = 0
        sliderAdapter.submitList(tabs.first().sliders.map { it.copy() })
    }

    fun getCurrentTabId(): String = tabs[currentTabIndex].id
    fun getCurrentSliders(): List<AdjustSlider> = sliderAdapter.currentList
}
