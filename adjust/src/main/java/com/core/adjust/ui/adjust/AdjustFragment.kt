package com.core.adjust.ui.adjust

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentAdjustBinding

class AdjustFragment : Fragment(R.layout.f_fragment_adjust) {
    private var _bindingView: FFragmentAdjustBinding? = null
    private val bindingView get() = _bindingView!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _bindingView = FFragmentAdjustBinding.bind(view)
    }

    override fun onDestroyView() {
        _bindingView = null
        super.onDestroyView()
    }
}