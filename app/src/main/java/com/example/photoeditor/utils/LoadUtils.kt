package com.example.photoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LoadUtils {
    suspend fun loadBitmapForEditingWithMemoryClass(
        context: Context,
        uri: Uri,
        freeStyle: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val target = if (freeStyle) {
            ImageSizeCalculator.calcFreeStyleSize(context)
        } else {
            ImageSizeCalculator.calcEditorSize(context)
        }

        val req = ImageRequest.Builder(context)
            .data(uri)
            .size(target, target)            // ⬅️ dùng Int width/height
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()

        val result = ImageLoader(context).execute(req)
        if (result is SuccessResult) {
            (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
        } else null
    }
}