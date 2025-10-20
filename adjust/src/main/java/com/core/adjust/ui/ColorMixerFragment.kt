package com.core.adjust.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.core.adjust.R
import com.core.adjust.databinding.FragmentColorMixerBinding
import com.core.adjust.databinding.RowSliderHslBinding
import com.core.adjust.model.ColorChannel
import com.core.adjust.model.ColorPillUi
import com.core.adjust.model.HslAdjustState
import com.core.adjust.model.HslTriple
import kotlinx.coroutines.launch

class ColorMixerFragment : Fragment(R.layout.fragment_color_mixer) {

    private var _binding: FragmentColorMixerBinding? = null
    private val binding get() = _binding!!

    private val vm: ColorMixerViewModel by viewModels()

    private val colorMap by lazy {
        mapOf(
            ColorChannel.RED to 0xFFE53935.toInt(),
            ColorChannel.ORANGE to 0xFFFF8A65.toInt(),
            ColorChannel.YELLOW to 0xFFFFEE58.toInt(),
            ColorChannel.GREEN to 0xFF43A047.toInt(),
            ColorChannel.AQUA to 0xFF26C6DA.toInt(),
            ColorChannel.BLUE to 0xFF1E88E5.toInt(),
            ColorChannel.PURPLE to 0xFF8E24AA.toInt(),
            ColorChannel.MAGENTA to 0xFFD81B60.toInt()
        )
    }

    private lateinit var adapter: ColorPillAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentColorMixerBinding.bind(view)

        // Label 3 hàng
        binding.rowHue.tvLabel.text = "Hue"
        binding.rowSat.tvLabel.text = "Saturation"
        binding.rowLum.tvLabel.text = "Luminance"

        // Slider listeners (snap-to-zero + haptic)
        setupSlider(binding.rowHue) { vm.updateHue(it) }
        setupSlider(binding.rowSat) { vm.updateSat(it) }
        setupSlider(binding.rowLum) { vm.updateLum(it) }

        // Reset ALL
        binding.btnResetAll.setOnClickListener { vm.resetAll(); lightHaptic() }

        // Targeted toggle
        binding.btnTargeted.setOnClickListener { vm.toggleTargeted(); lightHaptic() }

        // RecyclerView 8 màu
        adapter = ColorPillAdapter { channel -> vm.select(channel); lightHaptic() }
        binding.rcvColors.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.rcvColors.adapter = adapter

        // Press & hold preview (giữ trên preview ImageView của bạn)
        // VD: val preview = requireActivity().findViewById<ImageView>(R.id.ivPreview)
        // preview.setOnTouchListener { _, ev -> ... vm.setPreviewHeld(true/false) }

        // Collect state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    // Update slider values theo kênh đang chọn
                    val triple = state.map[state.selected] ?: HslTriple()
                    setSlider(binding.rowHue, triple.hue)
                    setSlider(binding.rowSat, triple.saturation)
                    setSlider(binding.rowLum, triple.luminance)

                    // Update dãy pills
                    val list = ColorChannel.entries.map { ch ->
                        ColorPillUi(ch, colorMap[ch]!!, selected = (ch == state.selected))
                    }
                    adapter.submitList(list)

                    // Render
                    renderPreview(state)
                }
            }
        }
    }

    private fun setupSlider(row: RowSliderHslBinding, onChange: (Int) -> Unit) {
        row.slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val v = value.toInt()
                row.tvValue.text = v.toString()

                // Snap-to-zero (±2)
                val snapped = when {
                    v in -2..2 -> {
                        if (v != 0) lightHaptic()
                        row.slider.value = 0f
                        0
                    }

                    else -> v
                }
                onChange(snapped)
            }
        }
        // Double-tap label -> reset tham số hiện tại
        row.tvLabel.setOnClickListener(object : View.OnClickListener {
            private var last = 0L
            override fun onClick(v: View?) {
                val now = SystemClock.elapsedRealtime()
                if (now - last < 250) {
                    row.slider.value = 0f
                    row.tvValue.text = "0"
                    onChange(0)
                    lightHaptic()
                }
                last = now
            }
        })
    }

    private fun setSlider(row: RowSliderHslBinding, value: Int) {
        if (row.slider.value.toInt() != value) row.slider.value = value.toFloat()
        row.tvValue.text = value.toString()
    }

    private fun renderPreview(state: HslAdjustState) {
        val previewHeld = state.isPreviewHeld
        // TODO: nối với engine của bạn:
        // if (previewHeld) show original
        // else applyHsl(state.map) -> cập nhật lên ImageView
        // vm/state.map cung cấp hue[8], sat[8], lum[8]
    }

    private fun lightHaptic() =
        runCatching { binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
