package com.core.adjust.ui.hsl

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.core.adjust.R
import com.core.adjust.databinding.FFragmentColorMixerBinding
import com.core.adjust.databinding.FRowSliderHslBinding
import com.core.adjust.model.ColorChannel
import com.core.adjust.model.HslAdjustState
import com.core.adjust.model.HslTriple
import com.core.adjust.ui.ShareAdjustViewModel
import kotlinx.coroutines.launch
import java.util.EnumMap

class ColorMixerFragment : Fragment(R.layout.f_fragment_color_mixer) {
    private var _bindingView: FFragmentColorMixerBinding? = null
    private val bindingView get() = _bindingView!!

    private val colorMixerViewModel: ColorMixerViewModel by viewModels()
    private val shareAdjustViewModel: ShareAdjustViewModel by activityViewModels()

    private val colorViews = EnumMap<ColorChannel, View>(ColorChannel::class.java)
    private val colorHolders = EnumMap<ColorChannel, FrameLayout>(ColorChannel::class.java)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _bindingView = FFragmentColorMixerBinding.bind(view)
        setupColorButtons(bindingView)

        // Label 3 slider
        bindingView.rowHue.tvLabel.text = "Hue"
        bindingView.rowSat.tvLabel.text = "Saturation"
        bindingView.rowLum.tvLabel.text = "Luminance"

        // Căn đều chiều rộng label theo nhãn dài nhất
        bindingView.root.post {
            val labels = listOf(bindingView.rowHue.tvLabel, bindingView.rowSat.tvLabel, bindingView.rowLum.tvLabel)

            // Lấy kích thước text dài nhất
            val maxWidth = labels.maxOf { label ->
                label.paint.measureText(label.text.toString())
            }.toInt() + 8.dp(labels.first().context)

            // Áp dụng cùng width + buộc layout lại
            labels.forEach { label ->
                val lp = label.layoutParams
                lp.width = maxWidth
                label.layoutParams = lp
                label.requestLayout()
            }
        }

        // Thiết lập slider
        setupSlider(bindingView.rowHue) { colorMixerViewModel.updateHue(it) }
        setupSlider(bindingView.rowSat) { colorMixerViewModel.updateSat(it) }
        setupSlider(bindingView.rowLum) { colorMixerViewModel.updateLum(it) }

        // Lắng nghe thay đổi từ ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            colorMixerViewModel.state.collect { state ->
                val triple = state.map[state.selected] ?: HslTriple()
                setSlider(bindingView.rowHue, triple.hue)
                setSlider(bindingView.rowSat, triple.saturation)
                setSlider(bindingView.rowLum, triple.luminance)
                renderColorSelection(state)
            }
        }

        // Reset
        viewLifecycleOwner.lifecycleScope.launch {
            shareAdjustViewModel.resetFlow.collect { mode ->
                when (mode) {
                    ShareAdjustViewModel.HSL -> {
                        colorMixerViewModel.resetAll()
                    }
                }
            }
        }
    }

    private fun setupSlider(row: FRowSliderHslBinding, onChange: (Int) -> Unit) {
        row.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val value = progress - 100
                    row.tvValue.text = value.toString()
                    onChange(value)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val current = colorMixerViewModel.state.value
                val selectedChannel = current.selected.ordinal
                val triple = current.map[current.selected] ?: HslTriple()

                shareAdjustViewModel.updateHsl(
                    selectedChannel,
                    triple.hue,
                    triple.saturation,
                    triple.luminance
                )
            }
        })
    }

    private fun setSlider(row: FRowSliderHslBinding, value: Int) {
        val progress = value + 100
        row.seekBar.progress = progress
        row.tvValue.text = value.toString()
    }

    override fun onDestroyView() {
        _bindingView = null
        super.onDestroyView()
    }

    // --- Color Buttons ---

    private val colorMap = mapOf(
        ColorChannel.RED to 0xFFE53935.toInt(),
        ColorChannel.ORANGE to 0xFFFF8A65.toInt(),
        ColorChannel.YELLOW to 0xFFFFEE58.toInt(),
        ColorChannel.GREEN to 0xFF43A047.toInt(),
        ColorChannel.AQUA to 0xFF26C6DA.toInt(),
        ColorChannel.BLUE to 0xFF1E88E5.toInt(),
        ColorChannel.PURPLE to 0xFF8E24AA.toInt(),
        ColorChannel.MAGENTA to 0xFFD81B60.toInt()
    )

    private fun setupColorButtons(b: FFragmentColorMixerBinding) {
        val context = b.root.context
        val frameSize = 32.dp(context)
        val circleSize = 22.dp(context)

        colorMap.forEach { (channel, colorInt) ->
            val frame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(frameSize, frameSize).apply {
                    marginStart = 4.dp(context)
                    marginEnd = 4.dp(context)
                }
                clipChildren = false
                clipToPadding = false
                foreground = context.selectableRipple()
            }

            val circle = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorInt)
                    setStroke(1.dp(context), 0x55FFFFFF)
                }
                isClickable = false
            }

            val dot = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (circleSize * 0.18f).toInt(),
                    (circleSize * 0.18f).toInt(),
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = (circleSize * 0.08f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                isVisible = false
            }

            frame.addView(circle)
            frame.addView(dot)
            frame.setOnClickListener { colorMixerViewModel.select(channel) }

            // Lưu dot vào tag để không dùng findViewById
            frame.setTag(R.id.dot_changed, dot)

            b.colorSelectorLayout.addView(frame)
            colorViews[channel] = circle
            colorHolders[channel] = frame
        }
    }

    // --- Hiệu ứng chọn màu ---

    private fun renderColorSelection(state: HslAdjustState) {
        colorHolders.forEach { (channel, frame) ->
            val selected = channel == state.selected
            val circle = colorViews[channel]
            val bg = circle?.background as? GradientDrawable

            // Animation scale nhẹ
            frame.animate()
                .scaleX(if (selected) 1.08f else 1f)
                .scaleY(if (selected) 1.08f else 1f)
                .setDuration(140)
                .start()

            ViewCompat.setElevation(frame, if (selected) 6f else 0f)

            // Glow viền sáng
            if (selected) {
                bg?.setStroke(3.dp(frame.context), Color.WHITE)
                bg?.setColorFilter(
                    Color.argb(60, 255, 255, 255),
                    PorterDuff.Mode.SRC_ATOP
                )
            } else {
                bg?.setStroke(1.dp(frame.context), 0x55FFFFFF)
                bg?.clearColorFilter()
            }

            // Hiện dot nếu đã chỉnh
            val changed = state.map[channel]?.let {
                it.hue != 0 || it.saturation != 0 || it.luminance != 0
            } == true
            val dotView = frame.getTag(R.id.dot_changed) as? View
            dotView?.isVisible = changed
        }
    }

    // --- Extension tiện ích ---
    private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()

    private fun Context.selectableRipple(): Drawable? = TypedValue().let { tv ->
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        ResourcesCompat.getDrawable(resources, tv.resourceId, theme)
    }
}