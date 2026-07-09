package dev.antonyng.hvvferry.data.models

import kotlinx.serialization.Serializable

@Serializable
data class FerryConfig(
    // Display Settings
    val routeFormat: RouteFormat = RouteFormat.ABBREVIATED,
    val showTwoDepartures: Boolean = false,
    val updateIntervalSeconds: Int = 300, // 5 minutes — matches repository cache TTL
    
    // Ferry Lines
    val enabledFerryLines: Set<String> = setOf("62", "64", "68", "72", "73", "75"),
    
    // GPS Settings
    val gpsAutoDetectionEnabled: Boolean = false,
    val proximityRadiusMeters: Int = 1000,
    val gpsLookupIntervalSeconds: Int = 180, // How often to search for nearby stops (default 3 min)
    
    // Manual Stop
    val manualStopId: String? = null,
    val manualStopName: String? = null,

    // Connection filter: empty = show all, non-empty = only matching "line::direction" pairs
    val enabledConnections: Set<String> = emptySet()
) {
    companion object {
        fun connectionKey(lineName: String, direction: String) = "$lineName::$direction"
    }
}

@Serializable
enum class RouteFormat {
    ABBREVIATED,    // "62 → Finkenw." + pier "Brücke 3"
    DIRECTION_ONLY  // "62 → Finkenwerder" + pier full name
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
    FAST(60, "1 minute"),
    NORMAL(300, "5 minutes (recommended)"),
    SLOW(600, "10 minutes");

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
