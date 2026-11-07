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
The 86 records were extracted from the user-provided Travel Mate KML. The KML did not declare
a reuse licence. Obtain written permission from Travel Mate before publishing or distributing
the dataset in a public release. The current repository branch is public; treat the bundled
asset as a provisional integration pending that permission. Attribution shown in the UI:
`Source: Travel Mate KML`.

## Cartographic limitations
The tourist markers use their point coordinates. The background country outline is still
the existing stylised illustration, not an official boundary or navigation map. This change
does not yet render official district/upazila polygons.
