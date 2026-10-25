# ORBIT deployment and release checklist

## 1. Deploy the backend

1. Create a Render Blueprint from this repository and review the proposed service.
2. The blueprint mounts a persistent disk at `/var/data`; keep `ORBIT_DATA=/var/data/db.json` so user accounts and app data survive service restarts.
3. Set `RESEND_API_KEY` and `ORBIT_EMAIL_FROM` in Render to enable email verification. The sender must be verified with Resend. The backend returns a clear 503 if OTP delivery is not configured instead of falsely claiming the code was sent.
4. Phone signup is not production-ready until an SMS provider is integrated; use email verification for now. `ORBIT_DEV_OTP=1` is only for local development and must never be enabled for real users.
5. Set `OPENAI_API_KEY` in Render only if AI image generation is required. Never put provider secrets in Android code or the APK.
6. Confirm `https://YOUR-SERVICE.onrender.com/health` returns `{"ok":true}`.

## 2. Build an APK that can reach the backend

1. In GitHub repository Settings → Secrets and variables → Actions → Variables, add repository variable `ORBIT_API_URL` with the deployed HTTPS API base URL (for example, `https://your-service.onrender.com`, without a trailing slash).
2. The Android APK workflow runs on `main` and `fix/orbit-ui-and-deployment`; it reads that variable when building. If the variable is missing, the build warns and falls back to `http://10.0.2.2:8080`, which only works from an emulator connected to a development computer.
3. Open Actions → Build Android APK → latest successful run → Artifacts → `ORBIT-debug-apk` to download the APK.

## Feature status and honest limitations

- Offline trip recommendations: local deterministic Brain for supported intents; it is not a general-purpose LLM.
- Signup/sign-in: backend required. Email OTP requires Resend configuration; SMS OTP is not yet integrated.
- Image generation: requires network access, AI consent, a reachable backend and server-side provider key.
- Video generation: no working video-generation pipeline is implemented yet; don't present it as available.
- Voice: uses Android's installed speech service. Bengali/English recognition and offline availability depend on device language packs and the speech provider.
- Bangladesh map: the in-app progress-map outline is stylised and not an authoritative administrative map. Do not claim survey-grade district boundaries; use vetted boundary GeoJSON for a geographically accurate district map.
- Live map tiles require a valid Google Maps API key at build time.

## Release validation

- Run `cd backend && npm test`.
- Confirm `/health` from the target phone's network.
- Test signup, delivery, OTP verification, logout and login with a fresh account.
- Test offline planning in airplane mode.
- Test microphone permission, no-match, timeout, Bengali locale and speech-service-unavailable states.
- Test image generation with valid server credentials and with credentials removed.
- Build the Android app and test navigation, signup, map, image generation and voice on a real phone before release.
