package dev.antonyng.hvvferry.api

import dev.antonyng.hvvferry.api.models.*
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class GeofoxClient @Inject constructor(
    private val karooSystem: KarooSystemService
) {

    companion object {
        private const val BASE_URL = "https://gti.geofox.de/gti/public"
        private const val TIMEOUT_SECONDS = 15L
    }

    @Volatile private var isConnected = false

    fun ensureConnected() {
        if (!isConnected) {
            karooSystem.connect { connected ->
                isConnected = connected
                if (!connected) Timber.w("GeofoxClient: KarooSystemService connection failed")
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Make an authenticated POST request via the Karoo SDK HTTP client.
     * This routes through the Karoo OS network layer (Bluetooth companion or WiFi),
     * matching the approach used by other Karoo extensions such as karoo-headwind.
     */
    private suspend inline fun <reified TRequest, reified TResponse> makeRequest(
        endpoint: String,
        requestBody: TRequest,
        username: String,
        password: String
    ): TResponse {
        val bodyJson = json.encodeToString(
            kotlinx.serialization.serializer<TRequest>(),
            requestBody
        )

        ensureConnected()

        val authHeaders = GeofoxAuth.createAuthHeaders(username, bodyJson, password)

        val headers = authHeaders + mapOf("Content-Type" to "application/json")

        Timber.d("Making request to: $endpoint body=$bodyJson")

        val response = callbackFlow {
            val listenerId = karooSystem.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    method = "POST",
                    url = "$BASE_URL/$endpoint",
                    headers = headers,
                    body = bodyJson.toByteArray(Charsets.UTF_8),
                    waitForConnection = false,
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete) {
                        trySend(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    close(GeofoxApiException("HTTP error: $err"))
                }
            )
            awaitClose { karooSystem.removeConsumer(listenerId) }
        }.timeout(TIMEOUT_SECONDS.seconds).single()

        if (response.error != null) {
            throw GeofoxApiException("Request failed: ${response.error}")
        }
        if (response.statusCode !in 200..299) {
            throw GeofoxApiException("HTTP ${response.statusCode} for $endpoint")
        }

        val responseBody = response.body
            ?: throw GeofoxApiException("Empty response body for $endpoint")

        val bodyString = responseBody.toString(Charsets.UTF_8)
        Timber.d("Response body for $endpoint: ${bodyString.take(500)}")

        return try {
            json.decodeFromString(
                kotlinx.serialization.serializer<TResponse>(),
                bodyString
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse response for $endpoint")
            throw GeofoxApiException("Failed to parse response: ${e.message}", e)
        }
    }

    /**
     * Test API connection with init request
     */
    suspend fun init(username: String, password: String): InitResponse {
        return makeRequest(
            endpoint = "init",
            requestBody = InitRequest(),
            username = username,
            password = password
        )
    }

    /**
     * Find stations by name or GPS coordinates
     */
    suspend fun checkName(
        request: CheckNameRequest,
        username: String,
        password: String
    ): CheckNameResponse {
        return makeRequest(
            endpoint = "checkName",
            requestBody = request,
            username = username,
            password = password
        )
    }

    /**
     * Get departure times for a station
     */
    suspend fun getDepartureList(
        request: DepartureListRequest,
        username: String,
        password: String
    ): DepartureListResponse {
        return makeRequest(
            endpoint = "departureList",
            requestBody = request,
            username = username,
            password = password
        )
    }

    /**
     * Get service announcements
     */
    suspend fun getAnnouncements(
        request: GetAnnouncementsRequest,
        username: String,
        password: String
    ): GetAnnouncementsResponse {
        return makeRequest(
            endpoint = "getAnnouncements",
            requestBody = request,
            username = username,
            password = password
        )
    }
}

class GeofoxApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
