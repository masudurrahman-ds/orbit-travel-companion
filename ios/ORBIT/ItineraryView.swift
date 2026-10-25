import SwiftUI
import SwiftData

struct ItineraryView: View {
    @Environment(\.modelContext) private var context
    @Environment(PlannerState.self) private var planner
    @Query(sort: \Trip.createdAt, order: .reverse) private var trips: [Trip]
    @Query private var activities: [ActivityOption]
    @Query private var allItems: [ItineraryItem]
    @State private var selectedDay = 1
    var embedded = false

    var body: some View {
        NavWrap(embedded: embedded) {
            ScrollView {
                if let trip = currentTrip(trips, planner) {
                    content(for: trip)
                } else {
                    InfoCard(title: "No trip yet", message: "Plan a trip first, then its day-by-day schedule appears here.").padding(16)
                }
            }
            .navigationTitle(currentTrip(trips, planner)?.title ?? "Itinerary")
        }
    }

    @ViewBuilder
    private func content(for trip: Trip) -> some View {
        let day = min(max(selectedDay, 1), trip.days)
        let rainy = planner.rainyDays[trip.id] ?? []
        let byId = Dictionary(uniqueKeysWithValues: activities.map { ($0.id, $0) })
        let todays = allItems.filter { $0.tripId == trip.id && $0.dayNo == day }.sorted { $0.startHour < $1.startHour }

        VStack(alignment: .leading, spacing: 10) {
            ChipFlow(items: Array(1...trip.days)) { d in
                Chip(label: rainy.contains(d) ? "Day \(d) (rain)" : "Day \(d)", selected: day == d) { selectedDay = d }
            }
            Button { toggleRain(day, trip: trip) } label: {
                Text(rainy.contains(day) ? "Clear rain on Day \(day)" : "Simulate rain on Day \(day)").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered).tint(Theme.navy)

            if rainy.contains(day) {
                InfoCard(title: "Re-planned", message: "Outdoor activities were moved off this day; only indoor options remain.",
                         background: Color.orange.opacity(0.15))
            }
            if todays.isEmpty { InfoCard(title: "Nothing scheduled", message: "No suitable activities for this day.") }
            ForEach(todays.indices, id: \.self) { i in
                if let a = byId[todays[i].activityId] {
                    InfoCard(title: "\(String(format: "%02d", todays[i].startHour)):00  \(a.name)",
                             message: "\(a.category.capitalized) | \(a.isIndoor ? "Indoor" : "Outdoor") | \(bdt(a.estimatedCost)) per person")
                }
            }
        }
        .padding(16)
    }

    /// Adaptive re-planning: rain on a day regenerates the plan with indoor-only activities for it.
    private func toggleRain(_ day: Int, trip: Trip) {
        var set = planner.rainyDays[trip.id] ?? []
        if set.contains(day) { set.remove(day) } else { set.insert(day) }
        planner.rainyDays[trip.id] = set

        allItems.filter { $0.tripId == trip.id }.forEach { context.delete($0) }
        let acts = activities.filter { $0.destinationId == trip.destinationId }
        ItineraryGenerator.generate(tripId: trip.id, days: trip.days, activities: acts,
                                    interests: trip.interestSet, rainyDays: set, tier: trip.tierValue)
            .forEach { context.insert($0) }
        try? context.save()
    }
}
