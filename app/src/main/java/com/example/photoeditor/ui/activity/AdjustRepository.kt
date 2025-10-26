package com.example.photoeditor.ui.activity

import com.core.adjust.AdjustParams
import com.core.adjust.model.AdjustSlider
import com.core.adjust.model.AdjustTab
import com.example.photoeditor.R

object AdjustRepository {

    fun defaultTabs(): List<AdjustTab> {
        return listOf(
            AdjustTab(
                key = "light",
                iconRes = R.drawable.ic_tab_light, // tạo vector đơn giản
                label = "Light",
                sliders = listOf(
                    AdjustSlider("exposure", "Exposure", -500, 500, 0), // EV *100
                    AdjustSlider("brightness", "Brightness", -100, 100, 0),
                    AdjustSlider("contrast", "Contrast", -100, 100, 0),
                    AdjustSlider("highlights", "Highlights", -100, 100, 0),
                    AdjustSlider("shadows", "Shadows", -100, 100, 0),
                    AdjustSlider("whites", "Whites", -100, 100, 0),
                    AdjustSlider("blacks", "Blacks", -100, 100, 0),
                )
            ),
            AdjustTab(
                key = "color",
                iconRes = R.drawable.ic_tab_color,
                label = "Color",
                sliders = listOf(
                    AdjustSlider("temperature", "Temperature", -100, 100, 0),
                    AdjustSlider("tint", "Tint", -100, 100, 0),
                    AdjustSlider("vibrance", "Vibrance", -100, 100, 0),
                    AdjustSlider("saturation", "Saturation", -100, 100, 0),
                )
            ),
            AdjustTab(
                key = "effects",
                iconRes = R.drawable.ic_tab_effects,
                label = "Effects",
                sliders = listOf(
                    AdjustSlider("texture", "Texture", -100, 100, 0),
                    AdjustSlider("clarity", "Clarity", -100, 100, 0),
                    AdjustSlider("dehaze", "Dehaze", -100, 100, 0),
                    AdjustSlider("vignette", "Vignette", -100, 100, 0),
                    AdjustSlider("grain", "Grain", 0, 100, 0),
                )
            ),
            AdjustTab(
                key = "hsl",
                iconRes = com.core.adjust.R.drawable.f_ic_targeted, // tạo vector tròn 8 màu hoặc icon “color wheel”
                label = "HSL",
                sliders = emptyList() // <- rất quan trọng: để controller biết hiển thị UI custom
            )
        )
    }

    fun map(list: List<AdjustSlider>, adjustParams: AdjustParams) {
        list.forEach {
            when (it.key) {
                "exposure" -> it.value = (adjustParams.exposure * 100).toInt()
                "brightness" -> it.value = (adjustParams.brightness * 100).toInt()
                "contrast" -> it.value = (adjustParams.contrast * 100).toInt()
                "highlights" -> it.value = (adjustParams.highlights * 100).toInt()
                "shadows" -> it.value = (adjustParams.shadows * 100).toInt()
                "whites" -> it.value = (adjustParams.whites * 100).toInt()
                "blacks" -> it.value = (adjustParams.blacks * 100).toInt()
                //
                "temperature" -> it.value = (adjustParams.temperature * 100).toInt()
                "tint" -> it.value = (adjustParams.tint * 100).toInt()
                "vibrance" -> it.value = (adjustParams.vibrance * 100).toInt()
                "saturation" -> it.value = (adjustParams.saturation * 100).toInt()
                //
                "texture" -> it.value = (adjustParams.texture * 100).toInt()
                "clarity" -> it.value = (adjustParams.clarity * 100).toInt()
                "dehaze" -> it.value = (adjustParams.dehaze * 100).toInt()
                "vignette" -> it.value = (adjustParams.vignette * 100).toInt()
                "grain" -> it.value = (adjustParams.grain * 100).toInt()
            }
        }
    }

    fun map(adjustSlider: AdjustSlider, adjustParams: AdjustParams) {
        when (adjustSlider.key) {
            "exposure" -> adjustParams.exposure = adjustSlider.value / 100f
            "brightness" -> adjustParams.brightness = adjustSlider.value / 100f
            "contrast" -> adjustParams.contrast = adjustSlider.value / 100f
            "highlights" -> adjustParams.highlights = adjustSlider.value / 100f
            "shadows" -> adjustParams.shadows = adjustSlider.value / 100f
            "whites" -> adjustParams.whites = adjustSlider.value / 100f
            "blacks" -> adjustParams.blacks = adjustSlider.value / 100f
            //
            "temperature" -> adjustParams.temperature = adjustSlider.value / 100f
            "tint" -> adjustParams.tint = adjustSlider.value / 100f
            "vibrance" -> adjustParams.vibrance = adjustSlider.value / 100f
            "saturation" -> adjustParams.saturation = adjustSlider.value / 100f
            //
            "texture" -> adjustParams.texture = adjustSlider.value / 100f
            "clarity" -> adjustParams.clarity = adjustSlider.value / 100f
            "dehaze" -> adjustParams.dehaze = adjustSlider.value / 100f
            "vignette" -> adjustParams.vignette = adjustSlider.value / 100f
            "grain" -> adjustParams.grain = adjustSlider.value / 100f
        }
    }
}
