package com.example.photoeditor.model

import androidx.annotation.DrawableRes

data class AdjustTab(
    val id: String, // "light", "color", "effects", ...
    @DrawableRes val iconRes: Int,
    val label: String,
    val sliders: List<AdjustSlider>
)
