# ORBIT Tourist Location Catalogue Integration

## Status
The Android-side offline GeoJSON loader is available in
`android/app/src/main/java/com/crowncouncil/orbit/data/TouristLocationCatalog.kt`.

## Required asset
Copy `tourist-locations.geojson` from the separately prepared
**ORBIT Tourist Locations Import** package into:

`android/app/src/main/assets/tourist-locations.geojson`

The loader intentionally returns an empty list if the asset is absent or malformed,
so existing app startup is not blocked. The map UI must not claim the markers are
loaded until the asset has been copied and validated.

## Data contract
- GeoJSON FeatureCollection with Point geometries.
- Coordinates are WGS84 in GeoJSON order: [longitude, latitude].
- Each feature carries name, source_region, district/division/upazila when matched,
  and administrative_match_status.
- Unmatched locations must remain displayable using their coordinates; do not invent
  administrative labels.
- The catalogue is read-only and is not automatically inserted into Room's
  `destination` table. This avoids changing visit history and recommendation data.

## Source and licensing
The 86 records were extracted from the user-provided Travel Mate KML. That KML did
not declare a reuse license. Confirm permission with Travel Mate before public
distribution. Keep source attribution visible in the app and repository.

## Validation notes
84 of 86 points fell inside the supplied matched 2020 geoBoundaries ADM3/upazila
polygons. Two points (Cox's Bazar Sea Beach and Patenga Sea Beach) did not intersect
the available polygons and intentionally have no inferred district/upazila match.
