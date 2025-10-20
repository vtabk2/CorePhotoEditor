package com.core.adjust

object AdjustMask {
    const val LIGHT = 1L shl 0   // exposure..blacks
    const val COLOR = 1L shl 1   // temp/tint/saturation/vibrance
    const val DETAIL = 1L shl 2   // texture/clarity/dehaze
    const val VIGNETTE = 1L shl 3
    const val GRAIN = 1L shl 4
    const val MASK_HSL = 1L shl 5
}