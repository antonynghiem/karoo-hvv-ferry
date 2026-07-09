package dev.antonyng.hvvferry.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import dev.antonyng.hvvferry.data.database.Converters

@Entity(tableName = "ferry_stops")
@TypeConverters(Converters::class)
data class FerryStop(
    @PrimaryKey
    val stationId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val ferryLines: List<String>,  // e.g., ["62", "73"]
    val lastUpdated: Long = System.currentTimeMillis()
)

data class FerryStopWithDistance(
    val stop: FerryStop,
    val distance: Int  // meters
)
