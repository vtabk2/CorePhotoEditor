package com.core.adjust.utils

import android.graphics.Bitmap
import androidx.core.graphics.get
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

/**
 * Suggest LUT groups for an image using a few cheap, on-device signals.
 * Changes in this version:
 *  - Fallback group changed from "Misc" → "Essentials".
 *  - Support new sub-groups split from Misc: Essentials, Pastel, Cinematic, Studio.
 *  - Faces → add Portrait, Lifestyle, Essentials (+ Pastel when bright & low-contrast).
 *  - Low light → add Cinematic.
 *  - Vintage tone (warm yellow) → add Studio.
 *  - Outdoor → add Essentials.
 */
object FilterSuggestionUtils {

    /**
     * Gợi ý tối đa 3 nhóm LUT phù hợp.
     *
     * @param bitmap Ảnh để phân tích nhanh (có thể null nếu chỉ dựa EXIF)
     * @param exifPath Đường dẫn file dùng để đọc EXIF (có thể null)
     */
    suspend fun suggestGroups(bitmap: Bitmap?, exifPath: String? = null): List<String> {
        val results = mutableListOf<String>()

        // 1) Khuôn mặt (ML Kit)
        val hasFaces = detectFaceMLKit(bitmap)
        if (hasFaces) {
            results += listOf("Portrait", "Lifestyle", "Essentials")
            if (isBrightAndLowContrast(bitmap)) {
                results += "Pastel"
            }
        }

        // 2) EXIF cơ bản (nếu có)
        val exif = exifPath?.let { runCatching { ExifInterface(it) }.getOrNull() }
        val scene = exif?.getAttributeInt(ExifInterface.TAG_SCENE_CAPTURE_TYPE, -1)
        val lightSource = exif?.getAttributeInt(ExifInterface.TAG_LIGHT_SOURCE, -1)

        when (scene) {
            ExifConst.SCENE_CAPTURE_TYPE_LANDSCAPE -> {
                results += listOf("Travel", "Mood & Atmosphere", "Essentials")
            }

            ExifConst.SCENE_CAPTURE_TYPE_PORTRAIT -> {
                results += listOf("Portrait", "Lifestyle", "Essentials")
                if (isBrightAndLowContrast(bitmap)) results += "Pastel"
            }

            ExifConst.SCENE_CAPTURE_TYPE_NIGHT_SCENE -> {
                results += listOf("Urban & Street", "Black & White", "Cinematic")
            }
        }

        if (lightSource == ExifConst.LIGHT_SOURCE_DAYLIGHT ||
            lightSource == ExifConst.LIGHT_SOURCE_D65 ||
            lightSource == ExifConst.LIGHT_SOURCE_D50
        ) {
            results += listOf("Travel", "Lifestyle", "Essentials")
        }

        // 3) Phân tích nhanh bitmap (không tốn kém)
        val outdoor = detectOutdoor(bitmap)
        if (outdoor) results += listOf("Travel", "Mood & Atmosphere", "Essentials")

        val lowLight = detectLowLight(bitmap)
        if (lowLight) results += listOf("Urban & Street", "Black & White", "Cinematic")

        val vintage = detectVintageTone(bitmap)
        if (vintage) results += listOf("Vintage & Film", "Studio")

        // Fallback mới: Essentials (trước đây là Misc)
        if (results.isEmpty()) results += "Essentials"

        return results.distinct().take(3)
    }

    // ---------- Helpers ----------

    // ML Kit face detection (Play Services)
    private suspend fun detectFaceMLKit(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        val faces = runCatching { detector.process(image).await() }.getOrDefault(emptyList())
        detector.close()
        return faces.isNotEmpty()
    }

    // Ngoài trời: lấy 1 pixel vùng trời phía trên, ưu tiên blue
    private fun detectOutdoor(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.width < 2 || bitmap.height < 2) return false
        val pixel = bitmap[bitmap.width / 2, bitmap.height / 4]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return b > r && b > g
    }

    // Ảnh tối: trung bình luma thấp
    private fun detectLowLight(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return false
        val stepX = (w / 8).coerceAtLeast(1)
        val stepY = (h / 8).coerceAtLeast(1)
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bitmap[x, y]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sum += (r + g + b) / 3.0
                count++
                x += stepX
            }
            y += stepY
        }
        val avg = if (count > 0) sum / count else 255.0
        return avg < 80
    }

    // Tone vàng/ấm → film/vintage (r ≈ g > b)
    private fun detectVintageTone(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.width < 2 || bitmap.height < 2) return false
        val sample = bitmap[bitmap.width / 2, bitmap.height / 2]
        val r = (sample shr 16) and 0xFF
        val g = (sample shr 8) and 0xFF
        val b = sample and 0xFF
        return abs(r - g) < 20 && r > b && g > b
    }

    // Ảnh sáng, tương phản thấp, bão hòa nhẹ → hợp Pastel
    private fun isBrightAndLowContrast(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return false

        val pts = arrayOf(
            bitmap[w / 2, h / 2], bitmap[w / 4, h / 4], bitmap[3 * w / 4, h / 4], bitmap[w / 4, 3 * h / 4], bitmap[3 * w / 4, 3 * h / 4]
        )
        var lumSum = 0.0
        var lumSqSum = 0.0
        var satSum = 0.0
        for (p in pts) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val mx = maxOf(r, g, b).toDouble()
            val mn = minOf(r, g, b).toDouble()
            val luma = (r + g + b) / 3.0
            val sat = if (mx == 0.0) 0.0 else (mx - mn) / mx
            lumSum += luma
            lumSqSum += luma * luma
            satSum += sat
        }
        val n = pts.size
        val lumAvg = lumSum / n
        val lumVar = (lumSqSum / n) - (lumAvg * lumAvg)
        val satAvg = satSum / n

        val bright = lumAvg >= 170.0
        val lowContrast = lumVar <= 500.0
        val lowSaturation = satAvg <= 0.25
        return bright && (lowContrast || lowSaturation)
    }

    // Hằng EXIF cần thiết
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