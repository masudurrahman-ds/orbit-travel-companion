import SwiftUI
import SwiftData

/// Bangladesh with visited districts pinned in gold and districts still to visit left as faint dots.
struct VisitedMapView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSession.self) private var session
    @Query private var destinations: [Destination]
    @Query private var visits: [Visit]
    @State private var selected: String?
    @State private var rating: Destination?

    private var byDistrict: [String: [Destination]] { Dictionary(grouping: destinations, by: \.district) }
    private var visitedIds: Set<Int> { Set(visits.map(\.destinationId)) }
    private var doneDistricts: Set<String> { Set(byDistrict.filter { $0.value.contains { visitedIds.contains($0.id) } }.keys) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("\(doneDistricts.count) of \(byDistrict.count) districts | \(destinations.filter { visitedIds.contains($0.id) }.count) of \(destinations.count) spots")
                        .fontWeight(.semibold)
                    HStack(spacing: 16) {
                        Text("Gold pin = visited").foregroundStyle(Theme.gold).font(.footnote.weight(.semibold))
                        Text("Faint dot = still to visit").foregroundStyle(.secondary).font(.footnote)
                    }
                    mapCanvas
                    Text("Outline is stylised. Tap a marker to see that district's spots.").font(.caption).foregroundStyle(.secondary)
                    if let d = selected { districtList(d) }
                }
                .padding(16)
            }
            .navigationTitle("My Bangladesh")
            .sheet(item: $rating) { spot in RateVisitSheet(name: spot.name) { stars, note in mark(spot, stars, note) } }
        }
    }

    private var centres: [String: CGPoint] {
        byDistrict.compactMapValues { list in list.first.map { BangladeshMap.project(lon: $0.longitude, lat: $0.latitude) } }
    }

    private var mapCanvas: some View {
        GeometryReader { geo in
            Canvas { ctx, size in
                var path = Path()
                for (i, p) in BangladeshMap.outline.enumerated() {
                    let pt = BangladeshMap.project(lon: p.0, lat: p.1)
                    let point = CGPoint(x: pt.x * size.width, y: pt.y * size.height)
                    if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
                path.closeSubpath()
                ctx.fill(path, with: .color(Color(.systemGray5)))
                ctx.stroke(path, with: .color(Theme.navy), lineWidth: 2)
                for (district, p) in centres {
                    let c = CGPoint(x: p.x * size.width, y: p.y * size.height)
                    if doneDistricts.contains(district) {
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 9, y: c.y - 9, width: 18, height: 18)), with: .color(.white))
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 7, y: c.y - 7, width: 14, height: 14)), with: .color(Theme.gold))
                    } else {
                        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - 3, y: c.y - 3, width: 6, height: 6)), with: .color(.gray.opacity(0.6)), lineWidth: 1)
                    }
                    if district == selected {
                        ctx.stroke(Path(ellipseIn: CGRect(x: c.x - 13, y: c.y - 13, width: 26, height: 26)), with: .color(Theme.teal), lineWidth: 2)
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(SpatialTapGesture().onEnded { tap in
                let size = geo.size
                let nearest = centres.min { a, b in dist(a.value, tap.location, size) < dist(b.value, tap.location, size) }
                if let n = nearest, dist(n.value, tap.location, size) <= 36 { selected = n.key }
            })
        }
        .aspectRatio(BangladeshMap.aspect, contentMode: .fit)
    }

    private func dist(_ p: CGPoint, _ tap: CGPoint, _ size: CGSize) -> CGFloat {
        hypot(p.x * size.width - tap.x, p.y * size.height - tap.y)
    }

    @ViewBuilder
    private func districtList(_ district: String) -> some View {
        Text(district).font(.title3.bold())
        ForEach((byDistrict[district] ?? []).sorted { $0.id < $1.id }) { spot in
            let done = visitedIds.contains(spot.id)
            HStack {
                VStack(alignment: .leading) {
                    Text(spot.name).fontWeight(.semibold)
                    Text(spot.tagSet.sorted().map(\.capitalized).joined(separator: ", ")).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if done { Button("Unmark") { unmark(spot) }.buttonStyle(.bordered) }
                else { Button("Mark visited") { rating = spot }.buttonStyle(.borderedProminent).tint(Theme.navy) }
            }
        }
    }

    private func mark(_ spot: Destination, _ stars: Int?, _ note: String) {
        context.insert(Visit(destinationId: spot.id, rating: stars, note: note))
        try? context.save()
        guard session.signedIn else { return }
        var body: [String: Any] = ["destinationId": spot.id, "note": String(note.prefix(300))]
        if let stars { body["rating"] = stars }
        Task { _ = await session.request("POST", "/api/visits", body: body) }
    }

    private func unmark(_ spot: Destination) {
        visits.filter { $0.destinationId == spot.id }.forEach { context.delete($0) }
        try? context.save()
        if session.signedIn { Task { _ = await session.request("DELETE", "/api/visits/\(spot.id)") } }
    }
}

struct RateVisitSheet: View {
    @Environment(\.dismiss) private var dismiss
    let name: String
    let onSave: (Int?, String) -> Void
    @State private var stars: Int?
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Rate it (optional). Ratings feed place quality scores.") {
                    Picker("Stars", selection: $stars) {
                        Text("No rating").tag(Int?.none)
                        ForEach(1...5, id: \.self) { Text("\($0)").tag(Int?.some($0)) }
                    }.pickerStyle(.segmented)
                }
                Section("Memory note (optional)") { TextField("What do you want to remember?", text: $note, axis: .vertical).lineLimit(1...4) }
            }
            .navigationTitle("Visited \(name)")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { onSave(stars, note); dismiss() } }
            }
        }
        .presentationDetents([.medium])
    }
}
