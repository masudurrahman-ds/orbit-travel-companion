# ORBIT Tourist Location Catalogue Integration

## Implementation
The Android passport map now loads and renders the 86 named tourist placemarks offline.
The map supports tapping a placemark, viewing its name/source group/upazila/coordinates,
and searching tourist place names alongside district chips.

Files:
- `android/app/src/main/assets/tourist-locations.psv` — 86 offline point records.
- `android/app/src/main/java/com/crowncouncil/orbit/data/TouristLocationCatalog.kt` — safe asset parser.
- `android/app/src/main/java/com/crowncouncil/orbit/ui/VisitedMapScreen.kt` — marker rendering, hit testing and search selection.

The catalogue remains read-only and is not inserted into Room's `destination` table.
Existing visit history, recommendation scores, itinerary records and cost data are not changed.

## Data contract
Pipe-separated UTF-8 records with nine fields:
`id|name|latitude|longitude|source_region|district|division|upazila|administrative_match_status`

Coordinates are WGS84 latitude/longitude in the asset. Invalid rows and coordinates outside
Bangladesh's broad bounding box are skipped. Unmatched records remain visible using their
original coordinates; the two known unmatched points are Cox's Bazar Sea Beach and Patenga
Sea Beach. The UI deliberately shows the source folder and upazila rather than presenting
the supplied district/division labels as authoritative.

## Source and licensing
The 86 tourist points were extracted from the user-provided Travel Mate KML and incorporated at the user's direction. The app credits the source in the place detail panel.

Administrative boundaries use geoBoundaries gbOpen Bangladesh ADM3 (upazila) simplified GeoJSON, pinned to source commit `9469f09`, source vintage 2020, licence CC BY 4.0. The build downloads this exact source into Android assets and verifies its SHA-256 before packaging:
- Source: https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/BGD/ADM3/geoBoundaries-BGD-ADM3_simplified.geojson
- Attribution: `Boundaries © geoBoundaries (geoboundaries.org), CC BY 4.0. Underlying: Bangladesh Bureau of Statistics (BBS) / OCHA ROAP.`
- Asset name at build time: `android/app/src/main/assets/bangladesh-upazilas.geojson` (generated; not committed as a large source blob).

The build machine needs network access the first time it downloads the boundary asset. The installed app reads the bundled asset locally; map rendering and spatial lookups then work offline. A subsequent local build can reuse the downloaded asset.

## Geographic coverage and matching
The source contains 544 polygon features: 492 Polygon and 52 MultiPolygon. 426 features have matched district and division identifiers; 118 features do not. All features are drawn for cartographic coverage, but point-in-polygon administrative matching only uses features with matched district and division identifiers. ORBIT does not guess missing administrative relationships.

The boundary geometry is ADM3/upazila, not district-level polygons. A tourist point's district, division and upazila are derived from its containing matched polygon when available; legacy point metadata is a fallback only. This spatial match takes precedence over the original point labels.

## Cartographic limitations
The passport map now draws the geoBoundaries ADM3 upazila polygons and supports tapping within a polygon to open its district guide. Tourist placemarks remain point markers from the imported KML. The map is a static administrative reference layer, not a road-navigation map; it does not calculate routable driving paths or live travel times. Boundary vintage is 2020 and should not be represented as a current legal cadastral boundary.
