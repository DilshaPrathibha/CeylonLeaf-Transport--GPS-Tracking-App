package com.example.ceylonleaftransport

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Ask for multiple permissions, then start service
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> actuallyStart() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.statusText)
        val start  = findViewById<Button>(R.id.startBtn)
        val stop   = findViewById<Button>(R.id.stopBtn)

        start.setOnClickListener {
            status.text = "Status: Starting…"
            requestPermsThenStart()
        }
        stop.setOnClickListener {
            stopService(Intent(this, TrackingService::class.java))
            status.text = "Status: Stopped"
        }
    }

    private fun requestPermsThenStart() {
        val need = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = need.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            actuallyStart()
        } else {
            permLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun actuallyStart() {
        val i = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)

        // Ask to disable battery optimization (for reliability)
        val pm = getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(packageName) ?: true
        if (!ignoring) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
}
