package com.core.adjust.ui.filter.child.filter

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.core.adjust.R

class ChildFilterFragment : Fragment(R.layout.f_fragment_child_filter) {
    private val childFilterViewModel by viewModels<ChildFilterViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childFilterViewModel.loadData()
    }
}