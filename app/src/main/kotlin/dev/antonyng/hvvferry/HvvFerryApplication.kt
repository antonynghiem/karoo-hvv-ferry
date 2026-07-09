package dev.antonyng.hvvferry

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class HvvFerryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Configure logging based on build type
        if (BuildConfig.DEBUG) {
            // Debug builds: Full verbose logging with emojis and details
            Timber.plant(Timber.DebugTree())
        } else {
            // Release builds: Only log errors and warnings (no debug/info)
            Timber.plant(ReleaseTree())
        }
        
        Timber.d("HVV Ferry Extension initialized")
    }
    
    /**
     * Custom Timber tree for release builds.
     * Only logs warnings and errors - ignores debug and info logs.
     * This improves performance and security in production.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
                // Ignore debug and info logs in release
                return
            }
            
            // Only log warnings and errors in release builds
            // You could send these to a crash reporting service like Firebase Crashlytics
            if (priority == Log.ERROR || priority == Log.WARN) {
                // In production, you might want to send to crash reporting
                // FirebaseCrashlytics.getInstance().recordException(t ?: Exception(message))
                Log.println(priority, tag ?: "HvvFerry", message)
                t?.let { Log.println(priority, tag ?: "HvvFerry", Log.getStackTraceString(it)) }
            }
        }
    }
}
