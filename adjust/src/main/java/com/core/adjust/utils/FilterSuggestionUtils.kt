package com.core.adjust.utils

import android.graphics.Bitmap
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

object FilterSuggestionUtils {

    /**
     * Gợi ý nhóm filter dựa trên khuôn mặt (ML Kit), EXIF và tone màu ảnh.
     */
    suspend fun suggestGroups(bitmap: Bitmap?, exifPath: String? = null): List<String> {
        val results = mutableListOf<String>()

        // 1️⃣ Khuôn mặt (ML Kit)
        val hasFaces = detectFaceMLKit(bitmap)
        if (hasFaces) results += listOf("Portrait", "Lifestyle")

        // 2️⃣ Đọc EXIF cơ bản (nếu có)
        val exif = exifPath?.let { runCatching { ExifInterface(it) }.getOrNull() }
        val scene = exif?.getAttributeInt(ExifInterface.TAG_SCENE_CAPTURE_TYPE, -1)
        val lightSource = exif?.getAttributeInt(ExifInterface.TAG_LIGHT_SOURCE, -1)

        if (scene == ExifConst.SCENE_CAPTURE_TYPE_LANDSCAPE) {
            results += listOf("Travel", "Mood & Atmosphere")
        } else if (scene == ExifConst.SCENE_CAPTURE_TYPE_NIGHT_SCENE) {
            results += listOf("Urban & Street", "Black & White")
        }

        if (lightSource == ExifConst.LIGHT_SOURCE_DAYLIGHT ||
            lightSource == ExifConst.LIGHT_SOURCE_D65 ||
            lightSource == ExifConst.LIGHT_SOURCE_D50
        ) {
            results += listOf("Travel", "Lifestyle")
        }

        // 3️⃣ Phân tích nhanh bitmap
        val outdoor = detectOutdoor(bitmap)
        if (outdoor) results += listOf("Travel", "Mood & Atmosphere")

        val lowLight = detectLowLight(bitmap)
        if (lowLight) results += listOf("Urban & Street", "Black & White")

        val vintage = detectVintageTone(bitmap)
        if (vintage) results += listOf("Vintage & Film")

        if (results.isEmpty()) results += "Misc"

        return results.distinct().take(3)
    }

    // 🔍 Nhận diện khuôn mặt (Play Services ML Kit)
    private suspend fun detectFaceMLKit(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        val faces = detector.process(image).await()
        detector.close()
        return faces.isNotEmpty()
    }

    // 🌤️ Ngoài trời: pixel phía trên chứa nhiều xanh
    private fun detectOutdoor(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return b > r && b > g
    }

    // 🌙 Ảnh tối
    private fun detectLowLight(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val avg = pixels.map {
            val r = (it shr 16) and 0xFF
            val g = (it shr 8) and 0xFF
            val b = it and 0xFF
            (r + g + b) / 3f
        }.average()
        return avg < 80
    }

    // 🎞️ Tone vàng/ấm → film / vintage
    private fun detectVintageTone(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val sample = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val r = (sample shr 16) and 0xFF
        val g = (sample shr 8) and 0xFF
        val b = sample and 0xFF
        return abs(r - g) < 20 && r > b && g > b
    }

    // 🧩 Hằng EXIF cần thiết nhất
    private object ExifConst {
        // Scene capture type
        const val SCENE_CAPTURE_TYPE_LANDSCAPE = 1
        const val SCENE_CAPTURE_TYPE_PORTRAIT = 2
        const val SCENE_CAPTURE_TYPE_NIGHT_SCENE = 3

        // Light source
        const val LIGHT_SOURCE_DAYLIGHT = 1
        const val LIGHT_SOURCE_D65 = 21
        const val LIGHT_SOURCE_D50 = 23
    }
}