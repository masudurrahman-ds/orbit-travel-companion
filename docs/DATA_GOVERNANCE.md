# Data Provenance and Governance

**Status:** Working policy for the ORBIT prototype.

## 1. Principle

Every factual destination record should have a traceable origin and an explicit verification state. AI-generated text is not itself a source of truth.

## 2. Recommended provenance fields

| Field | Description |
|---|---|
| `record_id` | Stable internal identifier |
| `name` | Destination or point-of-interest name |
| `admin_area` | District/division only when verified |
| `latitude`, `longitude` | Coordinates with source and precision |
| `source_name` | Organization, dataset or publisher |
| `source_url` | Canonical source record or documentation |
| `retrieved_at` | Date the source was accessed |
| `license` | License and attribution obligations |
| `verified_at` | Last human or authoritative verification |
| `verification_status` | `unverified`, `reviewed`, `verified`, or `stale` |
| `content_type` | `official`, `third_party`, `user_submitted`, `cached`, or `generated` |
| `notes` | Known uncertainty or caveats |

## 3. Source hierarchy

Prefer, in order where appropriate:

1. Official government, local authority or destination operator information.
2. Primary data publishers with documented methodology and licensing.
3. Reputable secondary sources with a clear publication date.
4. User submissions marked as unverified until reviewed.

Do not infer that a source is official merely because its URL looks authoritative. Verify publisher identity and terms.

## 4. Map and geospatial data

- Use a dataset with explicit reuse rights and attribution terms.
- Record the source version and retrieval date for boundary GeoJSON.
- Validate coordinate reference systems and geometry before rendering.
- Do not describe a stylized illustration as an accurate district map.
- Treat route lines and marker positions as approximate unless validated.
- Keep API keys out of source code and version control.

## 5. AI-generated content

Label generated itinerary suggestions and images as generated. Where factual claims are made, link them to independently verified source records. If evidence is missing, express uncertainty or decline to state a specific fact. Never fabricate references, opening hours, prices, safety guarantees or transport availability.

## 6. Freshness

Use different refresh intervals based on volatility. Opening hours, prices, service availability and advisories may change quickly; stable geographic descriptions change less frequently. A stale record must be labeled or withheld where staleness could cause harm.

## 7. Privacy and retention

Collect only the data needed for a clearly stated feature. Define retention and deletion behavior for accounts, logs, location and media. Do not log passwords, verification codes, access tokens or unnecessary precise location data. Document any third-party processor and its data handling.

## 8. Licensing and attribution

Before adding a dataset, confirm its license permits the intended distribution and use. Include required attribution and notices in the app and repository. Public availability does not automatically imply unrestricted reuse.

## 9. Review checklist

- [ ] Source is traceable and appropriate for the claim.
- [ ] License and attribution are recorded.
- [ ] Retrieval and verification dates are recorded.
- [ ] Coordinates and administrative boundaries have been validated.
- [ ] Generated content is labeled.
- [ ] Personal data is minimized.
- [ ] Staleness and uncertainty are handled explicitly.
