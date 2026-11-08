# ORBIT - Intelligent Travel Planning

Team: The Crown Council | Course: Mobile Application and Development (0613-109), DIU CSE, Batch D-86

| Folder | What it is | Stack |
|---|---|---|
| `android/` | Android app (primary) | Kotlin, Jetpack Compose, Material 3, Room, Google Maps, MVVM |
| `ios/` | iOS app (same features) | Swift, SwiftUI, SwiftData, MapKit, Core Image (iOS 17+) |
| `backend/` | API server and the tested reference for the guide | Node.js, no dependencies, 79 automated tests |

## On-device generative model (optional)

The Android app uses Google's LiteRT-LM runtime for an optional on-device language model. The model is not bundled in the APK: from **More → Local language model**, import a compatible `.litertlm` model file and enable it. Use a trusted publisher, check the model's license, and ensure the phone has enough free storage and RAM; even small models can be hundreds of MB and generation speed varies by device. Importing a model requires a one-time file selection, but inference is local afterward.

The model can help interpret open-ended requests and provide direct answers when the rule-based travel planner does not understand. Destination recommendations, itinerary calculations, and budget estimates should still be grounded in ORBIT's bundled data. Local model outputs can be wrong; verify important travel details. Conversation memory and imported model weights are stored in app-private local storage. Removing the model or clearing conversation memory is available in-app.

Runtime dependency is pinned to LiteRT-LM 0.16.0 to avoid an unbounded `latest.release` dependency; CI must confirm compatibility with this repository's Kotlin/KSP versions before this feature is considered release-ready.

## Features
- **Plan and explore:** 113 spots across all 64 districts, ranked by a weighted model with a "why this score" breakdown. Three tour styles: **Budget** (lowest sensible cost), **Value** (affordable but good quality), **Fancy** (comfort first).
- **ORBIT Brain, the app's own guide:** a native agent that runs on the phone with no outside AI service, no account and no network. It understands budget, days, travellers, month, interests and tour style from plain sentences (for example "affordable but good quality trip for 4 in December"), picks Budget / Value / Fancy itself, plans, builds itineraries, compares places, estimates costs, gives best months, measures distance from your live location, tracks progress, handles follow-ups ("what about fancy", "another one", "make it 5 days"), and remembers preferences you tell it. It never asks questions; it states what it assumed. Memory notes live only on the device and can be deleted in Account.
- **Optional on-device language model (Bangla and looser wording):** off by default. Android optional LiteRT-LM model translation is temporarily disabled in the build-safe profile because the floating dependency caused a Kotlin/KSP metadata conflict; iOS uses Apple's built-in Foundation Models on iOS 26 and Apple Intelligence devices, with nothing to download. The model only *translates* a message ORBIT Brain did not understand into one short English request, using the last trip request/destination to interpret short follow-ups when available. ORBIT Brain then answers from the app's own data, and the model's own words are never shown, so it cannot invent prices or places.
- **My Bangladesh map:** visited districts pinned in gold, unvisited ones left as faint dots. Tap a district to mark or unmark spots, rate them and add a memory note.
- **Live map:** Google Maps (Android) / MapKit (iOS), your trail, distance to the destination district, "mark visited" prompt, and a button to ask the guide. Tracking runs only while the screen is open.
- **Photo editor:** brightness, contrast, colour, warmth, filters, rotate, square crop, save to gallery. Fully on-device.
- **Rankings:** leaderboard of travellers by distinct spots visited (opt-in).
- **Community spots:** users suggest new places; they appear after review.
- **Accounts:** email or mobile signup with a verification code, login, required Terms and Privacy agreement, optional AI / location / leaderboard agreements, data export, account deletion.
- **Privacy and Terms pages:** served by the backend at `/privacy` and `/terms`.
- **Itinerary, rain re-planning, budget tracking** from the first version.
- **Responsive UI:** text uses sp units and follows the phone's font size, side navigation rail on tablets, light and dark themes, edge-to-edge layout.

## Honest limits
- **Nothing here has been compiled or run on a device.** The backend's 79 tests pass (including cross-platform data-parity checks); the Kotlin and Swift code, including their ports of the Brain, was written without Android Studio or Xcode available, so expect a few small compile fixes on first build. The Android project includes `BrainTest.kt`, which mirrors the JavaScript tests so you can check the port.
- **ORBIT Brain is rule-based, not a trained language model.** It handles the request types it was built for and replies with examples when it does not understand. It cannot hold open-ended conversation. Training a real language model needs GPUs, data and weeks, and could not be done here.
- Spot coordinates are district-level approximations, so live-map distance and "you are in this district" are rough. Replace with exact coordinates before promising precision.
- The country outline is a stylised drawing, not a survey-grade border.
- Costs, best months and distances are estimates. Community ratings only influence the backend planner and the guide; the on-device ranking uses a neutral quality.
- The iOS app has no in-app camera (it picks from the photo library). Android can also take a photo with the system camera.
- **The language model layer has never been run.** No model, device or network was available here, so the LiteRT-LM and Foundation Models code follows their official documentation but is untested. Bangla quality depends on the model you choose and on Apple's supported languages, so test it with real Bangla messages before relying on it.
- **Android model file:** you must get a `.litertlm` model yourself from its official page and accept its license. The app never downloads or bundles one. Models are large (hundreds of MB to several GB) and need a recent phone with enough memory. Do not restore a floating `latest.release` dependency. Re-enable only after pinning a specific LiteRT-LM release verified against the Kotlin/KSP toolchain.
- An optional cloud guide (hosted model) exists on the backend but is off by default, and the apps never need it.
- Verification codes are only printed or returned in development mode until you connect an email or SMS provider.

## Run order
1. **Backend:** see `backend/README.md`. Start it on your computer.
2. **Android:** open the `android` folder in Android Studio (Ladybug or newer, JDK 17), let Gradle sync.
   - Add your Google Maps key to `~/.gradle/gradle.properties`: `MAPS_API_KEY=...` (enable "Maps SDK for Android" in Google Cloud). Never commit it.
   - Emulator reaches your computer at `http://10.0.2.2:8080` (default). For a real phone set `ORBIT_API_URL=http://<your-computer-ip>:8080` in the same file.
   - Run the app. Create an account; the dev server shows your verification code on screen.
   - Unit tests: right-click `app/src/test` > Run Tests (scoring, tiers and the Brain).
3. **iOS** (Mac with Xcode 15+): create a new SwiftUI App named `ORBIT` (minimum iOS 17, Storage None), delete the generated `ORBITApp.swift` and `ContentView.swift`, drag in every file from `ios/ORBIT/`, make sure `brain-data.json` is ticked under Build Phases > Copy Bundle Resources, and add the keys listed in `ios/ORBIT/Info-plist-keys.txt`.

## Keeping the apps in sync
Scoring, tiers, itinerary generation, seed data and the Brain exist in Kotlin, Swift and JavaScript with identical logic. When you change the Brain, change `backend/src/brain.js` first, run its tests, then port.
`gen_seed.py` (kept outside the zip) generated the seed files from one table.


## Release and repository handoff

Before publishing or handing this repository to another developer:

1. Run `cd backend && npm test` (Node.js 18+; no third-party npm packages are required).
2. Confirm the Android app syncs and builds in Android Studio with JDK 17 and SDK 35. This workspace does not include a Gradle wrapper, and this environment did not have Gradle/Android SDK installed, so a successful Android build has **not** been claimed.
3. Set a stable random `ORBIT_SECRET`, configure an actual email/SMS OTP delivery provider, and put the backend behind HTTPS before accepting real users. Development OTP output is for local testing only.
4. Use a persistent database (for example PostgreSQL) for a public deployment. The current JSON-file store is suitable for a course prototype, not concurrent production traffic or multi-instance hosting.
5. Add a real Maps key through local Gradle properties; never commit keys, tokens, database files or `.env` files.
6. Review current travel restrictions and re-check fares before release. Research values are dated snapshots, not live quotes. In particular, confirm the 2026–27 Saint Martin rules and verify the flagged Dhaka–Rajshahi distance.

The source tree is prepared for a Git repository. After you create the repository and provide its URL plus the preferred branch, the changes can be committed and pushed there.
