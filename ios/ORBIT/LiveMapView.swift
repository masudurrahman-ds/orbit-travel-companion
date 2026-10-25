import SwiftUI
import SwiftData
import MapKit
import CoreLocation
import Observation

/// Location updates run only while the live map is open and tracking is on.
@Observable
final class LocationTracker: NSObject, CLLocationManagerDelegate {
    var status: CLAuthorizationStatus = .notDetermined
    var trail: [CLLocationCoordinate2D] = []
    var tracking = false
    private let manager = CLLocationManager()

    override init() {
        super.init()
        status = manager.authorizationStatus
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 10
    }

    var authorized: Bool { status == .authorizedWhenInUse || status == .authorizedAlways }
    func requestPermission() { manager.requestWhenInUseAuthorization() }
    func start() { guard authorized else { return }; tracking = true; manager.startUpdatingLocation() }
    func stop() { tracking = false; manager.stopUpdatingLocation() }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { status = manager.authorizationStatus }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let l = locations.last else { return }
        trail = Array((trail + [l.coordinate]).suffix(500))
    }
}

struct LiveMapView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppSession.self) private var session
    @Environment(GuideState.self) private var guide
    @Environment(PlannerState.self) private var planner
    @Query(sort: \Trip.createdAt, order: .reverse) private var trips: [Trip]
    @Query private var destinations: [Destination]
    @Query private var visits: [Visit]
    @State private var tracker = LocationTracker()
    @State private var camera: MapCameraPosition = .region(MKCoordinateRegion(center: .init(latitude: 23.7, longitude: 90.4), span: .init(latitudeDelta: 6, longitudeDelta: 6)))
    var embedded = false
    var onOpenGuide: () -> Void = {}

    private var target: Destination? {
        guard let t = currentTrip(trips, planner) else { return nil }
        return destinations.first { $0.id == t.destinationId }
    }

    var body: some View {
        NavWrap(embedded: embedded) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(target.map { "Heading to \($0.name), \($0.district)." } ?? "No trip selected. Plan a trip to set your destination.")
                    Map(position: $camera) {
                        UserAnnotation()
                        if let t = target { Marker(t.name, coordinate: CLLocationCoordinate2D(latitude: t.latitude, longitude: t.longitude)) }
                        if tracker.trail.count > 1 { MapPolyline(coordinates: tracker.trail).stroke(Theme.teal, lineWidth: 5) }
                    }
                    .mapControls { MapUserLocationButton() }
                    .frame(height: 340).clipShape(RoundedRectangle(cornerRadius: 20))

                    if !tracker.authorized {
                        InfoCard(title: "Location needed", message: "Allow location to see yourself on the map. It is used only while this screen is open and tracking is on, and your path stays on this phone.")
                        Button { tracker.requestPermission() } label: { Text("Allow location").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent).tint(Theme.navy)
                    } else {
                        Button { tracker.tracking ? tracker.stop() : tracker.start() } label: { Text(tracker.tracking ? "Stop tracking" : "Start tracking").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent).tint(Theme.navy)
                    }

                    if let t = target, let here = tracker.trail.last {
                        let km = BangladeshMap.distanceKm(here.latitude, here.longitude, t.latitude, t.longitude)
                        InfoCard(title: String(format: "%.1f km to %@ centre", km, t.district), message: "Spot positions are district-level estimates, so treat this as a rough guide.")
                        if km <= 10, !visits.contains(where: { $0.destinationId == t.id }) {
                            Button { markVisited(t) } label: { Text("You are in \(t.district). Mark \(t.name) visited").frame(maxWidth: .infinity) }
                                .buttonStyle(.borderedProminent).tint(Theme.gold)
                        }
                        Button {
                            Task {
                                await guide.ask("How far is \(t.name) and how long will it take?", session: session, context: context, planner: planner, location: (here.latitude, here.longitude), targetId: t.id)
                            }
                            onOpenGuide()
                        } label: { Text("Ask the guide about this trip").frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                    }
                }
                .padding(16)
            }
            .navigationTitle("Live map")
            .onChange(of: tracker.trail.count) { _, _ in
                if tracker.tracking, let last = tracker.trail.last {
                    camera = .region(MKCoordinateRegion(center: last, span: .init(latitudeDelta: 0.05, longitudeDelta: 0.05)))
                }
            }
            .onAppear { if let t = target { camera = .region(MKCoordinateRegion(center: .init(latitude: t.latitude, longitude: t.longitude), span: .init(latitudeDelta: 1.5, longitudeDelta: 1.5))) } }
            .onDisappear { tracker.stop() }
        }
    }

    private func markVisited(_ t: Destination) {
        context.insert(Visit(destinationId: t.id, source: "gps"))
        try? context.save()
        if session.signedIn { Task { _ = await session.request("POST", "/api/visits", body: ["destinationId": t.id, "source": "gps"]) } }
    }
}
