package com.crowncouncil.orbit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Destination::class, ActivityOption::class, Trip::class, ItineraryItem::class, Expense::class, Visit::class, MemoryNote::class, ChatTurn::class],
    version = 4,
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

    abstract fun chatTurns(): ChatTurnDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_turn (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromUser INTEGER NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_turn_createdAt ON chat_turn(createdAt)")
            }
        }

        @Volatile private var instance: OrbitDatabase? = null

        fun get(context: Context): OrbitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, OrbitDatabase::class.java, "orbit.db"
                )
                    // Prototype: rebuild the local database when the schema changes. Replace with real migrations before release.
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
