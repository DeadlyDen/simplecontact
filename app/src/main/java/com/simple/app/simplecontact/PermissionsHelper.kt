package com.simple.app.simplecontact

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsHelper(private val activity: Activity) {

    fun requestPermissions(): Boolean {
        return if (hasReadContactPermission() && hasWriteContactPermission()) {
            true
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                PERMISSION_REQUEST_CODE
            )
            false
        }
    }

    private fun hasReadContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWriteContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun resultGranted(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (permissions.size != grantResults.size)
            return false
        if (grantResults.isEmpty())
            return false
        if (requestCode != PERMISSION_REQUEST_CODE)
            return false

        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 10
    }
}