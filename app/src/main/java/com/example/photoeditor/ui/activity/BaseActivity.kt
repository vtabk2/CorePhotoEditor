package com.example.photoeditor.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.photoeditor.utils.DialogUtils
import com.example.photoeditor.utils.extensions.PERMISSION_WRITE_STORAGE
import com.example.photoeditor.utils.extensions.dialogLayout
import com.example.photoeditor.utils.extensions.hasPermission
import com.example.photoeditor.utils.extensions.isTiramisuPlus
import com.example.photoeditor.utils.extensions.isUpSideDownCakePlus
import com.example.photoeditor.utils.result.OpenAppSystemSettingsContract
import com.example.photoeditor.utils.result.RequestPermissionContract

abstract class BaseActivity : AppCompatActivity() {
    private var deniedPermissionsDialog: AlertDialog? = null
    private var writeStoragePermissionsDialog: AlertDialog? = null
    private var openAppSystemSettingsContract = registerForActivityResult(OpenAppSystemSettingsContract()) { permissionId ->
        if (hasPermission(permissionId, hasFull = false)) {
            when (permissionId) {
                PERMISSION_WRITE_STORAGE -> goToOtherHasWriteStoragePermission()
                else -> goToOtherHasPermission()
            }
        }
    }

    // Register ActivityResult handler
    private val requestWriteStoragePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        // Handle permission requests results
        // See the permission example in the Android platform samples: https://github.com/android/platform-samples
        if (!results.isNullOrEmpty()) {
            val readMediaImages = if (results.containsKey(Manifest.permission.READ_MEDIA_IMAGES)) {
                results[Manifest.permission.READ_MEDIA_IMAGES] ?: false
            } else {
                false
            }
            val readMediaVisualUserSelected = if (results.containsKey(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
                results[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] ?: false
            } else {
                false
            }
            val readExternalStorage = if (results.containsKey(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                results[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            } else {
                false
            }
            if (readMediaImages || readMediaVisualUserSelected || readExternalStorage) {
                goToOtherHasWriteStoragePermission()
            } else {
                showDeniedPermissionsDialog(PERMISSION_WRITE_STORAGE)
            }
        }
    }

    private val requestPermissions = registerForActivityResult(RequestPermissionContract()) { data ->
        if (data.second) {
            goToOtherHasPermission()
        } else {
            showDeniedPermissionsDialog(data.first)
        }
    }

    fun checkPermission(permissionId: Int = PERMISSION_WRITE_STORAGE, hasFull: Boolean = true, callback: (granted: Boolean) -> Unit) {
        if (hasPermission(permissionId, hasFull = hasFull)) {
            callback.invoke(true)
        } else {
            //
            when (permissionId) {
                PERMISSION_WRITE_STORAGE -> {
                    // Permission request logic
                    if (isUpSideDownCakePlus()) {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) -> {
                                callback.invoke(true)
                            }

                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> {
                                writeStoragePermissionsDialog?.show()
                                dialogLayout(writeStoragePermissionsDialog)
                            }

                            else -> {
                                requestWriteStoragePermissions.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
                            }
                        }
                    } else if (isTiramisuPlus()) {
                        requestWriteStoragePermissions.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                    } else {
                        requestWriteStoragePermissions.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                }

                else -> {
                    requestPermissions.launch(permissionId)
                }
            }
            //
            callback.invoke(false)
        }
    }

    private fun showDeniedPermissionsDialog(permissionId: Int) {
        deniedPermissionsDialog?.dismiss()
        deniedPermissionsDialog = DialogUtils.initDeniedPermissionsDialog(this) { granted ->
            if (granted) {
                openAppSystemSettingsContract.launch(permissionId)
            }
        }
        deniedPermissionsDialog?.show()
        dialogLayout(deniedPermissionsDialog)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        writeStoragePermissionsDialog = DialogUtils.initWriteStorageDialog(this) { granted ->
            if (granted) {
                openAppSystemSettingsContract.launch(PERMISSION_WRITE_STORAGE)
            } else {
                requestWriteStoragePermissions.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
            }
        }
    }

    open fun goToOtherHasWriteStoragePermission() {}

    open fun goToOtherHasPermission() {}
}