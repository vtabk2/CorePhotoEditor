package com.example.photoeditor.ui.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.core.adjust.ui.ShareAdjustViewModel
import com.core.adjust.ui.filter.FilterFragment
import com.core.adjust.utils.FilterSuggestionUtils
import com.example.photoeditor.R
import com.example.photoeditor.databinding.ActivityMainBinding
import com.example.photoeditor.utils.LoadUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val shareAdjustViewModel: ShareAdjustViewModel by viewModels {
        ShareAdjustViewModel.Factory(this, lifecycleScope)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { onImagePicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickImage.setOnClickListener {

            checkPermission(callback = { granted ->
                if (granted) {
                    pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            })
        }

        shareAdjustViewModel.previewBitmap.observe(this) { bmp ->
            binding.imgAdjusted.setImageBitmap(bmp)
        }

        lifecycleScope.launch {
            shareAdjustViewModel.closeFlow.collect { close ->
                Log.d("TAG5", "MainActivity_onCreate: close = $close")
                FilterFragment.hideFilterFragment(this@MainActivity)
            }
        }

        FilterFragment.showFilterFragment(this, R.id.flBottom)
    }

    override fun goToOtherHasWriteStoragePermission() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun onImagePicked(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Main) {
            val src = withContext(Dispatchers.IO) {
                LoadUtils.loadBitmapForEditingWithMemoryClass(this@MainActivity, uri, freeStyle = false)
            } ?: return@launch

            binding.imgOriginal.setImageBitmap(src)

            val suggested = withContext(Dispatchers.Default) {
                FilterSuggestionUtils.suggestGroups(src, uri.path)
            }

            Log.d("TAG5", "Gợi ý filter: $suggested")

            // 🎨 hiển thị gợi ý trên UI (vd TextView)
            binding.tvSuggestion.text = "Đề xuất: ${suggested.joinToString(", ")}"

            Log.d("TAG5", "MainActivity_onImagePicked: " + shareAdjustViewModel.params)
            shareAdjustViewModel.setOriginal(src, requiredCreateThumb = true)
            shareAdjustViewModel.applyAdjust()
        }
    }

    override fun onDestroy() {
        FilterFragment.destroyFilterFragment(activity = this)
        super.onDestroy()
    }
}