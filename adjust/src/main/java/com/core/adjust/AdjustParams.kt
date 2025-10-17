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
    val activeMask: Long = 0L
) {
    companion object {
        fun buildMask(p: AdjustParams, eps: Float = 1e-6f): Long {
            fun nz(v: Float) = kotlin.math.abs(v) > eps
            var m = 0L
            if (nz(p.exposure) || nz(p.brightness) || nz(p.contrast) ||
                nz(p.highlights) || nz(p.shadows) || nz(p.whites) || nz(p.blacks)
            ) {
                m = m or AdjustMask.LIGHT
            }
            if (nz(p.temperature) || nz(p.tint) || nz(p.saturation) || nz(p.vibrance)) m = m or AdjustMask.COLOR
            if (nz(p.texture) || nz(p.clarity) || nz(p.dehaze)) m = m or AdjustMask.DETAIL
            if (nz(p.vignette)) m = m or AdjustMask.VIGNETTE
            if (nz(p.grain)) m = m or AdjustMask.GRAIN
            return m
        }
    }
}
