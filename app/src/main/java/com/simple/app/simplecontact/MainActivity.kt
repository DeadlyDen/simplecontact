package com.simple.app.simplecontact

import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private val permissionHelper = PermissionsHelper(this@MainActivity)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(
            SimpleContactReceiver(),
            IntentFilter(Intent.ACTION_BOOT_COMPLETED)
        )

        val hasPermissions = permissionHelper.requestPermissions()
        if (hasPermissions) {
            hideActivity()
        }
    }

    private fun hideActivity() {
        // i don't add coroutines in this app, so use handler
        Handler(Looper.getMainLooper()).postDelayed({
                                                        finish()
                                                    }, 300)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = permissionHelper.resultGranted(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults
        )
        if (granted) {
            hideActivity()
        } else {
            Toast.makeText(this@MainActivity, "Permissions denied", Toast.LENGTH_SHORT).show()
        }
    }
}