package com.core.adjust.model

data class HslAdjustState(
    val selected: ColorChannel = ColorChannel.ORANGE,
    val map: MutableMap<ColorChannel, HslTriple> =
        ColorChannel.entries.associateWith { HslTriple() }.toMutableMap(),
    val isPreviewHeld: Boolean = false,
    val enableTargeted: Boolean = false
)