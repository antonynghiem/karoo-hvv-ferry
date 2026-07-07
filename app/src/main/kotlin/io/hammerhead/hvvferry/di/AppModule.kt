package io.hammerhead.hvvferry.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.hammerhead.hvvferry.api.GeofoxClient
import io.hammerhead.hvvferry.data.preferences.CredentialManager
import io.hammerhead.hvvferry.data.preferences.PreferencesManager
import io.hammerhead.hvvferry.utils.DisplayFormatter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideGeofoxClient(): GeofoxClient {
        return GeofoxClient()
    }
    
    @Provides
    @Singleton
    fun provideCredentialManager(
        @ApplicationContext context: Context
    ): CredentialManager {
        return CredentialManager(context)
    }
    
    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }
    
    @Provides
    @Singleton
    fun provideDisplayFormatter(): DisplayFormatter {
        return DisplayFormatter()
    }
}
