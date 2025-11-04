package com.core.adjust.ui.adjust

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
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
            AdjustSlider("exposure", "Exposure", R.drawable.selector_ic_exposure),
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
            AdjustSlider("grain", "Grain", R.drawable.selector_ic_grain),
        )

        val adjustAdapter = AdjustAdapter { slider ->
//            viewModel.selectAdjust(slider.key)
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

        adjustAdapter.setSelectedKey(adjustList.getOrNull(0)?.key, callback = { value ->
            bindingView.tvValue.text = value.toString()
        })

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
}