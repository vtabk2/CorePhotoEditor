package com.core.adjust.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.core.adjust.R
import com.core.adjust.databinding.FragmentColorMixerBinding
import com.core.adjust.databinding.RowSliderHslBinding
import com.core.adjust.model.ColorChannel
import com.core.adjust.model.HslAdjustState
import com.core.adjust.model.HslTriple
import kotlinx.coroutines.launch
import java.util.EnumMap

class ColorMixerFragment : Fragment(R.layout.fragment_color_mixer) {
    private var _b: FragmentColorMixerBinding? = null
    private val b get() = _b!!
    private val vm: ColorMixerViewModel by viewModels()

    private val colorViews = EnumMap<ColorChannel, View>(ColorChannel::class.java)      // circle
    private val colorHolders = EnumMap<ColorChannel, FrameLayout>(ColorChannel::class.java) // frame

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentColorMixerBinding.bind(view)
        setupColorButtons(b)

        // Label 3 slider
        b.rowHue.tvLabel.text = "Hue"
        b.rowSat.tvLabel.text = "Saturation"
        b.rowLum.tvLabel.text = "Luminance"

        setupSlider(b.rowHue) { vm.updateHue(it) }
        setupSlider(b.rowSat) { vm.updateSat(it) }
        setupSlider(b.rowLum) { vm.updateLum(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collect { state ->
                val triple = state.map[state.selected] ?: HslTriple()
                setSlider(b.rowHue, triple.hue)
                setSlider(b.rowSat, triple.saturation)
                setSlider(b.rowLum, triple.luminance)

                // cập nhật ColorButtons (selected + dot)
                renderColorSelection(state)
            }
        }
    }

    private fun setupSlider(row: RowSliderHslBinding, onChange: (Int) -> Unit) {
        row.slider.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                val value = v.toInt()
                row.tvValue.text = value.toString()
                onChange(value)
            }
        }
    }

    private fun setSlider(row: RowSliderHslBinding, value: Int) {
        row.slider.value = value.toFloat()
        row.tvValue.text = value.toString()
    }

    override fun onDestroyView() {
        _b = null; super.onDestroyView()
    }

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

    private fun setupColorButtons(b: FragmentColorMixerBinding) {
        val frameSize = 48.dp(b.root.context)   // khung
        val circleSize = 30.dp(b.root.context)  // nút tròn thật

        colorMap.forEach { (channel, colorInt) ->
            val frame = FrameLayout(b.root.context).apply {
                layoutParams = LinearLayout.LayoutParams(frameSize, frameSize).apply {
                    marginStart = 6.dp(context)
                    marginEnd = 6.dp(context)
                }
                // rất quan trọng: không clip child khi scale
                clipChildren = false
                clipToPadding = false
                foreground = context.selectableRipple()
            }

            val circle = View(b.root.context).apply {
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorInt)
                    setStroke(1.dp(context), 0x55FFFFFF)
                }
                isClickable = false  // click gán vào frame
            }

            // dot báo đã chỉnh
            val dot = View(b.root.context).apply {
                id = R.id.dot_changed
                layoutParams = FrameLayout.LayoutParams(6.dp(context), 6.dp(context), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = 2.dp(context)
                }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                visibility = View.GONE
            }

            frame.addView(circle)
            frame.addView(dot)
            frame.setOnClickListener { vm.select(channel) }

            b.colorSelectorLayout.addView(frame)
            colorViews[channel] = circle
            colorHolders[channel] = frame
        }
    }

    private fun Int.dp(ctx: Context) = (this * ctx.resources.displayMetrics.density).toInt()

    private fun Context.selectableRipple(): Drawable? = TypedValue().let { tv ->
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        ResourcesCompat.getDrawable(resources, tv.resourceId, theme)
    }

    private fun renderColorSelection(state: HslAdjustState) {
        colorHolders.forEach { (channel, frame) ->
            val selected = channel == state.selected

            // scale container -> tránh cắt trên/dưới
            frame.animate()
                .scaleX(if (selected) 1.12f else 1f)
                .scaleY(if (selected) 1.12f else 1f)
                .setDuration(120)
                .start()

            // nhấn mạnh lớp nổi
            ViewCompat.setElevation(frame, if (selected) 6f else 0f)

            // stroke của vòng tròn
            (colorViews[channel]?.background as? GradientDrawable)?.apply {
                val strokeW = if (selected) 2.dp(frame.context) else 1.dp(frame.context)
                val strokeColor = if (selected) Color.WHITE else 0x55FFFFFF
                setStroke(strokeW, strokeColor)
            }

            // dot “đã chỉnh”
            val changed = state.map[channel]?.let { it.hue != 0 || it.saturation != 0 || it.luminance != 0 } == true
            frame.findViewById<View>(R.id.dot_changed)?.isVisible = changed
        }
    }
}