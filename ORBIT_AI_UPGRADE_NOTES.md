# ORBIT AI Upgrade — Voice + Travel Image Generation

## Implemented

- Expanded ORBIT Brain's shared offline vocabulary for everyday English paraphrases and common Banglish spellings (including travel intent, interests, trip-cost wording, spoken numbers, and destination aliases). The same `brain-data.json` is synchronized across Android, iOS, and backend.
- Fixed intent precedence so a request such as “help me plan a quick getaway” is treated as a planning request instead of returning only the help menu. Applied the same fix in the backend, Android, and iOS Brain implementations.
- Added regression tests for natural English and Banglish trip requests, destination aliases, and follow-up suggestions; the tests keep results grounded in the app's local destination data.

- Android Guide now has microphone input with explicit English (`en-US`) and Bengali (`bn-BD`) speech-recognition choices. Banglish uses the English recognition locale because it is Romanized Bangla rather than a separate Android locale.
- Microphone permission is requested only after the user taps the microphone. The recognized text is inserted into the composer for review before the user sends it to ORBIT Brain.
- Added a **Create a travel photo** panel in the Android Guide. It accepts a text prompt, shows generation progress/errors, and displays returned image data.
- Added authenticated `POST /api/agent/image` backend route. It checks AI consent, applies rate limiting, keeps provider credentials server-side, uses a 45-second upstream timeout, and avoids exposing raw provider diagnostics to the app.
- Documented server configuration and cost/privacy considerations in `backend/README.md`.

## Configure photo generation

Set `OPENAI_API_KEY` on the backend host (never in Android `gradle.properties`, source control, or the APK). Optional: set `ORBIT_IMAGE_MODEL`; the default is `gpt-image-1`. The user must be signed in and have AI-guide consent enabled. Provider use may incur charges; set provider budgets before public launch.

## Voice recognition limitations

Speech recognition is supplied by Android's installed recognition service. Bengali and English accuracy depends on the device and installed language packs. Offline recognition is not guaranteed. Banglish speech is recognized using `en-US`; users can review/edit the transcription before sending. This patch does not claim fully offline speech-to-text.

## Verification

- Context-aware on-device language assistance: when the deterministic Brain cannot interpret a message, the optional local model now receives a compact summary of the last trip request and destination. This helps translate follow-ups such as “make it cheaper” without letting the model supply factual prices or destinations; final results still come from ORBIT Brain and local data. Added equivalent context handling on Android and iOS.
- Model import now prefers `.litertlm` files, releases any previously loaded engine before replacement, and uses a temporary file plus a backup to avoid destroying an existing model if replacement fails.
- Backend test suite: **79 passed, 0 failed**, including image consent, prompt validation, server-side credential handling, and sanitized provider-error tests.
- Fixed the language-chip state so English and Banglish no longer appear selected together. Voice errors now give actionable feedback and the recognizer is destroyed on success/failure. Banglish remains an experimental route through the English speech model, not a guaranteed dedicated locale.
- Android compilation/device verification was not run: this checkout has no Gradle wrapper and the Android SDK/Gradle executable is unavailable here. Validate the Kotlin UI on a configured Android build machine before release.


## Next validation gate

- Android/iOS compilation and physical-device tests are still required. In particular, verify the selected `.litertlm` model is compatible with LiteRT-LM and the target phone memory budget, then test contextual Bangla/Banglish follow-ups offline. iOS Foundation Models remains limited to supported Apple Intelligence devices and OS versions.
