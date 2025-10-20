package com.core.adjust.model

data class AdjustSlider(
    val key: String,
    val label: String,
    val min: Int = -100,
    val max: Int = 100,
    var value: Int = 0
)