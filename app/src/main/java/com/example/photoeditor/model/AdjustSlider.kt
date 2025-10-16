package com.example.photoeditor.model

data class AdjustSlider(
    val key: String, // "exposure", "contrast", ...
    val label: String,
    val min: Int = -100,
    val max: Int = 100,
    var value: Int = 0
)