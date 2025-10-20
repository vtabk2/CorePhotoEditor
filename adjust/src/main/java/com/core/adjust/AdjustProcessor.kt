package com.core.adjust

import android.graphics.Bitmap
import android.util.Log

object AdjustProcessor {
    init {
        System.loadLibrary("adjust")
    }

    external fun applyAdjustNative(bitmap: Bitmap, params: AdjustParams, progress: AdjustProgress?)

    external fun clearCache()

    external fun releasePool()

    fun applyAdjust(bitmap: Bitmap?, params: AdjustParams, progress: AdjustProgress?) {
        if (bitmap == null) return
        val mask = AdjustParams.buildMask(params)
        if (mask == 0L) return
        Log.d("TAG5", "AdjustProcessor_applyAdjust: params = $params")
        applyAdjustNative(bitmap, params.copy(activeMask = mask), progress)
    }
}
