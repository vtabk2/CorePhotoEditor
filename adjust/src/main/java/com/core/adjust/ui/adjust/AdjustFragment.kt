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
            AdjustSlider("brightness", "Brightness", R.drawable.f_ic_filter_done),
            AdjustSlider("contrast", "Contrast", R.drawable.f_ic_filter_done),
            AdjustSlider("saturation", "Saturation", R.drawable.f_ic_filter_done),
            AdjustSlider("sharpness", "Sharpness", R.drawable.f_ic_filter_done)
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