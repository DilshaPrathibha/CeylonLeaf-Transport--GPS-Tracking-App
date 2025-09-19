package com.example.ceylonleaftransport

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ceylonleaftransport.ui.theme.CeylonLeafTransportTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private var isTracking by mutableStateOf(false)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TrackingService.LocalBinder
            isTracking = true
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isTracking = false
            Log.d(TAG, "Service disconnected")
        }
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE
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
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startTrackingService()
            promptForBatteryOptimization()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CeylonLeafTransportTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Status Text
                        Text(
                            text = if (isTracking) "Status: Running" else "Status: Stopped",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 32.dp)
                        )
                        
                        // Buttons
                        Button(
                            onClick = { 
                                if (!isTracking) startTracking() 
                                else stopTracking()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        ) {
                            Text(if (isTracking) "STOP" else "START", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    private fun startTracking() {
        if (checkLocationPermission()) {
            startTrackingService()
            promptForBatteryOptimization()
        } else {
            requestPermissions()
        }
    }

    private fun startTrackingService() {
        try {
            val serviceIntent = Intent(this, TrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            // Bind to the service
            bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            isTracking = true
            Log.d(TAG, "Tracking service started and bound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tracking service", e)
        }
    }

    private fun stopTracking() {
        try {
            unbindService(serviceConnection)
            val serviceIntent = Intent(this, TrackingService::class.java)
            stopService(serviceIntent)
            isTracking = false
            Log.d(TAG, "Tracking service stopped and unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop tracking service", e)
        }
    }


    private fun checkLocationPermission(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            startTrackingService()
            promptForBatteryOptimization()
        }
    }

    private fun promptForBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show battery optimization settings", e)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    CeylonLeafTransportTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            TrackingScreen(
                onStartTracking = { _, _ -> },
                onStopTracking = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onStartTracking: (String, String) -> Unit,
    onStopTracking: () -> Unit
) {
    var vehicleId by remember { mutableStateOf(TextFieldValue("DRIVER")) }
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