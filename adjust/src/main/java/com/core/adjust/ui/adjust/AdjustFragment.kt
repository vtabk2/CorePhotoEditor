package com.core.adjust.ui.adjust

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.core.adjust.AdjustParams
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentAdjustBinding
import com.core.adjust.model.AdjustSlider
import com.core.adjust.ui.ShareAdjustViewModel
import kotlinx.coroutines.launch

class AdjustFragment : Fragment(R.layout.f_fragment_adjust) {
    private var _bindingView: FFragmentAdjustBinding? = null
    private val bindingView get() = _bindingView!!

    private val shareAdjustViewModel: ShareAdjustViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _bindingView = FFragmentAdjustBinding.bind(view)

        val adjustList = listOf(
            AdjustSlider("exposure", "Exposure", R.drawable.selector_ic_exposure, -500, 500),
            AdjustSlider("brightness", "Brightness", R.drawable.selector_ic_brightness),
            AdjustSlider("contrast", "Contrast", R.drawable.selector_ic_contrast),
            AdjustSlider("highlights", "Highlights", R.drawable.selector_ic_highlights),
            AdjustSlider("shadows", "Shadows", R.drawable.selector_ic_shadows),
            AdjustSlider("whites", "Whites", R.drawable.selector_ic_whites),
            AdjustSlider("blacks", "Blacks", R.drawable.selector_ic_blacks),

            AdjustSlider("temperature", "Temperature", R.drawable.selector_ic_temperture),
            AdjustSlider("tint", "Tint", R.drawable.selector_ic_tint),
            AdjustSlider("vibrance", "Vibrance", R.drawable.selector_ic_vibrance),
            AdjustSlider("saturation", "Saturation", R.drawable.selector_ic_saturation),

            AdjustSlider("texture", "Texture", R.drawable.selector_ic_texture),
            AdjustSlider("clarity", "Clarity", R.drawable.selector_ic_clarity),
            AdjustSlider("dehaze", "Dehaze", R.drawable.selector_ic_dehaze),
            AdjustSlider("vignette", "Vignette", R.drawable.selector_ic_vignette),
            AdjustSlider("grain", "Grain", R.drawable.selector_ic_grain, 0),
        )

        val adjustAdapter = AdjustAdapter { slider, required ->
            if (required) {
                val span = (slider.max - slider.min)
                bindingView.seekBar.max = span
                bindingView.seekBar.progress = slider.value - slider.min
                bindingView.tvValue.text = slider.value.toString()
            }

            map(slider, shareAdjustViewModel.params)
            shareAdjustViewModel.applyAdjust()
        }

        bindingView.rvAdjust.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = adjustAdapter
            setHasFixedSize(true)
            itemAnimator = null
            clipToPadding = false
            setPadding(64, 0, 64, 0)
        }

        bindingView.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                adjustAdapter.updateValue(progress, callback = { value ->
                    bindingView.tvValue.text = value.toString()
                })
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                adjustAdapter.applyAdjust()
            }
        })

        // ✅ Snap helper để auto "bắt giữa"
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(bindingView.rvAdjust)

        // ✅ Đặt item đầu tiên làm mặc định + scroll vào giữa
        adjustAdapter.submitList(adjustList)

        adjustAdapter.setSelectedKey(adjustList.getOrNull(0)?.key)

        // Reset
        viewLifecycleOwner.lifecycleScope.launch {
            shareAdjustViewModel.resetFlow.collect { reset ->
                if (reset) {

                }
            }
        }
    }

    override fun onDestroyView() {
        _bindingView = null
        super.onDestroyView()
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