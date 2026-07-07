package io.hammerhead.hvvferry.api

import io.hammerhead.hvvferry.api.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofoxClient @Inject constructor() {
    
    companion object {
        private const val BASE_URL = "https://gti.geofox.de/gti/public"
        // Battery optimization: Reduced timeout from 30s to 15s
        private const val TIMEOUT_MS = 15000L
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = false
    }
    
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Timber.tag("GeofoxAPI").d(message)
                }
            }
            level = LogLevel.INFO
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
        
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }
    
    /**
     * Make authenticated request to Geofox API
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
        
        val authHeaders = GeofoxAuth.createAuthHeaders(username, bodyJson, password)
        
        Timber.d("Making request to: $endpoint")
        
        return try {
            client.post("$BASE_URL/$endpoint") {
                setBody(requestBody)
                authHeaders.forEach { (key, value) ->
                    header(key, value)
                }
            }.body()
        } catch (e: Exception) {
            Timber.e(e, "API request failed: $endpoint")
            throw GeofoxApiException("API request failed: ${e.message}", e)
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
    
    fun close() {
        client.close()
    }
}

class GeofoxApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
