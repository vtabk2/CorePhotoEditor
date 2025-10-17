package com.example.photoeditor.ui.activity

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.core.adjust.AdjustManager
import com.example.photoeditor.databinding.ActivityMainBinding
import com.example.photoeditor.utils.LoadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var adjustManager: AdjustManager

    // Photo Picker launcher (Android 13+ compatible)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let { onImagePicked(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adjustManager = AdjustManager(lifecycleScope)

        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Giả sử bạn đã findViewById hoặc ViewBinding với include_bottom_panel
        val controller = BottomPanelController(
            context = this,
            rcvTabs = binding.bottomPanel.rcvAdjustTabs,
            rcvSliders = binding.bottomPanel.rcvSliders
        ) { slider ->
            // ✅ Cập nhật params dựa vào id slider
            when (slider.key) {
                "exposure" -> adjustManager.params.exposure = slider.value / 100f
                "brightness" -> adjustManager.params.brightness = slider.value / 100f
                "contrast" -> adjustManager.params.contrast = slider.value / 100f
                "highlights" -> adjustManager.params.highlights = slider.value / 100f
                "shadows" -> adjustManager.params.shadows = slider.value / 100f
                "whites" -> adjustManager.params.whites = slider.value / 100f
                "blacks" -> adjustManager.params.blacks = slider.value / 100f
                //
                "temperature" -> adjustManager.params.temperature = slider.value / 100f
                "tint" -> adjustManager.params.tint = slider.value / 100f
                "vibrance" -> adjustManager.params.vibrance = slider.value / 100f
                "saturation" -> adjustManager.params.saturation = slider.value / 100f
                //
                "texture" -> adjustManager.params.texture = slider.value / 100f
                "clarity" -> adjustManager.params.clarity = slider.value / 100f
                "dehaze" -> adjustManager.params.dehaze = slider.value / 100f

            }

            adjustManager.applyAdjust { updated ->
                binding.imgAdjusted.setImageBitmap(updated)
            }
        }
        controller.bind()

    }

    private fun onImagePicked(uri: Uri) {
        // Dùng chung 1 hàm load bitmap theo memoryClass
        lifecycleScope.launch(Dispatchers.Main) {
            val src = withContext(Dispatchers.IO) {
                LoadUtils.loadBitmapForEditingWithMemoryClass(this@MainActivity, uri, freeStyle = false)
            } ?: return@launch

            adjustManager.setOriginalBitmap(src)

            binding.imgOriginal.setImageBitmap(src)
            binding.imgAdjusted.setImageBitmap(adjustManager.getPreviewBitmap())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adjustManager.release()
    }
}