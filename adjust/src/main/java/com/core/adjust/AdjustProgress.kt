package com.core.adjust

import androidx.annotation.Keep

@Keep
interface AdjustProgress {
    fun onProgress(percent: Int)
}