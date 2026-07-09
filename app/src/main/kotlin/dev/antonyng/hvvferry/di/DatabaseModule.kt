package dev.antonyng.hvvferry.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.antonyng.hvvferry.data.database.FerryDatabase
import dev.antonyng.hvvferry.data.database.FerryStopDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideFerryDatabase(
        @ApplicationContext context: Context
    ): FerryDatabase {
        return Room.databaseBuilder(
            context,
            FerryDatabase::class.java,
            "ferry_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    @Singleton
    fun provideFerryStopDao(database: FerryDatabase): FerryStopDao {
        return database.ferryStopDao()
    }
}
