package com.core.adjust.ui.adjust

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentAdjustBinding
import com.core.adjust.model.AdjustSlider

class AdjustFragment : Fragment(R.layout.f_fragment_adjust) {
    private var _bindingView: FFragmentAdjustBinding? = null
    private val bindingView get() = _bindingView!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _bindingView = FFragmentAdjustBinding.bind(view)

        val adjustList = listOf(
            AdjustSlider("exposure", "Exposure", R.drawable.ic_exposure),
            AdjustSlider("brightness", "Brightness", R.drawable.ic_brightness),
            AdjustSlider("contrast", "Contrast", R.drawable.ic_contrast),
            AdjustSlider("highlights", "Highlights", R.drawable.ic_highlights),
            AdjustSlider("shadows", "Shadows", R.drawable.ic_shadows),
            AdjustSlider("whites", "Whites", R.drawable.selector_ic_whites),
            AdjustSlider("blacks", "Blacks", R.drawable.selector_ic_blacks),

            AdjustSlider("temperature", "Temperature", R.drawable.ic_temperature),
            AdjustSlider("tint", "Tint", R.drawable.selector_ic_tint),
            AdjustSlider("vibrance", "Vibrance", R.drawable.ic_vibrance),
            AdjustSlider("saturation", "Saturation", R.drawable.ic_saturation),

            AdjustSlider("texture", "Texture", R.drawable.ic_texture),
            AdjustSlider("clarity", "Clarity", R.drawable.ic_clarity),
            AdjustSlider("dehaze", "Dehaze", R.drawable.ic_dehaze),
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
            clipToPadding = false
            setPadding(64, 0, 64, 0)
        }

        // ✅ Snap helper để auto "bắt giữa"
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(bindingView.rvAdjust)

        // ✅ Đặt item đầu tiên làm mặc định + scroll vào giữa
        adjustAdapter.submitList(adjustList)
        adjustAdapter.setSelectedKey(adjustList.first().key)
    }

    override fun onDestroyView() {
        _bindingView = null
        super.onDestroyView()
    }
}