package com.core.adjust.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.core.adjust.AdjustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ShareAdjustViewModel(val manager: AdjustManager) : ViewModel() {

    private val _previewBitmap = MutableLiveData<Bitmap>()
    val previewBitmap: LiveData<Bitmap> = _previewBitmap

    private val _resetFlow = MutableSharedFlow<Int>()
    val resetFlow: SharedFlow<Int> = _resetFlow

    private val _closeFlow = MutableSharedFlow<Boolean>()
    val closeFlow: SharedFlow<Boolean> = _closeFlow

    val params get() = manager.params

    fun setOriginal(bitmap: Bitmap) {
        manager.setOriginalBitmap(bitmap)
        _previewBitmap.value = bitmap
    }

    fun applyAdjust() {
        manager.applyAdjust { bmp ->
            _previewBitmap.value = bmp
        }
    }

    fun updateHsl(channelIndex: Int, hue: Int, sat: Int, lum: Int) {
        params.hslHue[channelIndex] = hue.toFloat()
        params.hslSaturation[channelIndex] = sat.toFloat()
        params.hslLuminance[channelIndex] = lum.toFloat()
        applyAdjust()
    }

    fun reset(mode: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _resetFlow.emit(mode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        manager.release()
    }

    fun close() {
        viewModelScope.launch(Dispatchers.IO) {
            _closeFlow.emit(true)
        }
    }

    class Factory(
        private val context: Context,
        private val scope: LifecycleCoroutineScope
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val manager = AdjustManager(context.applicationContext, scope)
            return ShareAdjustViewModel(manager) as T
        }
    }

    companion object {
        const val FILTER = 1
        const val ADJUST = 2
        const val HSL = 3
    }
}
