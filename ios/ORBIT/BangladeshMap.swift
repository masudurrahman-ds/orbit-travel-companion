import Foundation
import CoreGraphics

/// Stylised outline of Bangladesh (longitude, latitude). A simplified drawing, not a survey-grade border.
enum BangladeshMap {
    static let minLon = 87.9, maxLon = 92.8, minLat = 20.5, maxLat = 26.7
    /// Width / height of the drawn map once longitude is scaled by cos(23.5 degrees).
    static let aspect: CGFloat = CGFloat((maxLon - minLon) * 0.917 / (maxLat - minLat))

    static let outline: [(Double, Double)] = [
        (88.08, 26.00), (88.35, 26.50), (88.70, 26.40), (88.95, 26.25), (89.30, 26.08), (89.70, 26.10),
        (89.85, 25.80), (89.85, 25.30), (90.30, 25.20), (90.70, 25.15), (91.10, 25.20), (91.60, 25.15),
        (92.00, 25.10), (92.25, 24.90), (92.10, 24.50), (91.80, 24.20), (91.65, 23.90), (91.50, 23.50),
        (91.65, 23.10), (91.80, 23.00), (92.10, 23.60), (92.35, 23.30), (92.40, 22.80), (92.45, 22.20),
        (92.30, 21.70), (92.35, 21.30), (92.25, 20.90), (92.30, 20.60), (92.05, 21.15), (91.95, 21.50),
        (91.85, 22.00), (91.80, 22.35), (91.40, 22.85), (91.05, 22.75), (90.75, 22.50), (90.70, 22.10),
        (90.35, 21.95), (90.10, 21.80), (89.80, 21.80), (89.50, 21.75), (89.15, 21.85), (89.00, 22.10),
        (88.95, 22.40), (88.80, 22.80), (88.65, 23.20), (88.70, 23.60), (88.40, 23.90), (88.20, 24.30),
        (88.05, 24.65), (88.20, 25.00), (88.45, 25.40), (88.20, 25.80),
    ]

    /// 0...1 in both axes, y grows downward.
    static func project(lon: Double, lat: Double) -> CGPoint {
        CGPoint(x: (lon - minLon) / (maxLon - minLon), y: 1.0 - (lat - minLat) / (maxLat - minLat))
    }

    static func distanceKm(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let r = 6371.0
        let dLat = (lat2 - lat1) * .pi / 180, dLon = (lon2 - lon1) * .pi / 180
        let a = pow(sin(dLat / 2), 2) + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * pow(sin(dLon / 2), 2)
        return 2 * r * asin(sqrt(a))
    }
}
