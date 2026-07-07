package io.hammerhead.hvvferry.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class FerryUpdateService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("FerryUpdateService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("FerryUpdateService started")
        // Background update logic will be added here
        // Battery optimization: Changed from START_STICKY to START_NOT_STICKY
        // This prevents the service from auto-restarting and draining battery
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        Timber.d("FerryUpdateService destroyed")
        super.onDestroy()
    }
}
