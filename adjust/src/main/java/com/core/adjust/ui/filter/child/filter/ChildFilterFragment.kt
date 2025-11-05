package com.core.adjust.ui.filter.child.filter

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentChildFilterBinding

class ChildFilterFragment : Fragment(R.layout.f_fragment_child_filter) {
    private var _bindingView: FFragmentChildFilterBinding? = null
    private val bindingView get() = _bindingView!!

    private var filterCategoryAdapter: FilterCategoryAdapter? = null
    private var filterAdapter: FilterAdapter? = null

    private val childFilterViewModel by viewModels<ChildFilterViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _bindingView = FFragmentChildFilterBinding.bind(view)

        childFilterViewModel.loadData()

        childFilterViewModel.filterListLiveData.observe(this) { list ->
            filterCategoryAdapter?.submitList(list)
            filterAdapter?.submitList(list)
        }

        filterCategoryAdapter = FilterCategoryAdapter { index ->
            filterAdapter?.getPositionForGroup(index)?.let { pos ->
                bindingView.rvFilterCategory.smoothScrollToPosition(pos)
            }
        }

        filterAdapter = FilterAdapter { groupIndex ->
            filterCategoryAdapter?.setSelected(groupIndex)
            bindingView.rvFilterCategory.smoothScrollToPosition(groupIndex)
        }

        bindingView.rvFilterCategory.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = filterCategoryAdapter
        }

        bindingView.rvFilter.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
            filterAdapter?.attachScrollSync(this)
        }
    }
}