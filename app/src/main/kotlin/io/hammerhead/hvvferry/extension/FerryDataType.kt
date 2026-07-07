package io.hammerhead.hvvferry.extension

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.hvvferry.data.preferences.CredentialManager
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.data.repository.FerryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import kotlin.math.*

/**
 * Ferry departure data type implementation.
 * 
 * Battery optimization: This class only polls for data when the data field
 * is actively displayed on the Karoo screen. When the user switches to a 
 * different screen or profile, polling automatically stops.
 */
class FerryDataType(
    extension: String,
    private val context: Context,
    private val repository: FerryRepository,
    private val credentialManager: CredentialManager,
    private val preferencesManager: PreferencesManager,
    private val viewProvider: FerryViewProvider
) : DataTypeImpl(extension, "ferry-next-departure") {

    // Battery optimization: Response caching
    private var cachedDeparturesData: Pair<String, List<Any>>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_TTL_MS = 30_000 // 30 seconds
    
    // Battery optimization: Exponential backoff
    private var failureCount = 0
    private val MAX_BACKOFF_SECONDS = 600L // 10 minutes max
    
    companion object {
        private const val HAMBURG_CENTER_LAT = 53.5511
        private const val HAMBURG_CENTER_LON = 9.9937
        private const val PROXIMITY_RADIUS_KM = 15.0
    }

    /**
     * Called when the data field starts streaming (becomes VISIBLE on screen).
     * 
     * BATTERY OPTIMIZATION: This is the key lifecycle hook!
     * Polling only happens when this method is active.
     * When the user switches screens, emitter.setCancellable{} is called automatically.
     */
    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("🚢 Ferry data field became VISIBLE - starting smart polling")
        
        if (!credentialManager.hasCredentials()) {
            Timber.w("No credentials configured - cannot stream ferry data")
            emitter.onNext(StreamState.Searching)
            return
        }
        
        // Launch polling coroutine
        val updateJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(StreamState.Searching)
            
            while (isActive) {
                try {
                    // Battery optimization: Check if we should update
                    if (shouldUpdate()) {
                        val result = updateFerryData()
                        
                        if (result != null) {
                            // Emit streaming state with next departure time
                            val minutesUntil = result.timeOffset.toDouble()
                            
                            emitter.onNext(
                                StreamState.Streaming(
                                    dataPoint = DataPoint(
                                        dataTypeId = dataTypeId,
                                        values = mapOf(
                                            DataType.Field.SINGLE to minutesUntil
                                        )
                                    )
                                )
                            )
                            
                            // Reset failure count on success
                            failureCount = 0
                            Timber.d("✅ Streaming: Next ferry in ${minutesUntil.toInt()} minutes")
                        } else {
                            // No departures available
                            emitter.onNext(StreamState.Searching)
                            failureCount++
                        }
                    } else {
                        Timber.d("⏸️ Skipping update due to battery optimization checks")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error updating ferry data")
                    failureCount++
                    emitter.onNext(StreamState.Searching)
                }
                
                // Use exponential backoff delay on failures
                val intervalSeconds = preferencesManager.getUpdateInterval().toLong()
                val delayMs = if (failureCount > 0) {
                    val backoffDelay = min(
                        intervalSeconds * (2.0.pow(failureCount.toDouble())).toLong(),
                        MAX_BACKOFF_SECONDS
                    )
                    Timber.d("⏰ Using backoff delay: ${backoffDelay}s (failures: $failureCount)")
                    backoffDelay * 1000
                } else {
                    intervalSeconds * 1000
                }
                
                delay(delayMs)
            }
        }
        
        // BATTERY OPTIMIZATION: This is called when the data field becomes HIDDEN
        emitter.setCancellable {
            Timber.d("🛑 Ferry data field became HIDDEN - stopping polling to save battery")
            updateJob.cancel()
            // Clear cache when stopping
            cachedDeparturesData = null
            cacheTimestamp = 0
            failureCount = 0
        }
    }

    /**
     * Called when a graphical view is needed (if graphical=true in extension_info.xml).
     * 
     * Currently graphical=false, so this won't be called.
     * Keeping this for future use if we want custom graphical views.
     */
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("🎨 Starting ferry graphical view")
        
        if (!credentialManager.hasCredentials()) {
            Timber.w("No credentials configured")
            return
        }
        
        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    if (shouldUpdate()) {
                        val ferryConfig = preferencesManager.getConfig()
                        val stopId = ferryConfig.manualStopId
                        
                        if (stopId != null) {
                            val stop = repository.getStopById(stopId)
                            val departures = stop?.let { 
                                val maxDepartures = if (ferryConfig.showTwoDepartures) 10 else 5
                                repository.getDepartures(it, maxDepartures).getOrNull() 
                            } ?: emptyList()
                            
                            // Create custom RemoteViews
                            val remoteViews = viewProvider.createFerryDataFieldView(
                                departures,
                                null, // distance
                                ferryConfig
                            )
                            
                            emitter.updateView(remoteViews)
                            Timber.d("✅ Updated view with ${departures.size} departures")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error updating ferry view")
                }
                
                val intervalSeconds = preferencesManager.getUpdateInterval().toLong()
                delay(intervalSeconds * 1000)
            }
        }
        
        emitter.setCancellable {
            Timber.d("🛑 Stopping ferry view")
            viewJob.cancel()
        }
    }

    /**
     * Update ferry data and return the next departure.
     * Returns null if no departures available.
     */
    private suspend fun updateFerryData(): io.hammerhead.hvvferry.data.models.Departure? {
        val config = preferencesManager.getConfig()
        val stopId = config.manualStopId ?: return null
        
        // Check cache first
        if (isCacheValid(stopId)) {
            Timber.d("💾 Using cached departures for stop $stopId")
            val cached = cachedDeparturesData?.second as? List<io.hammerhead.hvvferry.data.models.Departure>
            return cached?.firstOrNull { !it.cancelled }
        }
        
        val stop = repository.getStopById(stopId) ?: return null
        val maxDepartures = if (config.showTwoDepartures) 10 else 5
        val result = repository.getDepartures(stop, maxDepartures)
        val departures = result.getOrNull() ?: return null
        
        // Update cache
        cachedDeparturesData = Pair(stopId, departures)
        cacheTimestamp = System.currentTimeMillis()
        
        Timber.d("📡 Fetched ${departures.size} departures for ${stop.name} - cached")
        
        // Return first non-cancelled departure
        return departures.firstOrNull { !it.cancelled }
    }

    /**
     * Battery optimization: Check if cached data is still valid
     */
    private fun isCacheValid(stopId: String): Boolean {
        val cached = cachedDeparturesData ?: return false
        if (cached.first != stopId) return false
        
        val age = System.currentTimeMillis() - cacheTimestamp
        return age < CACHE_TTL_MS
    }

    /**
     * Battery optimization: Determine if we should update based on multiple factors
     */
    private fun shouldUpdate(): Boolean {
        // Check 1: Are we during ferry service hours?
        if (!isDuringServiceHours()) {
            Timber.d("⏰ Skipping update: outside service hours")
            return false
        }
        
        // Check 2: Do we have network connectivity?
        if (!hasNetworkConnectivity()) {
            Timber.d("📡 Skipping update: no network connectivity")
            return false
        }
        
        // Check 3: GPS proximity (prepared for future implementation)
        val config = preferencesManager.getConfig()
        if (config.gpsAutoDetectionEnabled) {
            // TODO: Get actual GPS coordinates from Karoo SDK
            // For now, skip this check to prevent battery drain from location requests
            Timber.d("📍 GPS auto-detection enabled but not yet implemented - allowing update")
        }
        
        return true
    }

    /**
     * Battery optimization: Check if we're during typical ferry service hours
     * Hamburg ferries typically run 5am-11pm
     */
    private fun isDuringServiceHours(): Boolean {
        val currentHour = LocalTime.now().hour
        return currentHour in 5..23
    }

    /**
     * Battery optimization: Check network connectivity
     */
    private fun hasNetworkConnectivity(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Timber.e(e, "Error checking network connectivity")
            true // Fail open - allow update if we can't check
        }
    }

    /**
     * Battery optimization: Calculate distance between two GPS coordinates (Haversine formula)
     * This would be used for proximity-based throttling if GPS is implemented
     */
    @Suppress("unused")
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadiusKm * c
    }

    /**
     * Battery optimization: Check if current location is near Hamburg ferry area
     * This would be used if GPS coordinates were available from Karoo SDK
     */
    @Suppress("unused")
    private fun isNearHamburg(lat: Double, lon: Double): Boolean {
        val distance = calculateDistance(lat, lon, HAMBURG_CENTER_LAT, HAMBURG_CENTER_LON)
        return distance <= PROXIMITY_RADIUS_KM
    }
}
