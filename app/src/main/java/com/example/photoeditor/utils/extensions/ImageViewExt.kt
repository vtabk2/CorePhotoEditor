package com.example.photoeditor.utils.extensions

import android.net.Uri
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil3.asImage
import coil3.load
import coil3.request.allowHardware
import coil3.request.crossfade
import com.example.photoeditor.utils.ImageSizeCalculator

/**
 * Load ảnh vào ImageView bằng Coil 3, downsample theo RAM (memoryClass).
 *
 * @param uri         Uri ảnh cần load
 * @param freeStyle   true nếu là màn freestyle/collage (ảnh nhỏ nhiều ảnh)
 * @param placeholderResId  ID drawable placeholder tuỳ chọn. Nếu = null -> không dùng placeholder/error.
 */
fun ImageView.loadUriWithMemoryClass(
    uri: Uri,
    freeStyle: Boolean = false,
    placeholderResId: Int? = null
) {
    val ctx = context
    val target = if (freeStyle) {
        ImageSizeCalculator.calcFreeStyleSize(ctx)
    } else {
        ImageSizeCalculator.calcEditorSize(ctx)
    }

    this.load(uri) {
        size(target, target)
        allowHardware(false)
        crossfade(true)

        // ✅ chỉ thêm placeholder/error nếu có truyền vào
        placeholderResId?.let { resId ->
            val phImage = ContextCompat.getDrawable(ctx, resId)?.asImage()
            placeholder(phImage)
            error(phImage)
        }
    }
}
