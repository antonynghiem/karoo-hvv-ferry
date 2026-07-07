package io.hammerhead.hvvferry.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
    
    /**
     * Save API credentials (encrypted)
     */
    fun saveCredentials(username: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
        Timber.d("Credentials saved securely")
    }
    
    /**
     * Get API username
     */
    fun getUsername(): String? {
        return encryptedPrefs.getString(KEY_USERNAME, null)
    }
    
    /**
     * Get API password
     */
    fun getPassword(): String? {
        return encryptedPrefs.getString(KEY_PASSWORD, null)
    }
    
    /**
     * Check if credentials are stored
     */
    fun hasCredentials(): Boolean {
        return !getUsername().isNullOrBlank() && !getPassword().isNullOrBlank()
    }
    
    /**
     * Clear stored credentials
     */
    fun clearCredentials() {
        encryptedPrefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
        Timber.d("Credentials cleared")
    }
}
