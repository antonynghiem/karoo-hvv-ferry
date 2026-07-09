package dev.antonyng.hvvferry.utils

import dev.antonyng.hvvferry.data.models.Departure
import dev.antonyng.hvvferry.data.models.RouteFormat
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
     * Format route based on route format setting
     * Full:        "→ Finkenwerder"
     * Abbreviated: "→ Finkenw."
     * Direction:   "→ Finkenw."  (same as abbreviated — fits in tight space)
     */
    fun formatDirection(destination: String, format: RouteFormat, origin: String = ""): String {
        return when (format) {
            RouteFormat.ABBREVIATED -> "→ ${abbreviate(destination)}"
            RouteFormat.DIRECTION_ONLY -> "→ $destination"
        }
    }
    
    /**
     * Abbreviate a stop or pier name for compact display.
     * Pier names like "Landungsbrücken Brücke 2" → "Brücke 2"
     * Station names like "Finkenwerder" → "Finkenw."
     */
    fun abbreviate(name: String): String {
        // For pier names: strip the station prefix, keep "Brücke N"
        val bruckeMatch = Regex("""Brücke \d+""").find(name)
        if (bruckeMatch != null) return bruckeMatch.value

        val abbreviations = mapOf(
            "Landungsbrücken" to "Landungsbr.",
            "Teufelsbrück" to "Teufelsbrück",
            "Finkenwerder" to "Finkenw.",
            "Neumühlen/Övelgönne" to "Neumühlen",
            "Ernst-August-Schleuse" to "E-A-Schleuse",
            "Ernst-August-Kanal" to "E-A-Kanal",
            "Elbphilharmonie" to "Elbphilharm.",
            "Dockland (Fischereihafen)" to "Dockland",
            "Blankenese" to "Blankenese",
            "Steinwerder" to "Steinwerder",
            "Neßsand" to "Neßsand"
        )
        return abbreviations[name] ?: name
    }

    private fun abbreviateStopName(name: String): String = abbreviate(name)
    
    /**
     * Format time with optional delay
     * Examples: "18:34", "18:34 +5min"
     */
    fun formatTime(departure: Departure): String {
        val baseTime = departure.getDisplayTime()
        
        return when {
            departure.cancelled -> "CANCELLED"
            departure.hasDelay() -> "$baseTime +${departure.delay}min"
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
