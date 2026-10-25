# ORBIT implementation audit and handoff

**Workspace:** `orbit/` from `ORBIT_latest_with_research_WIP.zip`  
**Audit date:** 25 October 2026  
**Primary target:** Android app; backend and iOS reference implementation included.

## Verification performed

- Backend test suite: **74 passed, 0 failed** (`cd backend && npm test`).
- Added a cross-platform parity test requiring the backend, Android and iOS copies of `brain-data.json` and `travel-costs.json` to remain identical.
- Added a data coverage test requiring exactly 113 destinations across all 64 districts.
- Added throttling for OTP verification requests by identifier and client IP, in addition to the existing per-code attempt/expiry limits.
- Added the OTP-throttling regression test.
- Excluded runtime JSON database files and environment secrets from Git.
- Updated the README's stale test count and added release/handoff requirements.

## Feature inventory from source review

| Area | Current implementation | Important qualification |
|---|---|---|
| Discovery and ranking | 113 destinations, 64 districts, weighted scoring and score breakdowns | Destination coordinates are approximate in several records. |
| Tour styles | Budget, Value and Fancy tiers | Daily costs are estimates; Cox's Bazar audit shows material model error. |
| Search and guide | Rule-based offline Brain; follow-up state; Bangla/Banglish intent patterns | Not an open-ended trained LLM. Optional local model is a translation/intent assist, not the source of travel facts. |
| Itinerary and rain | Day-by-day generation; rainy-day indoor activity re-plan | Rain action is a planning simulation unless a live weather provider is integrated. |
| Cost references | Dated sourced fares, confidence levels and labelled estimates | Several routes and current access rules need further primary-source verification. |
| Community | Spot suggestions and moderation; community price reports | Admin moderation token and a real operator process must be configured. |
| Accounts/privacy | Email/phone identifiers, OTP, consent, export, deletion, Terms/Privacy pages | OTP delivery is development-only until a provider is connected. |
| Visits/social | Visited map, badges, passport/story sharing, opt-in leaderboard | GPS check-ins are a higher bar, not cryptographic proof; destination coordinates can be approximate. |
| Groups | Invite codes, shared plans, expense balancing | It calculates settlements only; it does not move money. |
| Maps | Live map and journey tracking while the screen is open | Maps key and platform configuration are required. Not survey-grade navigation. |
| Photo editor | On-device adjustments, filters, crop/rotate and save/share | iOS uses photo-library selection; Android camera use is delegated to the system camera app. |
| Backend | API routes for accounts, spots, visits, prices, advisories, leaderboard and groups | JSON-file storage is prototype-grade, not safe for multi-instance production. |
| Android/iOS build | Source projects and unit tests present | **Not compiled in this environment**: no Gradle executable/wrapper or Android SDK was available; iOS requires Xcode on macOS. |

## Research-specific release cautions

1. The audit's Saint Martin rules apply to the **2025–26 season**. Do not represent them as confirmed for 2026–27 without checking official current guidance.
2. The report gives a post-September bus-rate estimate of about Tk 2.40/km but says the final gazetted rate should be confirmed.
3. Keep the Dhaka–Rajshahi route/distance mismatch flagged; do not silently change either value.
4. Display the date, source, and confidence rating for researched prices. Never style a booking-site snapshot as a guaranteed quote.
5. The report's Cox's Bazar per-day calibration is derived from assumptions and one destination. Do not extrapolate it to all Bangladesh destinations without more receipts and source data.
6. Publish seeded advisories only after an operator reviews them. The research report itself recommends checking current restrictions and official sources.

## Remaining release blockers

- Configure a stable `ORBIT_SECRET`; otherwise tokens become invalid on server restart.
- Connect email/SMS delivery for OTPs; development OTP output is not account verification suitable for public release.
- Deploy behind HTTPS and configure production CORS/origin policy if the client architecture requires it.
- Replace the JSON file store with a transactional persistent database before concurrent public use.
- Build and run Android unit/UI tests in Android Studio; run iOS build/tests on macOS/Xcode.
- Test on physical devices, including location denial, no network, offline guide, large font, tablet layout, portrait/landscape, and low-memory behavior.
- Obtain exact spot coordinates and verify live-map distance/visited prompts before describing them as precise.
- Re-check fares, advisories, emergency contact details and access rules before a public release.

## Suggested next Git handoff

Create an empty repository, share its URL and target branch, then push the contents of this project root. Do not commit a real `.env`, runtime database, Maps API key, admin token or signing key.
