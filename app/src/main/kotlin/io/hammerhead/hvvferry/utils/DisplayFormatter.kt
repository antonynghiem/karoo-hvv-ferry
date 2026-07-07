package io.hammerhead.hvvferry.utils

import io.hammerhead.hvvferry.data.models.Departure
import io.hammerhead.hvvferry.data.models.RouteFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayFormatter @Inject constructor() {
    
    /**
     * Format a ferry departure for display on data field
     * Format: "62 → Finkenwerder  18:34 (450m)"
     */
    fun formatDeparture(
        departure: Departure,
        distance: Int?,
        format: RouteFormat
    ): String {
        val line = departure.line.name
        val direction = formatDirection(departure.line.direction, format)
        val time = formatTime(departure)
        val dist = distance?.let { " (${formatDistance(it)})" } ?: ""
        
        return "$line $direction  $time$dist"
    }
    
    /**
     * Format direction based on route format setting
     */
    private fun formatDirection(destination: String, format: RouteFormat): String {
        return when (format) {
            RouteFormat.FULL -> "→ $destination"
            RouteFormat.ABBREVIATED -> "→ ${abbreviateStopName(destination)}"
            RouteFormat.DIRECTION_ONLY -> {
                // For direction only, show first word or abbreviated version
                val firstWord = destination.split(" ").first()
                "→ ${abbreviateStopName(firstWord)}"
            }
        }
    }
    
    /**
     * Abbreviate common stop names
     */
    private fun abbreviateStopName(name: String): String {
        val abbreviations = mapOf(
            "Landungsbrücken Brücke 1" to "Landungsbr. Br.1",
            "Landungsbrücken Brücke 3" to "Landungsbr. Br.3",
            "Landungsbrücken" to "Landungsbr.",
            "Teufelsbrück" to "Tfbrück",
            "Finkenwerder" to "Finkenw.",
            "Neumühlen/Övelgönne" to "Neumühlen/Övlg.",
            "Ernst-August-Kanal" to "E-A-Kanal",
            "Elbphilharmonie" to "Elbphil.",
            "Altona Dockland" to "Altona Dockl."
        )
        return abbreviations[name] ?: name
    }
    
    /**
     * Format time with optional delay
     * Examples: "18:34", "18:34 +5"
     */
    private fun formatTime(departure: Departure): String {
        val baseTime = departure.getDisplayTime()
        
        return when {
            departure.cancelled -> "CANCELLED"
            departure.hasDelay() -> "$baseTime +${departure.delay}"
            else -> baseTime
        }
    }
    
    /**
     * Format distance in meters or kilometers
     */
    fun formatDistance(meters: Int): String {
        return if (meters < 1000) {
            "${meters}m"
        } else {
            String.format("%.1fkm", meters / 1000.0)
        }
    }
}
