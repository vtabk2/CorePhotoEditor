package com.example.photoeditor.utils

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import com.example.photoeditor.databinding.DialogDeniedPermissionsBinding
import com.example.photoeditor.databinding.DialogWriteStorageBinding

object DialogUtils {
    fun initDeniedPermissionsDialog(context: Context, callback: (granted: Boolean) -> Unit): AlertDialog {
        val builder = AlertDialog.Builder(context)
        val binding = DialogDeniedPermissionsBinding.inflate(LayoutInflater.from(context), null, false)
        builder.setView(binding.root)
        val deniedPermissionsDialog = builder.create()
        deniedPermissionsDialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        binding.tvDeniedPermissionsClose.setOnClickListener {
            deniedPermissionsDialog.dismiss()
            callback(false)
        }
        binding.tvDeniedPermissionsSettings.setOnClickListener {
            deniedPermissionsDialog.dismiss()
            callback(true)
        }
        return deniedPermissionsDialog
    }

    fun initWriteStorageDialog(context: Context, callback: (granted: Boolean) -> Unit): AlertDialog {
        val builder = AlertDialog.Builder(context)
        val binding = DialogWriteStorageBinding.inflate(LayoutInflater.from(context), null, false)
        builder.setView(binding.root)
        val writeStorageDialog = builder.create()
        writeStorageDialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        binding.tvDeniedPermissionsClose.setOnClickListener {
            writeStorageDialog.dismiss()
            callback(false)
        }
        binding.tvDeniedPermissionsSettings.setOnClickListener {
            writeStorageDialog.dismiss()
            callback(true)
        }
        return writeStorageDialog
    }
}