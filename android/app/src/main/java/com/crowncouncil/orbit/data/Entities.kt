package com.crowncouncil.orbit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "destination")
data class Destination(
    @PrimaryKey val id: Long,
    val name: String,
    val district: String,
    val division: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val tags: String,          // comma separated: beach,food
    val distanceKm: Int,       // approximate distance from the traveller's origin
    val costPerDayBdt: Int,    // per person, per day
    val recommendedDays: Int,
    val bestMonths: String,    // comma separated month numbers
    val blurb: String
)

@Entity(
    tableName = "activity",
    foreignKeys = [ForeignKey(Destination::class, ["id"], ["destinationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("destinationId")]
)
data class ActivityOption(
    @PrimaryKey val id: Long,
    val destinationId: Long,
    val name: String,
    val category: String,
    val estimatedCost: Int,    // per person
    val isIndoor: Boolean,
    val durationHours: Int
)

@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val destinationId: Long,
    val startMonth: Int,
    val days: Int,
    val budget: Int,
    val travellers: Int,
    val interests: String,
    val status: String = "PLANNED",
    val tier: String = "VALUE"
)

@Entity(
    tableName = "itinerary_item",
    foreignKeys = [
        ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ActivityOption::class, ["id"], ["activityId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tripId"), Index("activityId")]
)
data class ItineraryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val activityId: Long,
    val dayNo: Int,
    val startHour: Int,
    val isFixed: Boolean = false
)

@Entity(
    tableName = "expense",
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val category: String,
    val amount: Int,
    val currency: String = "BDT",
    val note: String = ""
)

/** Join result used by the itinerary screen. */
data class ItineraryRow(
    val id: Long,
    val dayNo: Int,
    val startHour: Int,
    val isFixed: Boolean,
    val activityId: Long,
    val name: String,
    val category: String,
    val estimatedCost: Int,
    val isIndoor: Boolean
)

/** A place the traveller marked as visited. One row per destination. */
@Entity(tableName = "visit")
data class Visit(
    @PrimaryKey val destinationId: Long,
    val visitedAt: Long,         // epoch millis
    val rating: Int? = null,     // 1-5, optional
    val note: String = "",
    val source: String = "self"  // "self" or "gps"
)

/** A preference or memory the traveller told the guide. Stored only on this device. */
@Entity(tableName = "memory_note")
data class MemoryNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
