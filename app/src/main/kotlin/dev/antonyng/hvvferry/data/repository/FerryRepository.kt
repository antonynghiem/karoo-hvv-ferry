package dev.antonyng.hvvferry.data.repository

import dev.antonyng.hvvferry.api.GeofoxClient
import dev.antonyng.hvvferry.api.models.*
import dev.antonyng.hvvferry.data.database.FerryStopDao
import dev.antonyng.hvvferry.data.models.*
import dev.antonyng.hvvferry.data.preferences.CredentialManager
import dev.antonyng.hvvferry.data.preferences.PreferencesManager
import dev.antonyng.hvvferry.utils.DistanceCalculator
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FerryRepository @Inject constructor(
    private val geofoxClient: GeofoxClient,
    private val ferryStopDao: FerryStopDao,
    private val credentialManager: CredentialManager,
    private val preferencesManager: PreferencesManager
) {
    
    companion object {
        // Known Hamburg ferry lines by major stops
        // Maps station IDs to the specific ferry lines that serve them
        private val KNOWN_FERRY_LINES_BY_STOP = mapOf(
            // Landungsbrücken - major hub for most ferry lines (verified via Geofox API)
            "Master:80950" to listOf("62", "72", "73", "75"),
            // Finkenwerder Fähre (verified via Geofox API)
            "Master:51014" to listOf("62", "64"),
            // Teufelsbrück (verified via Geofox API)
            "Master:80981" to listOf("62", "64"),
            // Neumühlen/Övelgönne (verified via Geofox API)
            "Master:52982" to listOf("62", "64"),
            // Dockland Fischereihafen (verified via Geofox API)
            "Master:80989" to listOf("62", "64"),
            // Neßsand (seasonal, not yet verified)
            "Master:54661" to listOf("68"),
            // Blankenese Fähre (verified via Geofox API)
            "Master:81054" to listOf("62", "64"),
            // Rüschpark (verified via Geofox API)
            "Master:51981" to listOf("62", "64")
        )
        
        // Default list for unknown stops - all Hamburg ferry lines
        private val ALL_FERRY_LINES = listOf("62", "64", "68", "72", "73", "75")

        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    /**
     * Test API connection
     */
    suspend fun testConnection(): Result<String> {
        return try {
            val username = credentialManager.getUsername()
                ?: return Result.failure(Exception("No username"))
            val password = credentialManager.getPassword()
                ?: return Result.failure(Exception("No password"))
            
            val response = geofoxClient.init(username, password)
            Result.success("Connected: ${response.buildText}")
        } catch (e: Exception) {
            Timber.e(e, "Connection test failed")
            Result.failure(e)
        }
    }
    
    /**
     * Find ferry stops near GPS coordinates
     */
    suspend fun findNearbyFerryStops(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): Result<List<FerryStopWithDistance>> {
        return try {
            // First check local cache
            val cachedStops = ferryStopDao.getAllStopsSync()
            
            if (cachedStops.isNotEmpty()) {
                // Use cached stops and calculate distances
                val nearby = cachedStops
                    .map { stop ->
                        val distance = DistanceCalculator.calculateDistance(
                            latitude, longitude,
                            stop.latitude, stop.longitude
                        )
                        FerryStopWithDistance(stop, distance)
                    }
                    .filter { it.distance <= radiusMeters }
                    .sortedBy { it.distance }
                
                Result.success(nearby)
            } else {
                // No cache, use API
                findNearbyStopsFromApi(latitude, longitude, radiusMeters)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to find nearby stops")
            Result.failure(e)
        }
    }
    
    private suspend fun findNearbyStopsFromApi(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): Result<List<FerryStopWithDistance>> {
        return try {
            val username = credentialManager.getUsername()
                ?: return Result.failure(Exception("No credentials"))
            val password = credentialManager.getPassword()
                ?: return Result.failure(Exception("No credentials"))
            
            val request = CheckNameRequest(
                theName = SDName(
                    type = "STATION",
                    coordinate = Coordinate(x = longitude, y = latitude)
                ),
                maxList = 20,
                maxDistance = radiusMeters
            )
            
            val response = geofoxClient.checkName(request, username, password)
            
            val ferryStops = response.results
                .map { regional ->
                    FerryStopWithDistance(
                        stop = FerryStop(
                            stationId = regional.id,
                            name = regional.name,
                            latitude = regional.coordinate?.y ?: latitude,
                            longitude = regional.coordinate?.x ?: longitude,
                            ferryLines = extractFerryLines(regional.id)
                        ),
                        distance = regional.distance ?: 0
                    )
                }

            Result.success(ferryStops)
        } catch (e: Exception) {
            Timber.e(e, "API search failed")
            Result.failure(e)
        }
    }
    
    // Repository-level departure cache — shared between data field and FerryTimesActivity
    @Volatile private var cachedRawDepartures: Pair<String, List<Departure>>? = null // stopId → all departures
    @Volatile private var cacheTimestamp: Long = 0

    /**
     * Invalidate the departure cache — call when stop or connection filter changes.
     */
    fun invalidateCache() {
        cachedRawDepartures = null
        cacheTimestamp = 0
        Timber.d("🗑️ Departure cache invalidated")
    }

    private fun isCacheValid(stopId: String): Boolean {
        val cached = cachedRawDepartures ?: return false
        if (cached.first != stopId) return false
        return System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS
    }

    /**
     * Get departures for a ferry stop.
     * Always fetches 20 and caches for 5 minutes.
     * ignoreConnectionFilter=true returns all directions (used by FerryTimesActivity and route picker).
     */
    suspend fun getDepartures(
        stop: FerryStop,
        maxDepartures: Int = 20,
        ignoreConnectionFilter: Boolean = false
    ): Result<List<Departure>> {
        val enabledLines = preferencesManager.getEnabledFerryLines()
        val enabledConnections = preferencesManager.getConfig().enabledConnections

        // Serve from cache if valid
        if (isCacheValid(stop.stationId)) {
            Timber.d("💾 Using cached departures for ${stop.stationId}")
            val allCached = cachedRawDepartures!!.second
            val filtered = allCached.filter {
                ignoreConnectionFilter ||
                enabledConnections.isEmpty() ||
                FerryConfig.connectionKey(it.line.name, it.line.direction) in enabledConnections
            }
            return Result.success(filtered)
        }

        return try {
            val username = credentialManager.getUsername()
                ?: return Result.failure(Exception("No credentials"))
            val password = credentialManager.getPassword()
                ?: return Result.failure(Exception("No credentials"))

            val request = DepartureListRequest(
                station = SDName(
                    id = stop.stationId,
                    name = stop.name,
                    type = "STATION"
                ),
                time = GTITime(
                    date = "heute",
                    time = "jetzt"
                ),
                maxList = 20,
                maxTimeOffset = 120,
                useRealtime = true,
                serviceTypes = listOf("FAEHRE")
            )

            val response = geofoxClient.getDepartureList(request, username, password)

            Timber.d("🔍 Raw departures: ${response.departures.size} total, lines+dir+time: ${response.departures.map { "${it.line.name}->${it.line.direction}@${it.timeOffset}min" }}")

            // Map to domain model — apply line filter but NOT connection filter (cache stores all)
            val allDepartures = response.departures
                .filter { !it.cancelled }
                .filter { it.line.name in enabledLines }
                .map { data ->
                    Departure(
                        line = FerryLine(
                            name = data.line.name,
                            direction = data.line.direction,
                            origin = data.line.origin,
                            type = data.line.type.simpleType.lowercase(),
                            id = data.line.id
                        ),
                        timeOffset = data.timeOffset,
                        plannedTime = calculateDepartureTime(data.timeOffset),
                        delay = data.delay,
                        cancelled = data.cancelled,
                        extra = data.extra,
                        platform = data.platform,
                        serviceId = data.serviceId,
                        originLat = data.stopPoint?.coordinate?.y,
                        originLon = data.stopPoint?.coordinate?.x
                    )
                }

            // Store all departures in cache (connection filter applied at read time)
            cachedRawDepartures = Pair(stop.stationId, allDepartures)
            cacheTimestamp = System.currentTimeMillis()

            // Apply connection filter for caller
            val filtered = allDepartures.filter {
                ignoreConnectionFilter ||
                enabledConnections.isEmpty() ||
                FerryConfig.connectionKey(it.line.name, it.line.direction) in enabledConnections
            }

            Result.success(filtered)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get departures")
            Result.failure(e)
        }
    }
    
    /**
     * Get all cached ferry stops
     */
    fun getAllCachedStops(): Flow<List<FerryStop>> {
        return ferryStopDao.getAllStops()
    }
    
    /**
     * Search cached ferry stops
     */
    suspend fun searchCachedStops(query: String): List<FerryStop> {
        return ferryStopDao.searchStops(query)
    }
    
    /**
     * Get stop by ID
     */
    suspend fun getStopById(stationId: String): FerryStop? {
        return ferryStopDao.getStopById(stationId)
    }
    
    /**
     * Search for ferry stops by name via the Geofox API.
     * Filters results to ferry stops only and caches them locally.
     */
    suspend fun searchStopsByName(query: String): Result<List<FerryStop>> {
        return try {
            val username = credentialManager.getUsername()
                ?: return Result.failure(Exception("No credentials"))
            val password = credentialManager.getPassword()
                ?: return Result.failure(Exception("No credentials"))

            val request = CheckNameRequest(
                theName = SDName(name = query, type = "STATION"),
                maxList = 20
            )

            val response = geofoxClient.checkName(request, username, password)

            val ferryStops = response.results
                .map { regional ->
                    FerryStop(
                        stationId = regional.id,
                        name = regional.name,
                        latitude = regional.coordinate?.y ?: 0.0,
                        longitude = regional.coordinate?.x ?: 0.0,
                        ferryLines = extractFerryLines(regional.id)
                    )
                }

            // Cache results so GPS stop lookup can reuse them
            if (ferryStops.isNotEmpty()) {
                ferryStopDao.insertStops(ferryStops)
            }

            Timber.d("🔍 searchStopsByName('$query'): ${ferryStops.size} stops found")
            Result.success(ferryStops)
        } catch (e: Exception) {
            Timber.e(e, "searchStopsByName failed for query '$query'")
            Result.failure(e)
        }
    }

    /**
     * Cache ferry stops
     */
    suspend fun cacheStops(stops: List<FerryStop>) {
        ferryStopDao.insertStops(stops)
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheCount(): Int {
        return ferryStopDao.getStopCount()
    }
    
    /**
     * Clear cache
     */
    suspend fun clearCache() {
        ferryStopDao.deleteAllStops()
    }
    
    // Helper methods
    
    private fun calculateDepartureTime(offsetMinutes: Int): LocalDateTime {
        return LocalDateTime.now().plusMinutes(offsetMinutes.toLong())
    }
    
    private fun extractFerryLines(stationId: String): List<String> {
        // Try to get specific lines for this stop, otherwise return all
        // This approach reduces unnecessary API calls by filtering to relevant lines
        // TODO: Populate KNOWN_FERRY_LINES_BY_STOP with more real station IDs from testing
        return KNOWN_FERRY_LINES_BY_STOP[stationId] ?: ALL_FERRY_LINES
    }
}
