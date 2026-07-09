package dev.antonyng.hvvferry.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonyng.hvvferry.data.models.FerryConfig
import dev.antonyng.hvvferry.data.models.RouteFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "ferry_preferences"
        private const val KEY_CONFIG = "ferry_config"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<FerryConfig> = _configFlow.asStateFlow()
    
    /**
     * Get current configuration
     */
    fun getConfig(): FerryConfig {
        return _configFlow.value
    }
    
    /**
     * Save configuration
     */
    fun saveConfig(config: FerryConfig) {
        val jsonString = json.encodeToString(config)
        prefs.edit().putString(KEY_CONFIG, jsonString).apply()
        _configFlow.value = config
        Timber.d("Configuration saved: $config")
    }
    
    /**
     * Load configuration from preferences
     */
    private fun loadConfig(): FerryConfig {
        val jsonString = prefs.getString(KEY_CONFIG, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode config, using default")
                FerryConfig()
            }
        } else {
            FerryConfig()
        }
    }
    
    // Convenience methods
    fun getProximityRadius(): Int = getConfig().proximityRadiusMeters
    
    fun getEnabledFerryLines(): Set<String> = getConfig().enabledFerryLines
    
    fun getRouteFormat(): RouteFormat = getConfig().routeFormat
    
    fun isGpsEnabled(): Boolean = getConfig().gpsAutoDetectionEnabled
    
    fun showTwoDepartures(): Boolean = getConfig().showTwoDepartures
    
    fun getUpdateInterval(): Int = getConfig().updateIntervalSeconds
    
    fun getManualStopId(): String? = getConfig().manualStopId
}
