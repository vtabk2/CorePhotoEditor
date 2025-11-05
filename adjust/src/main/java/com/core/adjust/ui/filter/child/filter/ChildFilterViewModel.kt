package com.core.adjust.ui.filter.child.filter

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.core.adjust.model.lut.LutGroup
import com.core.adjust.model.lut.LutPresetRoot
import com.core.adjust.utils.GsonUtils.convertToObject
import com.core.gscore.utils.extensions.getTextFromAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildFilterViewModel(application: Application) : AndroidViewModel(application) {
    val filterListLiveData = MutableLiveData<MutableList<LutGroup>>()

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = loadGroupedLutPresets(getApplication())
            filterListLiveData.postValue(root.groups.toMutableList())
        }
    }

    private fun loadGroupedLutPresets(context: Context): LutPresetRoot {
        val json = context.assets.getTextFromAsset("filters/lut_presets.json")
        return json?.convertToObject<LutPresetRoot>() ?: LutPresetRoot()
    }
}