package com.kema.phenoxrush

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private val running = AtomicBoolean(false)
    private var sessionId = 0L
    private var startedAt = 0L
    private var last: Location? = null
    private var distance = 0.0
    private var lastPersist = 0L
    private val dao by lazy { TrackDatabase.get(this).dao() }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                if (!location.hasAccuracy() || location.accuracy > 50f) return@forEach
                val previous = last
                if (previous != null) {
                    val segment = previous.distanceTo(location).toDouble()
                    val seconds = (location.time - previous.time) / 1000.0
                    if (seconds > 0 && seconds <= 60 && segment >= 1.5 && segment / seconds <= 4.5) {
                        distance += segment
                    }
                }
                last = location
                scope.launch {
                    dao.addPoint(TrackPoint(sessionId=sessionId, latitude=location.latitude,
                        longitude=location.longitude, altitudeMeters=if (location.hasAltitude()) location.altitude else null,
                        accuracyMeters=location.accuracy, timestamp=location.time))
                    val now = System.currentTimeMillis()
                    if (now - lastPersist >= 15000) {
                        dao.session(sessionId)?.let { dao.updateSession(it.copy(distanceMeters=distance, durationMillis=now-startedAt)) }
                        lastPersist = now
                    }
                    updateNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "PHENOX RUSH tracking", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (!running.compareAndSet(false, true)) return
        startedAt = System.currentTimeMillis()
        val notification = notification("GPS tracking active")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
        scope.launch {
            sessionId = dao.addSession(WalkSession(startedAt=startedAt))
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateDistanceMeters(3f).setWaitForAccurateLocation(false).build()
            try { client.requestLocationUpdates(request, callback, mainLooper) }
            catch (_: SecurityException) { stopSelf() }
        }
    }

    private fun stopTracking() {
        if (!running.getAndSet(false)) { stopSelf(); return }
        client.removeLocationUpdates(callback)
        scope.launch {
            dao.session(sessionId)?.let { dao.updateSession(it.copy(endedAt=System.currentTimeMillis(),
                distanceMeters=distance, durationMillis=System.currentTimeMillis()-startedAt)) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PHENOX RUSH").setContentText(text).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()

    private fun updateNotification() {
        val km = distance / 1000.0
        val elapsed = (System.currentTimeMillis()-startedAt)/1000
        val content = String.format(java.util.Locale.US, "%.2f km • %02d:%02d:%02d", km, elapsed/3600, (elapsed%3600)/60, elapsed%60)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { client.removeLocationUpdates(callback); scope.cancel(); super.onDestroy() }
    companion object {
        const val ACTION_START = "com.kema.phenoxrush.START"
        const val ACTION_STOP = "com.kema.phenoxrush.STOP"
        const val CHANNEL = "phenox_tracking"
        const val NOTIFICATION_ID = 1618
    }
}
