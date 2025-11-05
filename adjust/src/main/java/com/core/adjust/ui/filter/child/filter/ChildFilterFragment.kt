package com.core.adjust.ui.filter.child.filter

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentChildFilterBinding
import com.core.adjust.ui.ShareAdjustViewModel
import kotlinx.coroutines.launch

class ChildFilterFragment : Fragment(R.layout.f_fragment_child_filter) {
    private var _bindingView: FFragmentChildFilterBinding? = null
    private val bindingView get() = _bindingView!!

    private var filterCategoryAdapter: FilterCategoryAdapter? = null
    private var filterAdapter: FilterAdapter? = null

    private val childFilterViewModel by viewModels<ChildFilterViewModel>()

    private val shareAdjustViewModel: ShareAdjustViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context?.let {

            _bindingView = FFragmentChildFilterBinding.bind(view)

            val customScrollLinearLayoutManager = CustomScrollLinearLayoutManager(it)

            filterCategoryAdapter = FilterCategoryAdapter(
                onCategorySelected = { group ->
                    bindingView.rvFilter.stopScroll()
                    customScrollLinearLayoutManager.scrollToPositionWithOffset(group.index, 0)
                },
                callbackScroll = { index ->
                    val position = if (index < 2) 0 else index
                    bindingView.rvFilterCategory.scrollToPosition(position)
                })

            filterAdapter = FilterAdapter(
                onFilterSelected = { filter ->
                    shareAdjustViewModel.params.lutPath = "filters/${filter.file}"
                    shareAdjustViewModel.applyAdjust()
                },
                callbackScroll = { index ->
                    val position = if (index < 2) 0 else index
                    bindingView.rvFilter.scrollToPosition(position)
                })

            bindingView.rvFilter.addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val findFirstCompletelyVisibleItemPosition: Int = customScrollLinearLayoutManager.findFirstCompletelyVisibleItemPosition()
                    filterCategoryAdapter?.checkScroll(findFirstCompletelyVisibleItemPosition)
                }
            })

            bindingView.rvFilterCategory.apply {
                layoutManager = CustomScrollLinearLayoutManager(it)
                adapter = filterCategoryAdapter
                itemAnimator = null
            }

            bindingView.rvFilter.apply {
                layoutManager = customScrollLinearLayoutManager
                adapter = filterAdapter
                itemAnimator = null
            }

            childFilterViewModel.loadData()
            childFilterViewModel.filterCategoryListLiveData.observe(this) { filterCategoryList ->
                filterCategoryAdapter?.submitList(filterCategoryList)
            }
            childFilterViewModel.filterListLiveData.observe(this) { filterList ->
                filterAdapter?.submitList(filterList)
            }

            // Reset
            viewLifecycleOwner.lifecycleScope.launch {
                shareAdjustViewModel.resetFlow.collect { mode ->
                    when (mode) {
                        ShareAdjustViewModel.FILTER -> {
                            filterCategoryAdapter?.unSelectedAll()
                            filterAdapter?.unSelectedAll()

                            shareAdjustViewModel.params.lutPath = null
                            shareAdjustViewModel.applyAdjust()
                        }
                    }
                }
            }
        }
    }
}