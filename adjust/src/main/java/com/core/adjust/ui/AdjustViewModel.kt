package com.core.adjust.ui

import android.graphics.Bitmap
import android.util.Log
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

class AdjustViewModel(val manager: AdjustManager) : ViewModel() {

    private val _previewBitmap = MutableLiveData<Bitmap>()
    val previewBitmap: LiveData<Bitmap> = _previewBitmap

    private val _resetFlow = MutableSharedFlow<Boolean>()
    val resetFlow: SharedFlow<Boolean> = _resetFlow

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

    fun resetHsl() {
        manager.resetHsl()
        viewModelScope.launch(Dispatchers.IO) {
            _resetFlow.emit(true)
        }
        applyAdjust()
    }

    fun reset(mode: Int) {
        Log.d("TAG5", "AdjustViewModel_reset: mode = $mode")
        when (mode) {
            FILTER -> {

            }

            ADJUST -> {
                manager.resetLight()
                manager.resetColor()
                manager.resetEffects()
            }

            HSL -> {
                manager.resetHsl()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _resetFlow.emit(true)
        }
        applyAdjust()
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

    class Factory(private val scope: LifecycleCoroutineScope) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val manager = AdjustManager(scope)
            return AdjustViewModel(manager) as T
        }
    }

    companion object {
        const val FILTER = 1
        const val ADJUST = 2
        const val HSL = 3
    }
}
