package com.core.adjust.model

import androidx.annotation.ColorInt

data class ColorPillUi(val channel: ColorChannel, @ColorInt val color: Int, val selected: Boolean)