package com.example.photoeditor.utils.extensions

import android.graphics.Bitmap

object BitmapExt {

    /**
     * Trả về chính bitmap nếu đã mutable và là ARGB_8888.
     * Ngược lại sẽ tạo bản copy mới dạng ARGB_8888, mutable để có thể chỉnh sửa pixel.
     */
    fun Bitmap.ensureMutable(): Bitmap {
        return if (this.isMutable && this.config == Bitmap.Config.ARGB_8888) {
            this
        } else {
            this.copy(Bitmap.Config.ARGB_8888, /* isMutable = */ true)
        }
    }
}