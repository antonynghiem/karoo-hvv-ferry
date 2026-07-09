package dev.antonyng.hvvferry.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antonyng.hvvferry.api.GeofoxClient
import dev.antonyng.hvvferry.data.preferences.CredentialManager
import dev.antonyng.hvvferry.data.preferences.PreferencesManager
import dev.antonyng.hvvferry.utils.DisplayFormatter
import io.hammerhead.karooext.KarooSystemService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideKarooSystemService(
        @ApplicationContext context: Context
    ): KarooSystemService {
        return KarooSystemService(context)
    }

    @Provides
    @Singleton
    fun provideGeofoxClient(karooSystem: KarooSystemService): GeofoxClient {
        return GeofoxClient(karooSystem)
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
