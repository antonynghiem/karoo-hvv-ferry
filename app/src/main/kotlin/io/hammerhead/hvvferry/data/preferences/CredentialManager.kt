package io.hammerhead.hvvferry.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages API credentials with secure storage.
 * 
 * Battery optimization: Credentials are cached in memory after first read to avoid
 * repeated AES-256-GCM decryption operations on every API call. The encrypted storage
 * is only accessed on first read or after credentials are updated/cleared.
 */
@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILENAME = "ferry_credentials"
        private const val KEY_USERNAME = "geofox_username"
        private const val KEY_PASSWORD = "geofox_password"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // Battery optimization: Cache decrypted credentials in memory to avoid
    // repeated AES-256-GCM decryption on every polling cycle (1,440x/day at 60s interval)
    @Volatile private var cachedUsername: String? = null
    @Volatile private var cachedPassword: String? = null
    @Volatile private var cacheInitialized = false
    
    /**
     * Save API credentials (encrypted)
     */
    fun saveCredentials(username: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
        // Update cache immediately so next read doesn't hit encrypted storage
        cachedUsername = username
        cachedPassword = password
        cacheInitialized = true
        Timber.d("Credentials saved securely")
    }
    
    /**
     * Get API username.
     * Battery optimization: Returns cached value if available, avoiding decryption.
     */
    fun getUsername(): String? {
        ensureCacheInitialized()
        return cachedUsername
    }
    
    /**
     * Get API password.
     * Battery optimization: Returns cached value if available, avoiding decryption.
     */
    fun getPassword(): String? {
        ensureCacheInitialized()
        return cachedPassword
    }
    
    /**
     * Check if credentials are stored.
     * Battery optimization: Uses cached values.
     */
    fun hasCredentials(): Boolean {
        ensureCacheInitialized()
        return !cachedUsername.isNullOrBlank() && !cachedPassword.isNullOrBlank()
    }
    
    /**
     * Clear stored credentials
     */
    fun clearCredentials() {
        encryptedPrefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
        // Clear cache
        cachedUsername = null
        cachedPassword = null
        cacheInitialized = true  // Mark as initialized (with null values)
        Timber.d("Credentials cleared")
    }
    
    /**
     * Initialize cache from encrypted storage if not already done.
     * Thread-safe via double-checked locking pattern with volatile.
     */
    private fun ensureCacheInitialized() {
        if (!cacheInitialized) {
            synchronized(this) {
                if (!cacheInitialized) {
                    cachedUsername = encryptedPrefs.getString(KEY_USERNAME, null)
                    cachedPassword = encryptedPrefs.getString(KEY_PASSWORD, null)
                    cacheInitialized = true
                    Timber.d("Credentials cache initialized")
                }
            }
        }
    }
}
