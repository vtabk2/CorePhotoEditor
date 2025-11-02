package com.core.adjust.ui.filter

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentFilterBinding
import com.core.adjust.ui.AdjustViewModel
import com.core.adjust.ui.ColorMixerFragment
import com.core.adjust.ui.adapter.CategoryPagerAdapter
import com.core.adjust.ui.adjust.AdjustFragment
import com.core.gscore.utils.extensions.setOnSingleClick
import com.google.android.material.tabs.TabLayoutMediator

class FilterFragment : Fragment(R.layout.f_fragment_filter) {
    private var _bindingView: FFragmentFilterBinding? = null
    private val bindingView get() = _bindingView

    private val adjustViewModel: AdjustViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _bindingView = FFragmentFilterBinding.bind(view)

        bindingView?.apply {
            val fragments = listOf(
                AdjustFragment(),
                ColorMixerFragment()
            )
            val titles = listOf("Adjust", "HSL")
            val modes = listOf(AdjustViewModel.ADJUST, AdjustViewModel.HSL)

            val adapter = CategoryPagerAdapter(requireActivity(), titles, fragments)
            viewPager2.adapter = adapter

            TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
                tab.text = adapter.getPageTitle(position)
            }.attach()

            rivReset.setOnSingleClick {
                Log.d("TAG5", "FilterFragment_onViewCreated: currentItem = " + viewPager2.currentItem)
                adjustViewModel.reset(modes[viewPager2.currentItem])
            }

            rivDone.setOnSingleClick {
                adjustViewModel.close()
            }
        }
    }

    companion object {
        private var filterFragment: FilterFragment? = null

        fun showFilterFragment(activity: FragmentActivity?, containerId: Int) {
            activity?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { act ->
                Handler(Looper.getMainLooper()).post {
                    if (act.isFinishing || act.isDestroyed) return@post

                    val fm = act.supportFragmentManager
                    if (fm.isDestroyed || fm.isStateSaved) return@post

                    val transaction = fm.beginTransaction().apply {
                        // Animation trượt lên khi show
                        setCustomAnimations(
                            R.anim.slide_in_bottom, // vào
                            R.anim.fade_out,        // ra
                            R.anim.fade_in,         // khi back
                            R.anim.slide_out_bottom // khi back hide
                        )
                    }

                    filterFragment?.let { fragment ->
                        val manager = fragment.parentFragmentManager
                        if (manager != fm && fragment.isAdded) {
                            manager.beginTransaction().remove(fragment)
                                .commitNowAllowingStateLoss()
                        } else if (fragment.view?.parent != null) {
                            (fragment.view?.parent as? ViewGroup)?.removeView(fragment.view)
                        }
                    }

                    filterFragment?.takeIf { it.isAdded }?.let {
                        transaction.show(it)
                    } ?: run {
                        filterFragment = FilterFragment()
                        transaction.add(containerId, filterFragment!!, "FilterFragment")
                    }

                    transaction.commitAllowingStateLoss()
                }
            }
        }

        fun hideFilterFragment(activity: FragmentActivity?) {
            activity?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { act ->
                Handler(Looper.getMainLooper()).post {
                    if (act.isFinishing || act.isDestroyed) return@post

                    filterFragment?.takeIf { it.isAdded }?.let { fragment ->
                        val fm = act.supportFragmentManager
                        if (fm.isDestroyed) return@post

                        fm.beginTransaction().apply {
                            // Animation trượt xuống khi hide
                            setCustomAnimations(0, R.anim.slide_out_bottom)
                            hide(fragment)
                            commitAllowingStateLoss()
                        }
                    }
                }
            }
        }

        fun destroyFilterFragment(activity: FragmentActivity?) {
            activity?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { act ->
                Handler(Looper.getMainLooper()).post {
                    try {
                        filterFragment?.let { fragment ->
                            val fm = act.supportFragmentManager
                            if (fragment.isAdded && !fm.isDestroyed) {
                                fm.beginTransaction().remove(fragment)
                                    .commitNowAllowingStateLoss()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        filterFragment = null
                    }
                }
            } ?: run {
                // Nếu activity null hoặc đã destroy → cleanup luôn
                filterFragment = null
            }
        }
    }
}