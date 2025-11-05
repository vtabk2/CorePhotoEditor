package com.core.adjust.ui.filter.child.filter

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.adjust.model.lut.LutPresetRoot
import com.core.adjust.utils.GsonUtils.convertToObject
import com.core.gscore.utils.extensions.getTextFromAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildFilterViewModel(application: Application) : AndroidViewModel(application) {
    fun loadData() {
        Log.d("TAG5", "ChildFilterViewModel_loadData: ")
        viewModelScope.launch(Dispatchers.IO) {
            val root = loadGroupedLutPresets(getApplication())
            Log.d("TAG5", "ChildFilterViewModel_loadData: root = $root")
            root.groups.forEach { group ->
                Log.d("TAG5", "Group: ${group.name}")
                group.filters.forEach { filter ->
                    Log.d("TAG5", "  → ${filter.name} (${filter.file})")
                }
            }
        }
    }

    private fun loadGroupedLutPresets(context: Context): LutPresetRoot {
        val json = context.assets.getTextFromAsset("filters/lut_presets.json")
        return json?.convertToObject<LutPresetRoot>() ?: LutPresetRoot()
    }
}