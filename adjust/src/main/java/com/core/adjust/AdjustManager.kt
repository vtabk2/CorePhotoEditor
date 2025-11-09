package com.core.adjust

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.core.adjust.model.lut.LutFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * Tạo thumbnail LUT và lưu vào Downloads/LUT_Thumbs (Android 10+ safe)
     */
    fun generateLutThumbsToDownloads(lutList: List<LutFilter>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/LUT_Thumbs"

            Log.d("TAG5", "AdjustManager_generateLutThumbsToDownloads: lutList.size = ${lutList.size}")

            originalBitmap?.let { bitmap ->
                lutList.forEach { lut ->
                    try {
                        if (lut.filePath.isBlank()) return@forEach

                        val fileName = "${lut.name}.jpg"

                        // 🧹 1️⃣ Xóa file cũ nếu trùng tên
                        val projection = arrayOf(MediaStore.MediaColumns._ID)
                        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
                        val selectionArgs = arrayOf(fileName, "$relativePath/")

                        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                                val oldUri = ContentUris.withAppendedId(collection, id)
                                resolver.delete(oldUri, null, null)
                                Log.d("TAG5", "🧹 Deleted old LUT thumb: $fileName")
                            }
                        }

                        // 🔹 2️⃣ Tạo entry mới trong MediaStore
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        }

                        val uri = resolver.insert(collection, values)
                        if (uri == null) {
                            Log.w("TAG5", "⚠️ Không thể tạo MediaStore entry cho $fileName")
                            return@forEach
                        }

                        // 🔹 3️⃣ Tạo thumbnail LUT
                        val scaled = bitmap.scaleAndCropToExactSize(300, 300)
                        val params = AdjustParams(lutPath = "filters/${lut.filePath}")
                        val result = scaled.copy(Bitmap.Config.ARGB_8888, true)
                        val success = AdjustProcessor.applyAdjust(context, result, params, null)

                        if (success) {
                            resolver.openOutputStream(uri)?.use { out ->
                                result.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            lut.thumbPath = uri.toString()
                            Log.d("TAG5", "✅ Saved LUT thumb: $fileName to $relativePath")
                        } else {
                            Log.w("TAG5", "⚠️ Failed to apply LUT: ${lut.name}")
                        }

                    } catch (e: Exception) {
                        Log.e("TAG5", "❌ Error creating thumb for ${lut.name}", e)
                    }
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
