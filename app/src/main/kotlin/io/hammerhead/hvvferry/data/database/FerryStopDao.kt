package io.hammerhead.hvvferry.data.database

import androidx.room.*
import io.hammerhead.hvvferry.data.models.FerryStop
import kotlinx.coroutines.flow.Flow

@Dao
interface FerryStopDao {
    
    @Query("SELECT * FROM ferry_stops ORDER BY name ASC")
    fun getAllStops(): Flow<List<FerryStop>>
    
    @Query("SELECT * FROM ferry_stops ORDER BY name ASC")
    suspend fun getAllStopsSync(): List<FerryStop>
    
    @Query("SELECT * FROM ferry_stops WHERE stationId = :stationId")
    suspend fun getStopById(stationId: String): FerryStop?
    
    @Query("SELECT * FROM ferry_stops WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchStops(query: String): List<FerryStop>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: FerryStop)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<FerryStop>)
    
    @Query("DELETE FROM ferry_stops")
    suspend fun deleteAllStops()
    
    @Query("SELECT COUNT(*) FROM ferry_stops")
    suspend fun getStopCount(): Int
}
