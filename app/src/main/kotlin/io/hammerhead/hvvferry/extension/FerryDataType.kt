package io.hammerhead.hvvferry.extension

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.hvvferry.data.models.Departure
import io.hammerhead.hvvferry.data.models.FerryConfig
import io.hammerhead.hvvferry.data.models.FerryStop
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
 * 
 * GPS Auto-Detection: When enabled, uses Karoo SDK's OnLocationChanged events
 * to find nearby ferry stops. Includes throttling and Hamburg proximity checks
 * to minimize battery impact.
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
    
    // GPS auto-detection state
    private var karooSystem: KarooSystemService? = null
    private var locationConsumerId: String? = null
    private var currentLocation: Pair<Double, Double>? = null  // (lat, lon)
    private var nearestStop: FerryStop? = null
    private var lastGpsLookupTime: Long = 0
    
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
        data class Success(val departure: Departure?, val stopName: String? = null) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
        data object NoStopsNearby : UpdateResult()
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
        
        // Get initial config to check if GPS is enabled
        val initialConfig = preferencesManager.getConfig()
        if (initialConfig.gpsAutoDetectionEnabled) {
            startLocationUpdates()
        }
        
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
                                    val stopInfo = result.stopName?.let { " at $it" } ?: ""
                                    Timber.d("✅ Streaming: Next ferry in ${minutesUntil.toInt()} minutes$stopInfo")
                                } else {
                                    // Valid response but no departures available
                                    emitter.onNext(StreamState.Searching)
                                    Timber.d("📭 No departures available (valid response)")
                                }
                            }
                            is UpdateResult.NoStopsNearby -> {
                                // GPS enabled but no stops found nearby - not an error
                                failureCount = 0
                                emitter.onNext(StreamState.Searching)
                                Timber.d("📍 No ferry stops nearby")
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
            stopLocationUpdates()
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
        
        // Get initial config to check if GPS is enabled
        val initialConfig = preferencesManager.getConfig()
        if (initialConfig.gpsAutoDetectionEnabled) {
            startLocationUpdates()
        }
        
        viewJob = scope.launch {
            while (isActive) {
                // Cache config once per cycle
                val ferryConfig = preferencesManager.getConfig()
                
                try {
                    if (shouldUpdate(ferryConfig)) {
                        // Get the active stop (GPS-detected or manual)
                        val stop = getActiveStop(ferryConfig)
                        
                        if (stop != null) {
                            val maxDepartures = if (ferryConfig.showTwoDepartures) 10 else 5
                            val departures = repository.getDepartures(stop, maxDepartures).getOrNull() 
                                ?: emptyList()
                            
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
            stopLocationUpdates()
        }
    }

    // ==================== GPS AUTO-DETECTION ====================

    /**
     * Start receiving GPS location updates from the Karoo SDK.
     * Only called when GPS auto-detection is enabled.
     */
    private fun startLocationUpdates() {
        if (karooSystem != null) {
            Timber.d("📍 GPS updates already running")
            return
        }
        
        Timber.d("📍 Starting GPS location updates")
        karooSystem = KarooSystemService(context).apply {
            connect { connected ->
                if (connected) {
                    Timber.d("📍 Connected to Karoo system, subscribing to GPS")
                    locationConsumerId = addConsumer(
                        onError = { error -> 
                            Timber.e("📍 GPS error: $error") 
                        },
                        onComplete = { 
                            Timber.d("📍 GPS stream completed") 
                        }
                    ) { event: OnLocationChanged ->
                        currentLocation = Pair(event.lat, event.lng)
                        Timber.v("📍 GPS update: ${event.lat}, ${event.lng}")
                    }
                    Timber.d("📍 GPS consumer started: $locationConsumerId")
                } else {
                    Timber.w("📍 Failed to connect to Karoo system for GPS")
                }
            }
        }
    }

    /**
     * Stop receiving GPS location updates.
     * Called when data field becomes hidden or GPS is disabled.
     */
    private fun stopLocationUpdates() {
        locationConsumerId?.let { id ->
            Timber.d("📍 Removing GPS consumer: $id")
            karooSystem?.removeConsumer(id)
        }
        karooSystem?.disconnect()
        karooSystem = null
        locationConsumerId = null
        currentLocation = null
        nearestStop = null
        lastGpsLookupTime = 0
        Timber.d("📍 GPS location updates stopped")
    }

    /**
     * Get the active ferry stop to use for departure lookups.
     * 
     * If GPS auto-detection is enabled:
     *   1. Check if we're near Hamburg (within 15km)
     *   2. Search for nearby stops within the configured radius
     *   3. Fall back to manual stop if no GPS stops found
     * 
     * If GPS is disabled, use the manually configured stop.
     */
    private suspend fun getActiveStop(config: FerryConfig): FerryStop? {
        if (config.gpsAutoDetectionEnabled) {
            val gpsStop = getStopFromGps(config)
            if (gpsStop != null) {
                return gpsStop
            }
            // Fall back to manual stop if GPS found nothing
            Timber.d("📍 GPS found no nearby stops, falling back to manual stop")
        }
        
        // Use manual stop
        return config.manualStopId?.let { repository.getStopById(it) }
    }

    /**
     * Get the nearest ferry stop based on current GPS location.
     * 
     * Battery optimizations:
     * - Throttled to config.gpsLookupIntervalSeconds (default 180s)
     * - Skipped if >15km from Hamburg center
     * - Returns cached result within throttle window
     */
    private suspend fun getStopFromGps(config: FerryConfig): FerryStop? {
        val location = currentLocation
        if (location == null) {
            Timber.d("📍 No GPS location available yet")
            return null
        }
        
        // Hamburg proximity check - skip lookup if >15km away
        if (!isNearHamburg(location.first, location.second)) {
            Timber.d("📍 >15km from Hamburg, skipping GPS stop lookup")
            return null
        }
        
        // Throttle GPS lookups based on configured interval
        val now = System.currentTimeMillis()
        val throttleMs = config.gpsLookupIntervalSeconds * 1000L
        if (now - lastGpsLookupTime < throttleMs && nearestStop != null) {
            Timber.d("📍 Using cached nearest stop: ${nearestStop?.name}")
            return nearestStop
        }
        
        // Time to search for nearby stops
        Timber.d("📍 Searching for ferry stops within ${config.proximityRadiusMeters}m of ${location.first}, ${location.second}")
        val result = repository.findNearbyFerryStops(
            latitude = location.first,
            longitude = location.second,
            radiusMeters = config.proximityRadiusMeters
        )
        
        lastGpsLookupTime = now
        nearestStop = result.getOrNull()?.firstOrNull()?.stop
        
        if (nearestStop != null) {
            Timber.d("📍 Found nearest stop: ${nearestStop?.name}")
        } else {
            Timber.d("📍 No ferry stops found within ${config.proximityRadiusMeters}m")
        }
        
        return nearestStop
    }

    // ==================== DATA UPDATE LOGIC ====================

    /**
     * Update ferry data and return the result.
     * Returns UpdateResult.Success with departure (or null if no departures).
     * Returns UpdateResult.NoStopsNearby when GPS is enabled but no stops found.
     * Returns UpdateResult.Error on API/network failures.
     */
    private suspend fun updateFerryData(config: FerryConfig): UpdateResult {
        // Get the active stop (GPS-detected or manual)
        val stop = getActiveStop(config)
        
        if (stop == null) {
            // Check if this is because GPS found no stops, or no stop configured at all
            return if (config.gpsAutoDetectionEnabled && config.manualStopId == null) {
                UpdateResult.NoStopsNearby
            } else if (config.manualStopId == null) {
                // No stop configured - this is not an error, just no data
                UpdateResult.Success(null)
            } else {
                UpdateResult.Error("Could not find configured stop")
            }
        }
        
        val stopId = stop.stationId
        
        // Check cache first - before any other operations
        if (isCacheValid(stopId)) {
            Timber.d("💾 Using cached departures for stop $stopId")
            val cached = cachedDeparturesData?.second
            val departure = cached?.firstOrNull { !it.cancelled }
            return UpdateResult.Success(departure, stop.name)
        }
        
        // Cache miss - need to fetch from API
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
                UpdateResult.Success(departure, stop.name)
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
        nearestStop = null
        lastGpsLookupTime = 0
    }

    /**
     * Battery optimization: Determine if we should update based on multiple factors
     */
    @Suppress("UNUSED_PARAMETER")
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
        val isNear = distance <= PROXIMITY_RADIUS_KM
        if (!isNear) {
            Timber.d("📍 Distance from Hamburg: ${String.format("%.1f", distance)}km (>$PROXIMITY_RADIUS_KM km limit)")
        }
        return isNear
    }
}
