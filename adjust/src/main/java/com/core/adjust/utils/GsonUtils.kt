package com.core.adjust.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object GsonUtils {
    inline fun <reified T> String.convertToListObject(): List<T>? {
        val listType: Type = object : TypeToken<List<T?>?>() {}.type
        return Gson().fromJson<List<T>>(this, listType)
    }

    inline fun <reified T> String.convertToObject(): T? {
        val listType: Type = object : TypeToken<T?>() {}.type
        return Gson().fromJson<T>(this, listType)
    }
}