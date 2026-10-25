package com.crowncouncil.orbit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Destination::class, ActivityOption::class, Trip::class, ItineraryItem::class, Expense::class, Visit::class, MemoryNote::class],
    version = 3,
    exportSchema = false
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun destinations(): DestinationDao
    abstract fun activities(): ActivityDao
    abstract fun trips(): TripDao
    abstract fun itinerary(): ItineraryDao
    abstract fun expenses(): ExpenseDao
    abstract fun visits(): VisitDao
    abstract fun notes(): MemoryDao

    companion object {
        @Volatile private var instance: OrbitDatabase? = null

        fun get(context: Context): OrbitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, OrbitDatabase::class.java, "orbit.db"
                )
                    // Prototype: rebuild the local database when the schema changes. Replace with real migrations before release.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
