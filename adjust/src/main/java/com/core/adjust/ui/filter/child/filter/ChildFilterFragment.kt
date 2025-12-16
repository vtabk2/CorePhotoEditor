package com.core.adjust.ui.filter.child.filter

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
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

            bindingView.seekBarFilter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    bindingView.tvValueFilter.text = progress.toString()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { progress ->
                        shareAdjustViewModel.updateLutAmount(progress / 100f)
                    }
                }
            })

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
                context = it,
                onFilterSelected = { filter ->
                    shareAdjustViewModel.updateLutPath(filter.filePath)
                },
                onSelectedAgain = { filter ->

                },
                onAutoScroll = { index ->
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
            childFilterViewModel.filterCategoryListLiveData.observe(viewLifecycleOwner) { filterCategoryList ->
                filterCategoryAdapter?.submitList(filterCategoryList)
            }
            childFilterViewModel.filterListLiveData.observe(viewLifecycleOwner) { filterList ->
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

            viewLifecycleOwner.lifecycleScope.launch {
                shareAdjustViewModel.requiredCreateThumbFlow.collect { requiredCreateThumb ->
                    if (requiredCreateThumb) {
                        Log.d("TAG5", "ChildFilterFragment_onViewCreated: requiredCreateThumb")
                        childFilterViewModel.filterListLiveData.value?.let { filterList ->
                            shareAdjustViewModel.manager.generateLutThumbsToDownloads(filterList)
                        }
                    }
                }
            }
        }
    }
}