package com.core.adjust

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AdjustManager chịu trách nhiệm quản lý ảnh gốc, ảnh preview và thông số chỉnh ảnh.
 * Tất cả thay đổi sẽ non-destructive (không làm hỏng ảnh gốc).
 */
class AdjustManager(private val lifecycleScope: LifecycleCoroutineScope) {

    private var originalBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var applyJob: Job? = null

    val params = AdjustParams()

    /**
     * Khởi tạo ảnh gốc và ảnh preview ban đầu.
     */
    fun setOriginalBitmap(bitmap: Bitmap) {
        originalBitmap = bitmap
        previewBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    fun getPreviewBitmap(): Bitmap? = previewBitmap

    /**
     * Gọi hàm apply adjust non-destructive.
     * Mỗi lần người dùng kéo slider, chỉ render lại bản mới từ ảnh gốc.
     */
    fun applyAdjust(onUpdated: (Bitmap) -> Unit) {
        val base = originalBitmap ?: return
        // Nếu đang chạy 1 job cũ thì hủy để không render thừa
        applyJob?.cancel()

        applyJob = lifecycleScope.launch(Dispatchers.Default) {
            val work = base.copy(Bitmap.Config.ARGB_8888, true)
            AdjustProcessor.applyAdjust(work, params)

            withContext(Dispatchers.Main) {
                previewBitmap = work
                onUpdated(work)
            }
        }
    }

    fun resetLight() {
        params.exposure = 0f
        params.brightness = 0f
        params.contrast = 0f
        params.highlights = 0f
        params.shadows = 0f
        params.whites = 0f
        params.blacks = 0f
    }

    fun resetColor() {
        params.temperature = 0f
        params.tint = 0f
        params.vibrance = 0f
        params.saturation = 0f
    }

    fun resetEffects() {
        params.texture = 0f
        params.clarity = 0f
        params.dehaze = 0f
    }

    /**
     * Giải phóng bộ nhớ nếu không còn dùng.
     */
    fun release() {
        originalBitmap?.recycle()
        previewBitmap?.recycle()
        originalBitmap = null
        previewBitmap = null
        applyJob?.cancel()
    }
}
