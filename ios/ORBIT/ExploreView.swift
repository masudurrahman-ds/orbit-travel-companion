import SwiftUI
import SwiftData

struct ExploreView: View {
    @Environment(\.modelContext) private var context
    @Environment(PlannerState.self) private var planner
    @Query private var destinations: [Destination]
    @Query private var activities: [ActivityOption]
    @State private var search = ""
    @Query private var visits: [Visit]
    var embedded = false
    let onPlanned: () -> Void

    var body: some View {
        let all = ScoringEngine.rank(destinations, planner.query, favorites: planner.favorites)
        let term = search.trimmingCharacters(in: .whitespaces)
        let ranked = term.isEmpty ? all : all.filter {
            $0.destination.name.localizedCaseInsensitiveContains(term)
                || $0.destination.district.localizedCaseInsensitiveContains(term)
                || $0.destination.division.localizedCaseInsensitiveContains(term)
        }
        NavWrap(embedded: embedded) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("\(planner.query.tier.label) tour | \(planner.query.days) days, \(bdt(planner.query.budget)), \(monthName(planner.query.month))")
                        .font(.footnote).foregroundStyle(.secondary)
                    if ranked.isEmpty {
                        InfoCard(title: "No matches", message: "Try another spot, district or division name.")
                    }
                    ForEach(ranked) { r in
                        DestinationCard(ranked: r,
                                        visited: visits.contains { $0.destinationId == r.destination.id },
                                        favorite: planner.favorites.contains(r.destination.id),
                                        onFavorite: { toggleFavorite(r.destination.id) },
                                        onPlan: { plan(r.destination) })
                    }
                }
                .padding(16)
            }
            .navigationTitle("Explore")
            .searchable(text: $search, prompt: "Search spot, district or division")
        }
    }

    private func toggleFavorite(_ id: Int) {
        if planner.favorites.contains(id) { planner.favorites.remove(id) } else { planner.favorites.insert(id) }
    }

    private func plan(_ d: Destination) {
        let q = planner.query
        let trip = Trip(title: d.name, destinationId: d.id, startMonth: q.month, days: q.days,
                        budget: q.budget, travellers: q.travellers, interests: q.interests.sorted().joined(separator: ","), tier: q.tier.rawValue)
        context.insert(trip)
        let acts = activities.filter { $0.destinationId == d.id }
        ItineraryGenerator.generate(tripId: trip.id, days: q.days, activities: acts, interests: q.interests, tier: q.tier)
            .forEach { context.insert($0) }
        try? context.save()
        planner.currentTripId = trip.id
        onPlanned()
    }
}

private struct DestinationCard: View {
    let ranked: RankedDestination
    let visited: Bool
    let favorite: Bool
    let onFavorite: () -> Void
    let onPlan: () -> Void

    var body: some View {
        let d = ranked.destination, s = ranked.score
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("\(s.percent)").font(.headline).foregroundStyle(.white)
                    .frame(width: 46, height: 46).background(Theme.navy, in: Circle())
                VStack(alignment: .leading) {
                    Text(d.name).font(.headline)
                    Text("\(d.district), \(d.division) | " + d.tagSet.sorted().map { $0.capitalized }.joined(separator: ", "))
                        .font(.caption).foregroundStyle(Theme.teal)
                }
                Spacer()
                Button(action: onFavorite) {
                    Image(systemName: favorite ? "heart.fill" : "heart").foregroundStyle(Theme.rose)
                }
            }
            Text(d.blurb).font(.subheadline)
            Text("Estimated \(bdt(s.estimatedCost)) | \(s.affordable ? "within budget" : "over budget")" + (visited ? " | Visited" : ""))
                .font(.footnote.weight(.semibold)).foregroundStyle(s.affordable ? Theme.green : Theme.rose)
            SectionLabel("WHY THIS SCORE")
            FactorBar(label: "Budget fit", value: s.budget, color: Theme.orange)
            FactorBar(label: "Interest match", value: s.interest, color: Theme.purple)
            FactorBar(label: "Time fit", value: s.time, color: Theme.teal)
            FactorBar(label: "Weather", value: s.weather, color: Theme.cyan)
            FactorBar(label: "Route", value: s.route, color: Theme.green)
            Button(action: onPlan) { Text("Plan this trip").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).tint(Theme.navy)
        }
        .padding(14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
    }
}

private struct FactorBar: View {
    let label: String
    let value: Double
    let color: Color
    var body: some View {
        HStack {
            Text(label).font(.caption).frame(width: 100, alignment: .leading)
            ProgressView(value: min(max(value, 0), 1)).tint(color)
            Text("\(Int((value * 100).rounded()))%").font(.caption).frame(width: 44, alignment: .trailing)
        }
    }
}
