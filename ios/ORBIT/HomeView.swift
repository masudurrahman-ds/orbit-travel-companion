import SwiftUI
import SwiftData

struct HomeView: View {
    @Environment(PlannerState.self) private var planner
    @Query(sort: \Trip.createdAt, order: .reverse) private var trips: [Trip]
    @Query private var destinations: [Destination]
    @Query private var activities: [ActivityOption]
    @Query private var allItems: [ItineraryItem]
    @Query private var allExpenses: [Expense]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Plan smarter. Travel better.").foregroundStyle(Theme.orange).fontWeight(.semibold)
                    if let trip = currentTrip(trips, planner) {
                        let dest = destinations.first { $0.id == trip.destinationId }
                        let spent = allExpenses.filter { $0.tripId == trip.id }.reduce(0) { $0 + $1.amount }
                        InfoCard(title: trip.title,
                                 message: "\(trip.days) days | \(trip.travellers) traveller(s) | \(monthName(trip.startMonth)) | budget \(bdt(trip.budget))",
                                 background: Theme.teal, foreground: .white)
                        HStack(spacing: 12) {
                            InfoCard(title: "Weather", message: weatherLabel(dest, month: trip.startMonth))
                            InfoCard(title: "Spent", message: bdt(spent))
                        }
                        SectionLabel("NEXT UP")
                        let names = Dictionary(uniqueKeysWithValues: activities.map { ($0.id, $0) })
                        let next = allItems.filter { $0.tripId == trip.id }
                            .sorted { ($0.dayNo, $0.startHour) < ($1.dayNo, $1.startHour) }.prefix(3)
                        ForEach(Array(next.enumerated()), id: \.offset) { _, item in
                            if let a = names[item.activityId] {
                                InfoCard(title: a.name,
                                         message: "Day \(item.dayNo), \(String(format: "%02d", item.startHour)):00 | \(a.category.capitalized)")
                            }
                        }
                    } else {
                        InfoCard(title: "No trip yet",
                                 message: "Open the Plan tab, set your budget, days and interests, then pick a ranked destination.")
                    }
                }
                .padding(16)
            }
            .navigationTitle("ORBIT")
        }
    }
}
