package com.core.adjust.ui.filter

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentFilterBinding

class FilterFragment : Fragment(R.layout.f_fragment_filter) {
    private var _bindingView: FFragmentFilterBinding? = null
    private val bindingView get() = _bindingView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}