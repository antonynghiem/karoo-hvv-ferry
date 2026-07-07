package io.hammerhead.hvvferry.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.hammerhead.hvvferry.data.models.FerryStop

@Database(
    entities = [FerryStop::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FerryDatabase : RoomDatabase() {
    abstract fun ferryStopDao(): FerryStopDao
}
