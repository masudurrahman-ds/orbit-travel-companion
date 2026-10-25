package com.crowncouncil.orbit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destination ORDER BY name")
    fun all(): Flow<List<Destination>>

    @Query("SELECT COUNT(*) FROM destination")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Destination>)
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityOption>)

    @Query("SELECT * FROM activity ORDER BY id")
    fun all(): Flow<List<ActivityOption>>

    @Query("SELECT * FROM activity WHERE destinationId = :destinationId ORDER BY id")
    suspend fun forDestination(destinationId: Long): List<ActivityOption>
}

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Query("SELECT * FROM trip ORDER BY id DESC")
    fun all(): Flow<List<Trip>>

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
abstract class ItineraryDao {
    @Insert
    abstract suspend fun insertAll(items: List<ItineraryItem>)

    @Query("DELETE FROM itinerary_item WHERE tripId = :tripId")
    abstract suspend fun clear(tripId: Long)

    @Query(
        "SELECT i.id AS id, i.dayNo AS dayNo, i.startHour AS startHour, i.isFixed AS isFixed, " +
            "a.id AS activityId, a.name AS name, a.category AS category, " +
            "a.estimatedCost AS estimatedCost, a.isIndoor AS isIndoor " +
            "FROM itinerary_item i JOIN activity a ON a.id = i.activityId " +
            "WHERE i.tripId = :tripId ORDER BY i.dayNo, i.startHour"
    )
    abstract fun rows(tripId: Long): Flow<List<ItineraryRow>>

    @Transaction
    open suspend fun replace(tripId: Long, items: List<ItineraryItem>) {
        clear(tripId)
        insertAll(items)
    }
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Query("SELECT * FROM expense WHERE tripId = :tripId ORDER BY id DESC")
    fun forTrip(tripId: Long): Flow<List<Expense>>
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visit ORDER BY visitedAt DESC")
    fun all(): Flow<List<Visit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: Visit)

    @Query("DELETE FROM visit WHERE destinationId = :destinationId")
    suspend fun delete(destinationId: Long)

    @Query("DELETE FROM visit")
    suspend fun clear()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_note ORDER BY id")
    fun all(): Flow<List<MemoryNote>>

    @Insert
    suspend fun insert(note: MemoryNote)

    @Query("SELECT COUNT(*) FROM memory_note")
    suspend fun count(): Int

    @Query("DELETE FROM memory_note WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memory_note")
    suspend fun clear()
}
