package com.core.adjust.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.core.adjust.model.ColorChannel
import com.core.adjust.model.HslAdjustState
import com.core.adjust.model.HslTriple
import com.core.adjust.model.lut.LutPresetRoot
import com.core.adjust.utils.GsonUtils.convertToObject
import com.core.gscore.utils.extensions.getTextFromAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ColorMixerViewModel : ViewModel() {
    private val _state = MutableStateFlow(HslAdjustState())
    val state: StateFlow<HslAdjustState> = _state

    fun select(channel: ColorChannel) = _state.update { it.copy(selected = channel) }

    fun updateHue(v: Int) = updateCurrent { it.hue = v.coerceIn(-100, 100) }
    fun updateSat(v: Int) = updateCurrent { it.saturation = v.coerceIn(-100, 100) }
    fun updateLum(v: Int) = updateCurrent { it.luminance = v.coerceIn(-100, 100) }

    private inline fun updateCurrent(block: (HslTriple) -> Unit) {
        _state.update { s ->
            val cur = s.map[s.selected] ?: HslTriple()
            block(cur)
            s.copy(map = s.map.apply { put(s.selected, cur) })
        }
    }

    fun resetCurrent() = _state.update { s -> s.map[s.selected] = HslTriple(); s.copy(map = s.map) }
    fun resetAll() = _state.update { HslAdjustState() }
    fun setPreviewHeld(b: Boolean) = _state.update { it.copy(isPreviewHeld = b) }
    fun toggleTargeted() = _state.update { it.copy(enableTargeted = !it.enableTargeted) }

    fun loadData(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val root = loadGroupedLutPresets(context)
            root.groups.forEach { group ->
                Log.d("LUT", "Group: ${group.name}")
                group.filters.forEach { filter ->
                    Log.d("LUT", "  → ${filter.name} (${filter.file})")
                }
            }
        }
    }

    fun loadGroupedLutPresets(context: Context): LutPresetRoot {
        val json = context.assets.getTextFromAsset("filters/lut_presets.json")
        return json?.convertToObject<LutPresetRoot>() ?: LutPresetRoot()
    }
}