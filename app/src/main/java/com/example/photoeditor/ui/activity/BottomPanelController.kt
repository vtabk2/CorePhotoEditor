package com.example.photoeditor.ui.activity

import android.widget.Button
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.core.adjust.AdjustManager
import com.core.adjust.AdjustProcessor
import com.core.adjust.model.AdjustSlider
import com.core.adjust.model.AdjustTab
import com.example.photoeditor.ui.adapter.AdjustSliderAdapter
import com.example.photoeditor.ui.adapter.AdjustTabAdapter

class BottomPanelController(
    private val rcvTabs: RecyclerView,
    private val rcvSliders: RecyclerView,
    private val btnReset: Button,
    private val adjustManager: AdjustManager,
    private val onSliderChanged: (AdjustSlider) -> Unit,
    private val onResetTab: (tabKey: String) -> Unit,
    private val onShowHsl: () -> Unit,
    private val onHideHsl: () -> Unit,
) {

    private val tabAdapter = AdjustTabAdapter { pos, tab ->
        currentTabIndex = pos

        if (tab.key == "hsl") {
            // Ẩn slider-list, hiển thị UI Color Mixer (View/Fragment) ở host
            rcvSliders.isVisible = false
            onShowHsl.invoke()
            return@AdjustTabAdapter
        } else {
            // Tab thường: show slider-list & hide HSL UI nếu đang mở
            onHideHsl.invoke()
            rcvSliders.isVisible = true

            // Tạo list mới từ tab
            val newList = tab.sliders.map { it.copy() }
            // Áp giá trị hiện tại từ AdjustParams vào list
            AdjustRepository.map(newList, adjustManager.params)
            // Gửi list mới vào adapter
            sliderAdapter.submitList(newList)
        }
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

        btnReset.setOnClickListener {
            AdjustProcessor.clearCache()
            onResetCurrentTab()
        }
    }

    private fun onResetCurrentTab() {
        val currentTab = tabs.getOrNull(currentTabIndex) ?: return

        when (currentTab.key) {
            "light" -> adjustManager.resetLight()
            "color" -> adjustManager.resetColor()
            "effects" -> adjustManager.resetEffects()
            "hsl" -> {
                adjustManager.resetHsl() // -> set mảng hslHue/Sat/Lum về 0, rebuild mask
                onResetTab.invoke("hsl") // để host bảo HSL UI reset về (0,0,0)
                return // không cần update sliderAdapter
            }
        }

        sliderAdapter.updateFromParams(adjustParams = adjustManager.params)
        onResetTab.invoke(currentTab.key)
    }
}
