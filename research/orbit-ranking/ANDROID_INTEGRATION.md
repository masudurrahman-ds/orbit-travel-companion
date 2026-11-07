# Android integration

1. Copy OrbitRanker.kt into android/app/src/main/java/com/crowncouncil/orbit/ai/.
2. Run the training script and place exported JSON at android/app/src/main/assets/orbit_ranker.json.
3. Load the asset off the UI thread using assets.open("orbit_ranker.json").bufferedReader().use { it.readText() }.
4. Provide exactly budget_fit, interest_match, season_fit, travel_time_fit and data_confidence. Normalisation must match the exported JSON.
5. Keep the existing planner as fallback if the asset is missing or invalid.
6. Add unit tests for feature order, scaling, deterministic scores and fallback.
7. Run gradle :app:testDebugUnitTest :app:assembleDebug from android/.

The branch already contains a GitHub Actions Android APK workflow. A downloadable APK exists only after that workflow completes successfully and uploads ORBIT-debug-apk. Emulator backend host is 10.0.2.2.
