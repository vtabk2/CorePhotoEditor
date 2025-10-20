package com.core.adjust.model

import androidx.annotation.DrawableRes

data class AdjustTab(
    val key: String,
    @DrawableRes val iconRes: Int,
    val label: String,
    val sliders: List<AdjustSlider>
)
