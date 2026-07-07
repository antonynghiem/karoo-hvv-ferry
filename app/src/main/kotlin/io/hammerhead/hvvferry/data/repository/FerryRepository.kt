package io.hammerhead.hvvferry.data.repository

import io.hammerhead.hvvferry.api.GeofoxClient
import io.hammerhead.hvvferry.api.models.*
import io.hammerhead.hvvferry.data.database.FerryStopDao
import io.hammerhead.hvvferry.data.models.*
import io.hammerhead.hvvferry.data.preferences.CredentialManager
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.utils.DistanceCalculator
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
            // Landungsbrücken - major hub for most ferry lines
            "Master:10702" to listOf("62", "72", "73", "75"),
            // Finkenwerder
            "Master:90625" to listOf("62", "64"),
            // Teufelsbrück
            "Master:90650" to listOf("62", "64"),
            // Neumühlen/Övelgönne
            "Master:90659" to listOf("62", "64"),
            // Dockland
            "Master:90677" to listOf("62", "64"),
            // Neßsand (seasonal)
            "Master:90699" to listOf("68"),
            // Blankenese
            "Master:90690" to listOf("62", "64")
        )
        
        // Default list for unknown stops - all Hamburg ferry lines
        private val ALL_FERRY_LINES = listOf("62", "64", "68", "72", "73", "75")
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
            
            // Filter for ferry stops only
            val ferryStops = response.results
                .filter { it.serviceTypes?.contains("ship") == true }
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
    
    /**
     * Get departures for a ferry stop
     */
    suspend fun getDepartures(
        stop: FerryStop,
        maxDepartures: Int = 10
    ): Result<List<Departure>> {
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
                maxList = maxDepartures,
                maxTimeOffset = 120,
                useRealtime = true
            )
            
            val response = geofoxClient.getDepartureList(request, username, password)
            
            val enabledLines = preferencesManager.getEnabledFerryLines()
            
            // Filter and convert to domain model
            val departures = response.departures
                .filter { !it.cancelled }  // Skip cancelled departures
                .filter { it.line.type == ServiceType.SHIP }
                .filter { it.line.name in enabledLines }
                .map { data ->
                    Departure(
                        line = FerryLine(
                            name = data.line.name,
                            direction = data.line.direction,
                            type = data.line.type.name.lowercase(),
                            id = data.line.id
                        ),
                        timeOffset = data.timeOffset,
                        plannedTime = calculateDepartureTime(data.timeOffset),
                        delay = data.delay,
                        cancelled = data.cancelled,
                        extra = data.extra,
                        platform = data.platform,
                        serviceId = data.serviceId
                    )
                }
            
            Result.success(departures)
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
