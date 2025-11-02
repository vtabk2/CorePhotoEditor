package com.example.photoeditor.ui.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.core.adjust.ui.AdjustViewModel
import com.core.adjust.ui.ColorMixerFragment
import com.core.adjust.ui.filter.FilterFragment
import com.example.photoeditor.R
import com.example.photoeditor.databinding.ActivityMainBinding
import com.example.photoeditor.utils.LoadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vm: AdjustViewModel by viewModels {
        AdjustViewModel.Factory(lifecycleScope)
    }

    // Photo Picker launcher (Android 13+ compatible)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { onImagePicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Giả sử bạn đã findViewById hoặc ViewBinding với include_bottom_panel
        val controller = BottomPanelController(
            rcvTabs = binding.bottomPanel.rcvAdjustTabs,
            rcvSliders = binding.bottomPanel.rcvSliders,
            btnReset = binding.bottomPanel.btnReset,
            adjustManager = vm.manager,
            onSliderChanged = { slider ->
                AdjustRepository.map(adjustSlider = slider, adjustParams = vm.params)
                vm.applyAdjust()
            },
            onResetTab = { tabKey ->
                if (tabKey == "hsl") {
                    vm.resetHsl()
                }
                vm.applyAdjust()
            },
            onShowHsl = {
                binding.bottomPanel.hslContainer.isVisible = true
                showHslUiIfNeeded()
            },
            onHideHsl = {
                binding.bottomPanel.hslContainer.isVisible = false
            })
        controller.bind()

        vm.previewBitmap.observe(this) { bmp ->
            binding.imgAdjusted.setImageBitmap(bmp)
        }

        FilterFragment.showFilterFragment(this, R.id.flBottom)
    }

    private fun showHslUiIfNeeded() {
        if (supportFragmentManager.findFragmentByTag("HSL") == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.hslContainer, ColorMixerFragment(), "HSL")
                .commitAllowingStateLoss()
        }
    }

    private fun onImagePicked(uri: Uri) {
        // Dùng chung 1 hàm load bitmap theo memoryClass
        lifecycleScope.launch(Dispatchers.Main) {
            val src = withContext(Dispatchers.IO) {
                LoadUtils.loadBitmapForEditingWithMemoryClass(this@MainActivity, uri, freeStyle = false)
            } ?: return@launch

            binding.imgOriginal.setImageBitmap(src)

            Log.d("TAG5", "MainActivity_onImagePicked: " + vm.params)
            vm.setOriginal(src)
            vm.applyAdjust()
        }
    }

    override fun onDestroy() {
        FilterFragment.destroyFilterFragment(activity = this)
        super.onDestroy()
    }
}