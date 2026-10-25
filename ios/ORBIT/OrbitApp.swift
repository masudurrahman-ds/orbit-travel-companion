import SwiftUI
import SwiftData

@main
struct OrbitApp: App {
    @State private var planner = PlannerState()
    @State private var session = AppSession()
    @State private var guide = GuideState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(planner)
                .environment(session)
                .environment(guide)
        }
        .modelContainer(for: [Destination.self, ActivityOption.self, Trip.self, ItineraryItem.self, Expense.self, Visit.self, MemoryNote.self])
    }
}
