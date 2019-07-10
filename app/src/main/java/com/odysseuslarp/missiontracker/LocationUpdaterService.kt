package com.odysseuslarp.missiontracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.google.firebase.firestore.GeoPoint

private const val CHANNEL_ID = "locUpdSrv"

class LocationUpdaterService : Service() {
    private val firebaseIdObserver = Observer<String> {
        LocationUpdater.setFirebaseId(it)
    }

    private val locationObserver = Observer<GeoPoint> {
        LocationUpdater.location = it
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        startForeground(R.id.trackingActiveNotification, buildNotification())
        if (BuildConfig.TEAM_MEMBER_APP) {
            FirebaseIdLiveData.observeForever(firebaseIdObserver)
        }
        lastLocation?.observeForever(locationObserver)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.TEAM_MEMBER_APP) {
            FirebaseIdLiveData.removeObserver(firebaseIdObserver)
        }
        lastLocation?.removeObserver(locationObserver)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
                if (getNotificationChannel(CHANNEL_ID) == null) {
                    createNotificationChannel(NotificationChannel(CHANNEL_ID, "Odysseus tracker state", NotificationManager.IMPORTANCE_LOW))
                }
            }
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("${getString(R.string.app_name)} active")
        .setContentText("Finish the task (by back button or recents list) to stop.")
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                0
            )
        )
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}