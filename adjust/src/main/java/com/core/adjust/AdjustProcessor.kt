package com.core.adjust

import android.graphics.Bitmap
import android.util.Log

object AdjustProcessor {
    init {
        System.loadLibrary("adjust")
    }

    external fun applyAdjustNative(bitmap: Bitmap, params: AdjustParams, progress: AdjustProgress?): Boolean

    external fun clearCache()

    external fun releasePool()

    fun applyAdjust(bitmap: Bitmap?, params: AdjustParams, progress: AdjustProgress?): Boolean {
        if (bitmap == null) return false
        val mask = AdjustParams.buildMask(params)
        if (mask == 0L) return true // cần return true để áp dụng lại ảnh gốc

        // 🔹 Nếu chỉ có LUT nhưng LUT không thay đổi so với lần trước => skip
        if (mask == AdjustMask.MASK_LUT && params.lutPath.isNullOrBlank()) return false

        Log.d("TAG5", "AdjustProcessor_applyAdjust: params = $params")
        return applyAdjustNative(bitmap, params.copy(activeMask = mask), progress)
    }
}
