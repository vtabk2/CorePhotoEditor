package com.example.photoeditor.ui.activity

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.photoeditor.databinding.ActivityMainBinding
import com.example.photoeditor.utils.LoadUtils
import com.example.photoeditor.utils.extensions.BitmapExt.ensureMutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Photo Picker launcher (Android 13+ compatible)
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
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
            context = this,
            rcvTabs = binding.bottomPanel.rcvAdjustTabs,
            rcvSliders = binding.bottomPanel.rcvSliders
        ) { slider ->
            // TODO: callback khi người dùng kéo slider
            // Map key -> tham số native và gọi preview:
            // when(slider.key) { "exposure" -> params.exposureEV = slider.value/100f, ... }
            // ImageAdjust.applyInPlace(workBitmap, params); binding.imgAdjusted.invalidate()
        }
        controller.bind()

    }

    private fun onImagePicked(uri: Uri) {
        // Dùng chung 1 hàm load bitmap theo memoryClass
        lifecycleScope.launch(Dispatchers.Main) {
            val src = withContext(Dispatchers.IO) {
                LoadUtils.loadBitmapForEditingWithMemoryClass(this@MainActivity, uri, freeStyle = false)
            } ?: return@launch

            // 1) Hiển thị ảnh gốc (trái)
            binding.imgOriginal.setImageBitmap(src)

            // 2) Chuẩn bị bitmap để Adjust (phải) — tạo bản sao mutable để chỉnh in-place
            val work = src.ensureMutable() // tránh sửa trực tiếp ảnh gốc
            binding.imgAdjusted.setImageBitmap(work)

            // 🔜 Khi bạn có params: ImageAdjust.applyInPlace(work, params)
            // binding.imgAdjusted.invalidate()
        }
    }
}