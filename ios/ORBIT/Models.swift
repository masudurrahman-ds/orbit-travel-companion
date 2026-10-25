import Foundation
import SwiftData
import Observation

@Model
final class Destination {
    @Attribute(.unique) var id: Int
    var name: String
    var district: String
    var division: String
    var country: String
    var latitude: Double
    var longitude: Double
    var tags: String            // comma separated: beach,food
    var distanceKm: Int         // approximate distance from the traveller's origin
    var costPerDayBdt: Int      // per person, per day
    var recommendedDays: Int
    var bestMonths: String      // comma separated month numbers
    var blurb: String

    init(id: Int, name: String, district: String, division: String, country: String, latitude: Double, longitude: Double, tags: String,
         distanceKm: Int, costPerDayBdt: Int, recommendedDays: Int, bestMonths: String, blurb: String) {
        self.id = id; self.name = name; self.district = district; self.division = division; self.country = country
        self.latitude = latitude; self.longitude = longitude; self.tags = tags
        self.distanceKm = distanceKm; self.costPerDayBdt = costPerDayBdt
        self.recommendedDays = recommendedDays; self.bestMonths = bestMonths; self.blurb = blurb
    }

    var tagSet: Set<String> { Set(tags.split(separator: ",").map(String.init)) }
    var monthSet: Set<Int> { Set(bestMonths.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }) }
}

@Model
final class ActivityOption {
    @Attribute(.unique) var id: Int
    var destinationId: Int
    var name: String
    var category: String
    var estimatedCost: Int      // per person
    var isIndoor: Bool
    var durationHours: Int

    init(id: Int, destinationId: Int, name: String, category: String, estimatedCost: Int, isIndoor: Bool, durationHours: Int) {
        self.id = id; self.destinationId = destinationId; self.name = name; self.category = category
        self.estimatedCost = estimatedCost; self.isIndoor = isIndoor; self.durationHours = durationHours
    }
}

@Model
final class Trip {
    @Attribute(.unique) var id: UUID
    var title: String
    var destinationId: Int
    var startMonth: Int
    var days: Int
    var budget: Int
    var travellers: Int
    var interests: String
    var tier: String
    var createdAt: Date

    init(title: String, destinationId: Int, startMonth: Int, days: Int, budget: Int, travellers: Int, interests: String, tier: String = "value") {
        self.id = UUID(); self.title = title; self.destinationId = destinationId; self.startMonth = startMonth
        self.days = days; self.budget = budget; self.travellers = travellers; self.interests = interests
        self.tier = tier; self.createdAt = Date()
    }

    var interestSet: Set<String> { Set(interests.split(separator: ",").map(String.init)) }
    var tierValue: Tier { Tier(rawValue: tier) ?? .value }
}

@Model
final class ItineraryItem {
    var tripId: UUID
    var activityId: Int
    var dayNo: Int
    var startHour: Int
    var isFixed: Bool

    init(tripId: UUID, activityId: Int, dayNo: Int, startHour: Int, isFixed: Bool = false) {
        self.tripId = tripId; self.activityId = activityId; self.dayNo = dayNo
        self.startHour = startHour; self.isFixed = isFixed
    }
}

@Model
final class Expense {
    var tripId: UUID
    var category: String
    var amount: Int
    var currency: String
    var note: String
    var createdAt: Date

    init(tripId: UUID, category: String, amount: Int, note: String, currency: String = "BDT") {
        self.tripId = tripId; self.category = category; self.amount = amount
        self.note = note; self.currency = currency; self.createdAt = Date()
    }
}

/// A place the traveller marked as visited. One row per destination.
@Model
final class Visit {
    @Attribute(.unique) var destinationId: Int
    var visitedAt: Date
    var rating: Int?
    var note: String
    var source: String   // "self" or "gps"

    init(destinationId: Int, rating: Int? = nil, note: String = "", source: String = "self") {
        self.destinationId = destinationId; self.visitedAt = Date(); self.rating = rating
        self.note = String(note.prefix(300)); self.source = source
    }
}

/// A preference or memory the traveller told the guide. Stored only on this device.
@Model
final class MemoryNote {
    var text: String
    var createdAt: Date
    init(text: String) { self.text = String(text.prefix(200)); self.createdAt = Date() }
}

/// UI state shared by all tabs (plan inputs, favourites, current trip, simulated rain days).
@Observable
final class PlannerState {
    var query = PlanQuery()
    var favorites: Set<Int> = []
    var currentTripId: UUID?
    var rainyDays: [UUID: Set<Int>] = [:]
}
