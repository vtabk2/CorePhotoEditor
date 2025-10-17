package com.core.adjust

data class AdjustParams(
    // Light
    var exposure: Float = 0f,
    var brightness: Float = 0f,
    var contrast: Float = 0f,
    var highlights: Float = 0f,
    var shadows: Float = 0f,
    var whites: Float = 0f,
    var blacks: Float = 0f,

    // Color
    var temperature: Float = 0f,
    var tint: Float = 0f,
    var vibrance: Float = 0f,
    var saturation: Float = 0f,

    // Effects
    var texture: Float = 0f,
    var clarity: Float = 0f,
    var dehaze: Float = 0f,
    var vignette: Float = 0f,
    var grain: Float = 0f,
)
