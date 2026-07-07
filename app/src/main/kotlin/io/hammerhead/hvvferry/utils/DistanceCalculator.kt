package io.hammerhead.hvvferry.utils

import kotlin.math.*

/**
 * Calculate distances between GPS coordinates
 */
object DistanceCalculator {
    
    private const val EARTH_RADIUS_METERS = 6371000.0
    
    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     * 
     * @return Distance in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Int {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = EARTH_RADIUS_METERS * c
        
        return distance.roundToInt()
    }
    
    /**
     * Estimate walking time in minutes (assuming 5 km/h average speed)
     */
    fun estimateWalkingMinutes(distanceMeters: Int): Int {
        val speedMetersPerMinute = 5000.0 / 60.0 // 5 km/h = ~83 m/min
        return (distanceMeters / speedMetersPerMinute).roundToInt()
    }
}
