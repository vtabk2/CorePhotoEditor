package com.core.adjust.ui.filter.child.filter

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.core.adjust.model.lut.LutFilter
import com.core.adjust.model.lut.LutGroup
import com.core.adjust.model.lut.LutPresetRoot
import com.core.adjust.utils.GsonUtils.convertToObject
import com.core.gscore.utils.extensions.getTextFromAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildFilterViewModel(application: Application) : AndroidViewModel(application) {
    val filterCategoryListLiveData = MutableLiveData<MutableList<LutGroup>>()
    val filterListLiveData = MutableLiveData<MutableList<LutFilter>>()

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = loadGroupedLutPresets(getApplication()).groups

            val filterCategoryList = mutableListOf<LutGroup>()
            val filterList = mutableListOf<LutFilter>()

            list.forEach {
                filterCategoryList.add(it.apply { index = filterList.size })
                it.filters.forEach { item ->
                    item.name = item.name.replace("Table", "")
                    filterList.add(item)
                }
            }
            filterCategoryListLiveData.postValue(filterCategoryList)
            filterListLiveData.postValue(filterList)
        }
    }

    private fun loadGroupedLutPresets(context: Context): LutPresetRoot {
        val json = context.assets.getTextFromAsset("lut_presets.json")
        return json?.convertToObject<LutPresetRoot>() ?: LutPresetRoot()
    }
}