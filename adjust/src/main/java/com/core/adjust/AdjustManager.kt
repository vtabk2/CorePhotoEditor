package com.core.adjust

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AdjustManager chịu trách nhiệm quản lý ảnh gốc, ảnh preview và thông số chỉnh ảnh.
 * Tất cả thay đổi sẽ non-destructive (không làm hỏng ảnh gốc).
 */
class AdjustManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private var originalBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var applyJob: Job? = null

    @Volatile
    private var isProcessing = false

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
        if (isProcessing) return
        isProcessing = true

        // Nếu đang chạy 1 job cũ thì hủy để không render thừa
        applyJob?.cancel()

        applyJob = lifecycleScope.launch(Dispatchers.Default) {
            val work = base.copy(Bitmap.Config.ARGB_8888, true)

            try {
                Log.d("TAG5", "AdjustManager_applyAdjust: ")
                val changed = AdjustProcessor.applyAdjust(context, work, params, progress = object : AdjustProgress {
                    override fun onProgress(percent: Int) {
                        Log.d("TAG5", "AdjustManager_onProgress: percent = $percent")
                    }
                })

                if (changed) {
                    withContext(Dispatchers.Main) {
                        previewBitmap?.recycle()
                        previewBitmap = work
                        //
                        Log.d("TAG5", "AdjustManager_applyAdjust: areBitmapsDifferent = " + areBitmapsDifferent(base, work))
                        //
                        onUpdated(work)
                    }
                } else {
                    work.recycle() // bỏ nếu không thay đổi
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    fun areBitmapsDifferent(b1: Bitmap?, b2: Bitmap?): Boolean {
        if (b1 == null || b2 == null) return true
        if (b1.width != b2.width || b1.height != b2.height) return true

        val same = b1.sameAs(b2)
        return !same
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

        AdjustProcessor.releasePool()
    }
}
