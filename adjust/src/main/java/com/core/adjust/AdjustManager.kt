package com.core.adjust

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.core.adjust.model.lut.LutFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

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

    private fun Bitmap.scaleAndCropToExactSize(targetWidth: Int = 220, targetHeight: Int = 300): Bitmap {
        // Tính tỉ lệ scale cần thiết để phủ kín cả 2 chiều
        val scaleRatio = max(
            targetWidth.toFloat() / this.width,
            targetHeight.toFloat() / this.height
        )

        // Tạo matrix để scale ảnh
        val matrix = Matrix().apply {
            postScale(scaleRatio, scaleRatio)
        }

        // Tạo bitmap mới đã scale
        val scaledBitmap = Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)

        // Tính toán vị trí cắt ở trung tâm
        val x = (scaledBitmap.width - targetWidth) / 2
        val y = (scaledBitmap.height - targetHeight) / 2

        // Đảm bảo không cắt vượt ra ngoài kích thước ảnh
        val cropX = max(0, x)
        val cropY = max(0, y)
        val cropWidth = min(targetWidth, scaledBitmap.width - cropX)
        val cropHeight = min(targetHeight, scaledBitmap.height - cropY)

        // Thực hiện cắt và trả về kết quả
        return Bitmap.createBitmap(scaledBitmap, cropX, cropY, cropWidth, cropHeight)
    }

    /**
     * Tạo thumbnail LUT và lưu vào DCIM/LUT_Thumbs
     */
    fun generateLutThumbsToDCIM(lutList: List<LutFilter>) {
        Log.d("TAG5", "AdjustManager_generateLutThumbsToDCIM: generateLutThumbsToDCIM.originalBitmap = $originalBitmap")
        originalBitmap?.let { bitmap ->
            // ✅ Thư mục lưu thumb
            val outputDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "LUT_Thumbs")
            if (!outputDir.exists()) outputDir.mkdirs()

            Log.d("TAG5", "AdjustManager_generateLutThumbsToDCIM: lutList.size = " + lutList.size)
            lutList.forEach { lut ->
                try {
                    if (lut.file.isBlank()) return@forEach

                    val thumbFile = File(outputDir, "${lut.name}.jpg")

                    Log.d("TAG5", "AdjustManager_generateLutThumbsToDCIM: thumbFile = $thumbFile")

                    // 🧹 Xóa sạch nếu có tồn tại
                    if (thumbFile.exists()) {
                        if (thumbFile.isDirectory) {
                            thumbFile.deleteRecursively()
                        } else {
                            val deleted = thumbFile.delete()
                            if (!deleted) Log.w("LutThumb", "⚠️ Không thể xóa file cũ: ${thumbFile.absolutePath}")
                        }
                    }

                    // ✅ Đảm bảo file mới được tạo
                    thumbFile.createNewFile()

                    // 🔹 Tạo bitmap nhỏ để áp LUT
                    val scaled = bitmap.scaleAndCropToExactSize(300, 300)

                    val params = AdjustParams(lutPath = lut.file)
                    val result = scaled.copy(Bitmap.Config.ARGB_8888, true)
                    val success = AdjustProcessor.applyAdjust(context, result, params, null)

                    if (success) {
                        FileOutputStream(thumbFile).use {
                            result.compress(Bitmap.CompressFormat.JPEG, 90, it)
                        }
                        lut.thumbPath = thumbFile.absolutePath
                        Log.d("TAG5", "✅ Saved ${lut.name} -> ${thumbFile.absolutePath}")
                    } else {
                        Log.w("TAG5", "⚠️ Failed to apply LUT: ${lut.name}")
                    }

                } catch (e: Exception) {
                    Log.e("TAG5", "❌ Error creating thumb for ${lut.name}", e)
                }
            }
        }
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
