package io.hammerhead.hvvferry.data.models

import kotlinx.serialization.Serializable

@Serializable
data class FerryConfig(
    // Display Settings
    val routeFormat: RouteFormat = RouteFormat.DIRECTION_ONLY,
    val showTwoDepartures: Boolean = false,
    val updateIntervalSeconds: Int = 60,
    
    // Ferry Lines
    val enabledFerryLines: Set<String> = setOf("62", "64", "68", "72", "73", "75"),
    
    // GPS Settings
    val gpsAutoDetectionEnabled: Boolean = false,
    val proximityRadiusMeters: Int = 1000,
    val gpsLookupIntervalSeconds: Int = 180, // How often to search for nearby stops (default 3 min)
    
    // Manual Stop
    val manualStopId: String? = null,
    val manualStopName: String? = null
)

@Serializable
enum class RouteFormat {
    FULL,           // "Landungsbrücken Brücke 1 → Finkenwerder"
    ABBREVIATED,    // "Landungsbr. → Finkenwerder"
    DIRECTION_ONLY  // "→ Finkenwerder"
}

enum class ProximityRadius(val meters: Int, val displayName: String) {
    VERY_CLOSE(500, "500m"),
    CLOSE(1000, "1 km"),
    MEDIUM(2000, "2 km"),
    FAR(5000, "5 km"),
    VERY_FAR(10000, "10 km");
    
    companion object {
        fun fromMeters(meters: Int): ProximityRadius {
            return values().find { it.meters == meters } ?: CLOSE
        }
    }
}

enum class UpdateInterval(val seconds: Int, val displayName: String) {
    FAST(30, "30 seconds"),
    NORMAL(60, "60 seconds (1 minute)"),
    SLOW(120, "120 seconds (2 minutes)");
    
    companion object {
        fun fromSeconds(seconds: Int): UpdateInterval {
            return entries.find { it.seconds == seconds } ?: NORMAL
        }
    }
}

enum class GpsLookupInterval(val seconds: Int, val displayName: String) {
    FAST(60, "60 seconds (1 minute)"),
    NORMAL(180, "180 seconds (3 minutes)"),
    SLOW(300, "300 seconds (5 minutes)");
    
    companion object {
        fun fromSeconds(seconds: Int): GpsLookupInterval {
            return entries.find { it.seconds == seconds } ?: NORMAL
        }
    }
}
