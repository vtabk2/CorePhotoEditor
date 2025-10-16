package com.example.photoeditor.ui.activity

import com.example.photoeditor.R
import com.example.photoeditor.model.AdjustSlider
import com.example.photoeditor.model.AdjustTab

object AdjustRepository {

    fun defaultTabs(): List<AdjustTab> {
        return listOf(
            AdjustTab(
                id = "light",
                iconRes = R.drawable.ic_tab_light, // tạo vector đơn giản
                label = "Light",
                sliders = listOf(
                    AdjustSlider("exposure", "Exposure", -500, 500, 0), // EV *100
                    AdjustSlider("contrast", "Contrast", -100, 100, 0),
                    AdjustSlider("highlights", "Highlights", -100, 100, 0),
                    AdjustSlider("shadows", "Shadows", -100, 100, 0),
                    AdjustSlider("whites", "Whites", -100, 100, 0),
                    AdjustSlider("blacks", "Blacks", -100, 100, 0),
                )
            ),
            AdjustTab(
                id = "color",
                iconRes = R.drawable.ic_tab_color,
                label = "Color",
                sliders = listOf(
                    AdjustSlider("temperature", "Temperature", -100, 100, 0),
                    AdjustSlider("tint", "Tint", -100, 100, 0),
                    AdjustSlider("vibrance", "Vibrance", -100, 100, 0),
                    AdjustSlider("saturation", "Saturation", -100, 100, 0),
                )
            ),
            AdjustTab(
                id = "effects",
                iconRes = R.drawable.ic_tab_effects,
                label = "Effects",
                sliders = listOf(
                    AdjustSlider("texture", "Texture", -100, 100, 0),
                    AdjustSlider("clarity", "Clarity", -100, 100, 0),
                    AdjustSlider("dehaze", "Dehaze", -100, 100, 0),
                )
            )
        )
    }
}
