import Foundation

struct Weights {
    let budget, interest, time, weather, route, preference: Double
    var sum: Double { budget + interest + time + weather + route + preference }
}

/// budget: cheapest sensible trip. value: affordable but good quality (the proposal's default weights).
/// fancy: comfort and experience first. Same numbers as the Android and backend planners.
enum Tier: String, CaseIterable, Identifiable {
    case budget, value, fancy
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
    var costFactor: Double {
        switch self {
        case .budget: return 0.7
        case .value: return 1.0
        case .fancy: return 1.8
        }
    }
    var weights: Weights {
        switch self {
        case .budget: return Weights(budget: 0.34, interest: 0.20, time: 0.12, weather: 0.14, route: 0.12, preference: 0.08)
        case .value: return Weights(budget: 0.22, interest: 0.20, time: 0.18, weather: 0.18, route: 0.12, preference: 0.10)
        case .fancy: return Weights(budget: 0.06, interest: 0.28, time: 0.16, weather: 0.22, route: 0.08, preference: 0.20)
        }
    }
    var blurb: String {
        switch self {
        case .budget: return "Lowest sensible cost: simple stays, local food, low-cost activities."
        case .value: return "Affordable but good quality: the best experience per taka."
        case .fancy: return "Comfort first: premium stays and experiences."
        }
    }
}

struct PlanQuery {
    var budget = 25_000
    var travellers = 2
    var days = 3
    var month = Calendar.current.component(.month, from: Date())
    var interests: Set<String> = ["beach", "food"]
    var tier: Tier = .value
}

struct ScoreBreakdown {
    let total, budget, interest, time, weather, route, preference: Double
    let estimatedCost: Int
    let affordable: Bool
    var percent: Int { Int((total * 100).rounded()) }
}

struct RankedDestination: Identifiable {
    let destination: Destination
    let score: ScoreBreakdown
    var id: Int { destination.id }
}

/// Weighted scoring model: S = sum(w_i * f_i), every f_i in [0, 1], weights sum to 1.
/// Mirrors the Android ScoringEngine.kt so both apps rank identically.
enum ScoringEngine {
    private static let maxDistanceKm = 600.0

    static func score(_ d: Destination, _ q: PlanQuery, favorite: Bool = false) -> ScoreBreakdown {
        let w = q.tier.weights
        let tripCost = Double(d.costPerDayBdt) * q.tier.costFactor * Double(q.days * q.travellers)
        let budget = tripCost <= 0 ? 1.0 : min(1.0, max(0.0, Double(q.budget) / tripCost))

        let interest = q.interests.isEmpty
            ? 0.5
            : Double(d.tagSet.intersection(q.interests).count) / Double(q.interests.count)

        let time = 1.0 - min(1.0, Double(abs(q.days - d.recommendedDays)) / Double(max(q.days, d.recommendedDays)))
        let weather = d.monthSet.contains(q.month) ? 1.0 : 0.4
        let route = 1.0 - min(1.0, Double(d.distanceKm) / maxDistanceKm)
        let preference = favorite ? 1.0 : 0.5

        let total = w.budget * budget + w.interest * interest + w.time * time
            + w.weather * weather + w.route * route + w.preference * preference
        return ScoreBreakdown(total: total, budget: budget, interest: interest, time: time, weather: weather,
                              route: route, preference: preference, estimatedCost: Int(tripCost.rounded()), affordable: budget >= 0.8)
    }

    /// Budget and value tours list affordable places first; fancy tours rank on score alone.
    static func rank(_ destinations: [Destination], _ q: PlanQuery, favorites: Set<Int> = []) -> [RankedDestination] {
        let rows = destinations.map { RankedDestination(destination: $0, score: score($0, q, favorite: favorites.contains($0.id))) }
        return rows.sorted { a, b in
            if q.tier != .fancy, a.score.affordable != b.score.affordable { return a.score.affordable }
            if a.score.total != b.score.total { return a.score.total > b.score.total }
            return a.destination.id < b.destination.id
        }
    }
}

enum ItineraryGenerator {
    static let slots = [9, 13, 16]

    /// Three slots per day, interest matches first. Within that, budget picks the cheapest activities,
    /// fancy the priciest, value the most hours of experience per taka. Rainy days only get indoor activities.
    static func generate(tripId: UUID, days: Int, activities: [ActivityOption], interests: Set<String>,
                         rainyDays: Set<Int> = [], tier: Tier = .value) -> [ItineraryItem] {
        func key(_ a: ActivityOption) -> Double {
            switch tier {
            case .budget: return Double(a.estimatedCost)
            case .fancy: return -Double(a.estimatedCost)
            case .value: return -(Double(a.durationHours) / Double(a.estimatedCost + 100))
            }
        }
        let ranked = activities.sorted { a, b in
            let ra = interests.contains(a.category) ? 1 : 0
            let rb = interests.contains(b.category) ? 1 : 0
            if ra != rb { return ra > rb }
            if key(a) != key(b) { return key(a) < key(b) }
            return a.id < b.id
        }
        var used = Set<Int>()
        var result: [ItineraryItem] = []
        for day in 1...max(days, 1) {
            for hour in slots {
                guard let pick = ranked.first(where: { !used.contains($0.id) && (!rainyDays.contains(day) || $0.isIndoor) })
                else { continue }
                used.insert(pick.id)
                result.append(ItineraryItem(tripId: tripId, activityId: pick.id, dayNo: day, startHour: hour))
            }
        }
        return result
    }
}
