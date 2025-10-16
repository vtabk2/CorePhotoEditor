package com.example.photoeditor.utils

import android.app.ActivityManager
import android.content.Context

object ImageSizeCalculator {

    /** Kích thước cạnh dài gợi ý cho editor chính (px) theo RAM app. */
    fun calcEditorSize(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memClassMb = am.memoryClass // MB heap limit cho app
        return when {
            memClassMb >= 512 -> 1080
            memClassMb >= 384 -> 960
            memClassMb >= 256 -> 720
            else -> 512
        }
    }

    /** Kích thước cho chế độ FreeStyle/Collage (px) theo RAM app. */
    fun calcFreeStyleSize(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memClassMb = am.memoryClass
        return if (memClassMb >= 256) 512 else 384
    }
}
