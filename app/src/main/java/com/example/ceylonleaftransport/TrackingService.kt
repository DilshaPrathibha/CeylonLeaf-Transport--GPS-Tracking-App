package com.example.ceylonleaftransport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TrackingService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }


    companion object {
        private const val CHANNEL_ID = "track"
        private const val NOTIF_ID = 1
        private const val BASE = "https://bethel-untattooed-madlyn.ngrok-free.app"
        private const val DRIVER = "DRIVER"
        private const val TAG = "Tracker"
    }

    private val http: OkHttpClient by lazy {
        val log = HttpLoggingInterceptor { m -> Log.d(TAG, m) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(log)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var request: LocationRequest
    private var callback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, makeNotification("Tracking active"))

        request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        // One warm-up GET so ngrok never shows a browser splash for our client
        warmUpNgrok()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (callback == null) {
            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    sendLocation(loc.latitude, loc.longitude)
                }
            }
            fused.requestLocationUpdates(request, callback as LocationCallback, mainLooper)
        }
        return START_STICKY
    }

    private fun warmUpNgrok() {
        val req = Request.Builder()
            .url("$BASE/driver-location.html")
            .header("ngrok-skip-browser-warning", "true")
            .header("User-Agent", "Android")
            .build()
        http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) { Log.w(TAG, "warmup fail", e) }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    private fun sendLocation(lat: Double, lng: Double) {
        val json = JSONObject(mapOf("lat" to lat, "lng" to lng)).toString()
        val body = json.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("$BASE/api/vehicles/$DRIVER/location")
            .post(body)
            .header("Content-Type", "application/json")
            .header("ngrok-skip-browser-warning", "true")
            .build()

        http.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "POST failed", e)
            }
            override fun onResponse(call: okhttp3.Call, res: okhttp3.Response) {
                Log.d(TAG, "POST ${res.code}")
                res.close()
            }
        })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Tracking", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun makeNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("CeylonLeaf tracking")
            .setContentText(text)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
