package dev.antonyng.hvvferry.data.models

import java.time.LocalDateTime

data class Departure(
    val line: FerryLine,
    val timeOffset: Int,              // Minutes from now
    val plannedTime: LocalDateTime,   // Actual time
    val delay: Int? = null,           // Delay in minutes (if any)
    val cancelled: Boolean = false,
    val extra: Boolean = false,       // Extra/reinforcement ferry
    val platform: String? = null,     // Pier number
    val serviceId: Int,
    val distance: Int? = null,        // Distance to stop in meters
    val originLat: Double? = null,    // Exact pier latitude
    val originLon: Double? = null     // Exact pier longitude
) {
    fun hasDelay(): Boolean = delay != null && delay > 0
    
    fun getDisplayTime(): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        return plannedTime.format(formatter)
    }
}

data class FerryLine(
    val name: String,           // "62"
    val direction: String,      // "Finkenwerder"
    val origin: String = "",    // "Landungsbrücken Brücke 3"
    val type: String,           // "ship"
    val id: String
)

data class Announcement(
    val id: String,
    val summary: String,
    val description: String?,
    val validFrom: LocalDateTime?,
    val validTo: LocalDateTime?
)
