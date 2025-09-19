package com.example.ceylonleaftransport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var httpClient: OkHttpClient
    private var vehicleId: String = BuildConfig.TRACK_DEFAULT_VEHICLE
    private var token: String = ""
    private val baseUrl = BuildConfig.TRACK_BASE_URL
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                sendLocationUpdate(location.latitude, location.longitude)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        createNotificationChannel()
        
        // Initialize location client and HTTP client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Get vehicle ID and token from intent (use defaults if not provided)
        intent?.let {
            vehicleId = it.getStringExtra("vehicleId") ?: BuildConfig.TRACK_DEFAULT_VEHICLE
            token = it.getStringExtra("token") ?: ""
        }

        try {
            // Start foreground service with notification
            startForeground(NOTIFICATION_ID, buildNotification("Tracking active"))
            
            // Request location updates
            startLocationUpdates()
        } catch (e: SecurityException) {
            stopSelf()
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 4000)
            .setMinUpdateIntervalMillis(2000)
            .setMinUpdateDistanceMeters(5f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun sendLocationUpdate(lat: Double, lng: Double) {
        try {
            val url = "$baseUrl/api/vehicles/$vehicleId/location"
            val json = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .apply {
                    if (token.isNotBlank()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    // Log error and retry after delay
                    android.util.Log.e("TrackingService", "Failed to send location update", e)
                    // You could add retry logic here
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        android.util.Log.e("TrackingService", "Failed to send location update: ${response.code} - ${response.message}")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("TrackingService", "Error in sendLocationUpdate", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vehicle Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing vehicle tracking notification"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vehicle Tracking")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
        httpClient.dispatcher.executorService.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "track"
        private const val NOTIFICATION_ID = 1

        fun buildNotification(context: Context): Notification {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Vehicle Tracking")
                .setContentText("Vehicle tracking in progress...")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }
}
