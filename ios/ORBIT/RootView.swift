import SwiftUI
import SwiftData

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSession.self) private var session
    @State private var tab = 0
    @State private var showExplore = false

    var body: some View {
        Group {
            if session.needsAuth {
                AuthGate()
            } else {
                TabView(selection: $tab) {
                    HomeView().tabItem { Label("Home", systemImage: "house") }.tag(0)
                    PlanView(onFind: { showExplore = true }).tabItem { Label("Plan", systemImage: "slider.horizontal.3") }.tag(1)
                    GuideView(onPlanSaved: { tab = 0 }).tabItem { Label("Guide", systemImage: "sparkles") }.tag(2)
                    VisitedMapView().tabItem { Label("Map", systemImage: "map") }.tag(3)
                    MoreView(onOpenGuide: { tab = 2 }).tabItem { Label("More", systemImage: "ellipsis.circle") }.tag(4)
                }
                .tint(Theme.navy)
                // Plan > Find destinations opens the ranked list; planning a trip returns to Home, which shows it.
                .sheet(isPresented: $showExplore) {
                    VStack(spacing: 0) {
                        HStack { Spacer(); Button("Close") { showExplore = false }.padding() }
                        ExploreView(onPlanned: { showExplore = false; tab = 0 })
                    }
                }
            }
        }
        .task { seedIfNeeded() }
        .task(id: session.signedIn) { await syncVisits() }
    }

    private func seedIfNeeded() {
        let count = (try? context.fetchCount(FetchDescriptor<Destination>())) ?? 0
        guard count == 0 else { return }
        Seed.destinations.forEach { context.insert($0) }
        Seed.activities.forEach { context.insert($0) }
        try? context.save()
    }

    /// After login: pull server visits we lack, push local visits the server lacks.
    private func syncVisits() async {
        guard session.signedIn else { return }
        let r = await session.request("GET", "/api/visits")
        guard r.ok else { return }
        let remote = r.json?["visits"] as? [[String: Any]] ?? []
        let remoteIds = Set(remote.compactMap { $0["destinationId"] as? Int })
        let local = (try? context.fetch(FetchDescriptor<Visit>())) ?? []
        let localIds = Set(local.map(\.destinationId))
        for v in remote {
            guard let id = v["destinationId"] as? Int, !localIds.contains(id) else { continue }
            context.insert(Visit(destinationId: id, rating: v["rating"] as? Int, note: v["note"] as? String ?? "", source: v["source"] as? String ?? "self"))
        }
        try? context.save()
        for v in local where !remoteIds.contains(v.destinationId) {
            var body: [String: Any] = ["destinationId": v.destinationId, "note": v.note, "source": v.source]
            if let rating = v.rating { body["rating"] = rating }
            _ = await session.request("POST", "/api/visits", body: body)
        }
    }
}

/// Resolves the trip shown on Home, Itinerary and Budget: the selected one, else the newest.
func currentTrip(_ trips: [Trip], _ planner: PlannerState) -> Trip? {
    trips.first { $0.id == planner.currentTripId } ?? trips.first
}
