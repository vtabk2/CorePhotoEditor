package com.core.adjust.utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File

fun ImageView.loadLutThumb(context: Context, thumbPath: String) {
    try {
        val file = File(thumbPath)
        if (file.exists()) {
            // ✅ Load ảnh từ file thật
            Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(this)
        } else {
            // ⚠️ Không tồn tại → thử load từ assets/thumb/
            val name = file.nameWithoutExtension
            val assetPath = "file:///android_asset/thumb/$name.jpg"
            Glide.with(context)
                .load(assetPath)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(this)
        }
    } catch (e: Exception) {
        // 🚑 Trường hợp path bị lỗi hoặc không hợp lệ
        val assetPath = "file:///android_asset/thumb/${thumbPath.substringAfterLast('/').substringBeforeLast('.')}.jpg"
        Glide.with(context)
            .load(assetPath)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(this)
    }
}