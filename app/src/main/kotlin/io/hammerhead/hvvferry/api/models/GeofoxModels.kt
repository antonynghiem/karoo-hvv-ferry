package io.hammerhead.hvvferry.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Init
@Serializable
data class InitRequest(
    val version: Int = 63
)

@Serializable
data class InitResponse(
    val returnCode: String,
    val beginOfService: String,
    val endOfService: String,
    val id: String,
    val dataId: String,
    val buildDate: String,
    val buildTime: String,
    val buildText: String
)

// CheckName (find stations by name or GPS)
@Serializable
data class CheckNameRequest(
    val version: Int = 63,
    val theName: SDName,
    val maxList: Int = 10,
    val maxDistance: Int? = null,  // For GPS search, max distance in meters
    val coordinateType: String = "EPSG_4326",
    val tariffDetails: Boolean = false,
    val allowTypeSwitch: Boolean = true
)

@Serializable
data class CheckNameResponse(
    val returnCode: String,
    val results: List<RegionalSDName>
)

// DepartureList
@Serializable
data class DepartureListRequest(
    val version: Int = 63,
    val station: SDName,
    val time: GTITime,
    val maxList: Int = 10,
    val maxTimeOffset: Int = 120,  // minutes
    val useRealtime: Boolean = true,
    val returnFilters: Boolean = true
)

@Serializable
data class DepartureListResponse(
    val returnCode: String,
    val departures: List<DepartureData>,
    val time: String? = null
)

@Serializable
data class DepartureData(
    val line: Line,
    val timeOffset: Int,
    val delay: Int? = null,
    val cancelled: Boolean = false,
    val extra: Boolean = false,
    val platform: String? = null,
    val serviceId: Int
)

// Common data structures
@Serializable
data class SDName(
    val name: String? = null,
    val city: String? = null,
    val combinedName: String? = null,
    val id: String? = null,
    val type: String? = null,  // "STATION", "ADDRESS", "POI", "COORDINATE", "UNKNOWN"
    val coordinate: Coordinate? = null,
    val layer: Int? = null,
    val tariffDetails: TariffDetails? = null,
    val serviceTypes: List<String>? = null,
    val hasStationInformation: Boolean? = null
)

@Serializable
data class RegionalSDName(
    val name: String,
    val city: String? = null,
    val combinedName: String? = null,
    val id: String,
    val type: String,
    val coordinate: Coordinate? = null,
    val serviceTypes: List<String>? = null,
    val distance: Int? = null,  // meters
    val time: Int? = null       // walking minutes
)

@Serializable
data class Coordinate(
    val x: Double,  // longitude
    val y: Double   // latitude
)

@Serializable
data class GTITime(
    val date: String,  // "heute", "morgen", or "dd.MM.yyyy"
    val time: String   // "jetzt", "HH:mm", or "HH-HH"
)

@Serializable
data class Line(
    val name: String,
    val direction: String,
    val type: ServiceType,
    val id: String
)

@Serializable
enum class ServiceType {
    @SerialName("ship") SHIP,
    @SerialName("bus") BUS,
    @SerialName("u") SUBWAY,
    @SerialName("s") S_TRAIN,
    @SerialName("r") REGIONAL_TRAIN,
    @SerialName("train") TRAIN,
    @SerialName("callable") CALLABLE,
    @SerialName("ast") AST
}

@Serializable
data class TariffDetails(
    val innerCity: Boolean? = null,
    val greaterArea: Boolean? = null,
    val tariffZones: List<Int>? = null,
    val rings: List<String>? = null,
    val counties: List<String>? = null
)

// GetAnnouncements
@Serializable
data class GetAnnouncementsRequest(
    val version: Int = 63,
    val filterByStopRef: List<String>? = null,
    val filterByLines: List<LineFilter>? = null
)

@Serializable
data class LineFilter(
    val name: String,
    val type: String = "ship"
)

@Serializable
data class GetAnnouncementsResponse(
    val returnCode: String,
    val announcements: List<AnnouncementData>
)

@Serializable
data class AnnouncementData(
    val id: String,
    val summary: String,
    val description: String? = null,
    val publicationTime: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null
)
