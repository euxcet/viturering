package com.euxcet.viturering.utils

import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ComponentActivity

class Permission {
    companion object {
        fun requestPermissions(activity: ComponentActivity, permissions: List<String>) {
            permissions.filter {
                ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }.apply {
                if (this.isNotEmpty()) {
                    ActivityCompat.requestPermissions(activity, this.toTypedArray(), 1001)
                }
            }
        }
    }
}