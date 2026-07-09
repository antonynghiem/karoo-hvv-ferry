package dev.antonyng.hvvferry.api

import timber.log.Timber
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Geofox API HMAC-SHA1 authentication
 */
object GeofoxAuth {
    
    /**
     * Generate HMAC-SHA1 signature for Geofox API request
     * 
     * @param requestBody The JSON request body as string
     * @param password The Geofox API password
     * @return Base64-encoded signature
     */
    fun generateSignature(requestBody: String, password: String): String {
        try {
            val mac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA1")
            mac.init(secretKey)
            
            val bytes = mac.doFinal(requestBody.toByteArray(Charsets.UTF_8))
            val signature = Base64.getEncoder().encodeToString(bytes)
            
            Timber.d("Generated signature for body length: ${requestBody.length}")
            return signature
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate HMAC-SHA1 signature")
            throw GeofoxAuthException("Failed to generate authentication signature", e)
        }
    }
    
    /**
     * Create authentication headers for Geofox API request
     */
    fun createAuthHeaders(
        username: String,
        requestBody: String,
        password: String
    ): Map<String, String> {
        val signature = generateSignature(requestBody, password)
        
        return mapOf(
            "geofox-auth-type" to "HmacSHA1",
            "geofox-auth-user" to username,
            "geofox-auth-signature" to signature
        )
    }
}

class GeofoxAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
