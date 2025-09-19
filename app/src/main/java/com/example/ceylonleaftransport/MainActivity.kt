package com.example.ceylonleaftransport

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ceylonleaftransport.ui.theme.CeylonLeafTransportTheme

class MainActivity : ComponentActivity() {
    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // If all permissions are granted, we don't need to do anything here
        // as the tracking will start when the user clicks the button
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CeylonLeafTransportTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TrackingScreen(
                        onStartTracking = { vehicleId, token ->
                            startTracking(vehicleId, token)
                        },
                        onStopTracking = {
                            stopTracking()
                        }
                    )
                }
            }
        }

        // Request permissions when activity is created
        requestPermissions()
    }

    private fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startTracking(vehicleId: String, token: String) {
        if (checkLocationPermission()) {
            // Start the tracking service
            val serviceIntent = Intent(this, TrackingService::class.java).apply {
                putExtra("vehicleId", vehicleId.ifEmpty { BuildConfig.TRACK_DEFAULT_VEHICLE })
                putExtra("token", token)
            }
            
            // Open the map activity
            val mapIntent = Intent(this, MapActivity::class.java).apply {
                putExtra("vehicleId", vehicleId.ifEmpty { BuildConfig.TRACK_DEFAULT_VEHICLE })
            }
            
            // Start both the service and the map activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            startActivity(mapIntent)
            promptForBatteryOptimization()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun promptForBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("For reliable tracking, please disable battery optimizations for this app.\n\n" +
                    "Note: Some devices (Xiaomi/Oppo/Vivo/others) may require additional manual settings in their battery optimization settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton("Later", null)
            .show()
    }
    
    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun stopTracking() {
        val intent = Intent(this, TrackingService::class.java)
        stopService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onStartTracking: (String, String) -> Unit,
    onStopTracking: () -> Unit
) {
    var vehicleId by remember { mutableStateOf(TextFieldValue(BuildConfig.TRACK_DEFAULT_VEHICLE)) }
    var token by remember { mutableStateOf(TextFieldValue("")) }
    var isTracking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Vehicle Tracking",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = vehicleId,
            onValueChange = { vehicleId = it },
            label = { Text("Vehicle ID") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )
        
        if (isTracking) {
            Button(
                onClick = {
                    onStopTracking()
                    isTracking = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Stop Tracking")
            }
        } else {
            Button(
                onClick = {
                    if (vehicleId.text.isNotBlank()) {
                        onStartTracking(vehicleId.text, token.text)
                        isTracking = true
                    } else {
                        // Show error if vehicle ID is empty
                        android.widget.Toast.makeText(
                            context,
                            "Please enter a vehicle ID",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Start Tracking")
            }
        }
        
        if (isTracking) {
            Text(
                text = "Tracking active for ${vehicleId.text}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TrackingScreenPreview() {
    CeylonLeafTransportTheme {
        TrackingScreen(
            onStartTracking = { _, _ -> },
            onStopTracking = {}
        )
    }
}