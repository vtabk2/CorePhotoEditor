package com.core.adjust

data class AdjustParams(
    var exposure: Float = 0f,
    var brightness: Float = 0f,
    var contrast: Float = 0f,
    var highlights: Float = 0f,
    var shadows: Float = 0f,
    var whites: Float = 0f,
    var blacks: Float = 0f,
    //
    var temperature: Float = 0f,
    var tint: Float = 0f,

    var saturation: Float = 0f,
)
