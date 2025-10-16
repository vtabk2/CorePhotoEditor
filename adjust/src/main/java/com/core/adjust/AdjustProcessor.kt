package com.core.adjust

import android.graphics.Bitmap
import android.util.Log

object AdjustProcessor {
    init {
        System.loadLibrary("adjust")
    }

    // static native method (annotated as @JvmStatic so JNI name matches a static method)
    @JvmStatic
    external fun applyAdjustNative(bitmap: Bitmap, params: AdjustParams)

    fun applyAdjust(bitmap: Bitmap?, params: AdjustParams) {
        if (bitmap == null) return
        Log.d("TAG5", "AdjustProcessor_applyAdjust: params = $params")
        applyAdjustNative(bitmap, params)
    }
}
