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
import io.hammerhead.hvvferry.data.models.Departure
import io.hammerhead.hvvferry.data.models.FerryConfig
import io.hammerhead.hvvferry.data.preferences.CredentialManager
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.data.repository.FerryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    // Battery optimization: Structured concurrency with proper scope management
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null
    private var viewJob: Job? = null

    // Battery optimization: Response caching
    private var cachedDeparturesData: Pair<String, List<Departure>>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_TTL_MS = 30_000L // 30 seconds
    
    // Battery optimization: Exponential backoff
    private var failureCount = 0
    private val MAX_BACKOFF_SECONDS = 600L // 10 minutes max
    
    companion object {
        private const val HAMBURG_CENTER_LAT = 53.5511
        private const val HAMBURG_CENTER_LON = 9.9937
        private const val PROXIMITY_RADIUS_KM = 15.0
    }

    /**
     * Result type for updateFerryData to distinguish API errors from empty results.
     * This allows proper exponential backoff behavior.
     */
    private sealed class UpdateResult {
        data class Success(val departure: Departure?) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
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
        
        // Cancel any existing job before starting new one
        streamJob?.cancel()
        
        // Launch polling coroutine with proper scope management
        streamJob = scope.launch {
            emitter.onNext(StreamState.Searching)
            
            while (isActive) {
                // Cache config once per polling cycle to reduce SharedPreferences I/O
                val config = preferencesManager.getConfig()
                
                try {
                    // Battery optimization: Check if we should update
                    if (shouldUpdate(config)) {
                        when (val result = updateFerryData(config)) {
                            is UpdateResult.Success -> {
                                // Reset failure count on ANY successful API response
                                failureCount = 0
                                
                                if (result.departure != null) {
                                    // Emit streaming state with next departure time
                                    val minutesUntil = result.departure.timeOffset.toDouble()
                                    
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
                                    Timber.d("✅ Streaming: Next ferry in ${minutesUntil.toInt()} minutes")
                                } else {
                                    // Valid response but no departures available
                                    emitter.onNext(StreamState.Searching)
                                    Timber.d("📭 No departures available (valid response)")
                                }
                            }
                            is UpdateResult.Error -> {
                                // Only increment failure count on actual errors
                                failureCount++
                                emitter.onNext(StreamState.Searching)
                                Timber.w("❌ Update error: ${result.message}")
                            }
                        }
                    } else {
                        Timber.d("⏸️ Skipping update due to battery optimization checks")
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error updating ferry data")
                    failureCount++
                    emitter.onNext(StreamState.Searching)
                }
                
                // Calculate delay with exponential backoff on failures
                val delayMs = calculateBackoffDelay(config.updateIntervalSeconds)
                delay(delayMs)
            }
        }
        
        // BATTERY OPTIMIZATION: This is called when the data field becomes HIDDEN
        emitter.setCancellable {
            Timber.d("🛑 Ferry data field became HIDDEN - stopping polling to save battery")
            streamJob?.cancel()
            streamJob = null
            clearState()
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
        
        // Cancel any existing view job
        viewJob?.cancel()
        
        viewJob = scope.launch {
            while (isActive) {
                // Cache config once per cycle
                val ferryConfig = preferencesManager.getConfig()
                
                try {
                    if (shouldUpdate(ferryConfig)) {
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
                
                delay(ferryConfig.updateIntervalSeconds * 1000L)
            }
        }
        
        emitter.setCancellable {
            Timber.d("🛑 Stopping ferry view")
            viewJob?.cancel()
            viewJob = null
        }
    }

    /**
     * Update ferry data and return the result.
     * Returns UpdateResult.Success with departure (or null if no departures).
     * Returns UpdateResult.Error on API/network failures.
     */
    private suspend fun updateFerryData(config: FerryConfig): UpdateResult {
        val stopId = config.manualStopId
        
        if (stopId == null) {
            // No stop configured - this is not an error, just no data
            return UpdateResult.Success(null)
        }
        
        // Check cache first - before any other operations
        if (isCacheValid(stopId)) {
            Timber.d("💾 Using cached departures for stop $stopId")
            val cached = cachedDeparturesData?.second
            val departure = cached?.firstOrNull { !it.cancelled }
            return UpdateResult.Success(departure)
        }
        
        // Cache miss - need to fetch from API
        val stop = repository.getStopById(stopId)
        if (stop == null) {
            return UpdateResult.Error("Stop not found: $stopId")
        }
        
        val maxDepartures = if (config.showTwoDepartures) 10 else 5
        val result = repository.getDepartures(stop, maxDepartures)
        
        return result.fold(
            onSuccess = { departures ->
                // Update cache with successful response
                cachedDeparturesData = Pair(stopId, departures)
                cacheTimestamp = System.currentTimeMillis()
                
                Timber.d("📡 Fetched ${departures.size} departures for ${stop.name} - cached")
                
                // Return first non-cancelled departure (may be null if all cancelled or empty)
                val departure = departures.firstOrNull { !it.cancelled }
                UpdateResult.Success(departure)
            },
            onFailure = { error ->
                UpdateResult.Error(error.message ?: "Unknown error")
            }
        )
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
     * Calculate delay with exponential backoff for failures.
     */
    private fun calculateBackoffDelay(baseIntervalSeconds: Int): Long {
        return if (failureCount > 0) {
            val backoffDelay = min(
                baseIntervalSeconds * (2.0.pow(failureCount.toDouble())).toLong(),
                MAX_BACKOFF_SECONDS
            )
            Timber.d("⏰ Using backoff delay: ${backoffDelay}s (failures: $failureCount)")
            backoffDelay * 1000
        } else {
            baseIntervalSeconds * 1000L
        }
    }

    /**
     * Clear all cached state when stopping.
     */
    private fun clearState() {
        cachedDeparturesData = null
        cacheTimestamp = 0
        failureCount = 0
    }

    /**
     * Battery optimization: Determine if we should update based on multiple factors
     */
    private fun shouldUpdate(config: FerryConfig): Boolean {
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
        if (config.gpsAutoDetectionEnabled) {
            // TODO: GPS auto-detection will be implemented in next phase
            Timber.d("📍 GPS auto-detection enabled but not yet implemented - allowing update")
        }
        
        return true
    }

    /**
     * Battery optimization: Check if we're during typical ferry service hours
     * Hamburg ferries typically run ~5am to ~11:30pm (last departures around 23:15)
     */
    private fun isDuringServiceHours(): Boolean {
        val now = LocalTime.now()
        return now.isAfter(LocalTime.of(4, 45)) && now.isBefore(LocalTime.of(23, 30))
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
     * Used for Hamburg proximity check to avoid unnecessary API calls when far from Hamburg.
     */
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
     * Used to skip GPS stop lookups when user is >15km from Hamburg center.
     */
    private fun isNearHamburg(lat: Double, lon: Double): Boolean {
        val distance = calculateDistance(lat, lon, HAMBURG_CENTER_LAT, HAMBURG_CENTER_LON)
        return distance <= PROXIMITY_RADIUS_KM
    }
}
