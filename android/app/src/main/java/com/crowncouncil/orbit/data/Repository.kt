package com.crowncouncil.orbit.data

class Repository(private val db: OrbitDatabase) {
    val destinations = db.destinations().all()
    val trips = db.trips().all()
    val visits = db.visits().all()
    val notes = db.notes().all()
    val activities = db.activities().all()

    fun rows(tripId: Long) = db.itinerary().rows(tripId)
    fun expenses(tripId: Long) = db.expenses().forTrip(tripId)

    suspend fun seedIfEmpty() {
        if (db.destinations().count() == 0) {
            db.destinations().insertAll(Seed.destinations)
            db.activities().insertAll(Seed.activities)
        }
    }

    suspend fun activitiesFor(destinationId: Long) = db.activities().forDestination(destinationId)
    suspend fun createTrip(trip: Trip): Long = db.trips().insert(trip)
    suspend fun replaceItinerary(tripId: Long, items: List<ItineraryItem>) = db.itinerary().replace(tripId, items)
    suspend fun addExpense(expense: Expense) = db.expenses().insert(expense)
    suspend fun deleteTrip(id: Long) = db.trips().delete(id)
    suspend fun saveVisit(v: Visit) = db.visits().upsert(v)
    suspend fun removeVisit(destinationId: Long) = db.visits().delete(destinationId)
    suspend fun clearVisits() = db.visits().clear()
    suspend fun addDestinations(d: List<Destination>, a: List<ActivityOption>) { db.destinations().insertAll(d); db.activities().insertAll(a) }
    suspend fun addNote(text: String): Boolean { if (db.notes().count() >= 50) return false; db.notes().insert(MemoryNote(text = text.take(200))); return true }
    suspend fun deleteNote(id: Long) = db.notes().delete(id)
    suspend fun clearNotes() = db.notes().clear()
    suspend fun saveItems(items: List<ItineraryItem>) = db.itinerary().insertAll(items)
}
