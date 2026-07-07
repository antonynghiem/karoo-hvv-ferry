package io.hammerhead.hvvferry.extension

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.hvvferry.api.GeofoxClient
import io.hammerhead.hvvferry.data.preferences.CredentialManager
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.data.repository.FerryRepository
import io.hammerhead.hvvferry.ui.FerryTimesActivity
import timber.log.Timber
import javax.inject.Inject

/**
 * HVV Ferry Extension for Hammerhead Karoo
 * 
 * Battery Optimization Architecture:
 * - Polling logic moved to FerryDataType (DataTypeImpl)
 * - Only polls when data field is VISIBLE on screen
 * - Automatically stops when user switches screens
 * - Implements smart caching, service hours, and network checks
 */
@AndroidEntryPoint
class HvvFerryExtension : KarooExtension("hvv-ferry", "1.0.0") {
    
    @Inject
    lateinit var repository: FerryRepository
    
    @Inject
    lateinit var credentialManager: CredentialManager
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var viewProvider: FerryViewProvider
    
    @Inject
    lateinit var geofoxClient: GeofoxClient
    
    /**
     * Provide data type implementations.
     * Battery optimization: Polling logic is in FerryDataType, not here!
     */
    override val types by lazy {
        listOf(
            FerryDataType(
                extension = extension,
                context = applicationContext,
                repository = repository,
                credentialManager = credentialManager,
                preferencesManager = preferencesManager,
                viewProvider = viewProvider
            )
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("🚢 HVV Ferry Extension created")
        Timber.d("🔋 Battery optimization: Polling only happens when data field is visible!")
        
        if (!credentialManager.hasCredentials()) {
            Timber.w("⚠️ No credentials configured - please set up in settings")
        }
    }
    
    override fun onDestroy() {
        Timber.d("🛑 HVV Ferry Extension destroyed")
        // Battery optimization: Close HTTP client to release network resources
        geofoxClient.close()
        super.onDestroy()
    }
    
    /**
     * Handle bonus action to show full ferry times screen
     */
    override fun onBonusAction(actionId: String) {
        super.onBonusAction(actionId)
        
        if (actionId == "show-ferry-times") {
            Timber.d("🚀 Launching ferry times activity")
            val intent = Intent(applicationContext, FerryTimesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }
}
