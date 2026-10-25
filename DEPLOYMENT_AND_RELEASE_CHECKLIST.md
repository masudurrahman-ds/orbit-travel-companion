# ORBIT deployment and release checklist

## Deploy a reachable backend

1. Push this repository to GitHub and create a Render Blueprint from it.
2. Review the service settings and set `OPENAI_API_KEY` only in the server environment if image generation is needed. Never put the provider key in Android code or the APK.
3. Confirm `https://YOUR-SERVICE.onrender.com/health` returns a healthy response.
4. Verify the backend's current storage and OTP implementation before production. A reachable API alone does not mean real email/SMS verification is configured.
5. Configure a real OTP delivery provider before enabling production account verification. Do not expose development OTPs to real users.

## Point Android at the backend

The development default `http://10.0.2.2:8080` only works for an Android emulator connecting to a server on the development computer. A physical phone needs a LAN address during local testing or a public HTTPS backend URL. Configure the Android build's API URL accordingly, then sync and rebuild.

## Feature truth table

- Offline trip recommendations: local deterministic Brain; no server required for its supported intents.
- Signup/sign-in: backend required; real OTP delivery requires a configured email/SMS provider.
- Image generation: reachable backend + server-side provider key + network + user consent.
- Video generation: not implemented as a working generation pipeline; do not present it as available.
- Voice: Android speech service and installed language support required. Offline recognition is not guaranteed.
- Bangladesh map: the current shape is a stylised illustration, not authoritative administrative boundary geometry. Replace it with vetted district-boundary GeoJSON before claiming exact district outlines.

## Release validation

- Run `cd backend && npm test`.
- Confirm `/health` from the target phone's network.
- Test signup, OTP delivery, verification, logout, and login using a fresh account.
- Test offline planning with airplane mode enabled.
- Test microphone permission, no-match, timeout, Bengali locale, and speech-service unavailable states.
- Test image generation with valid server credentials and with credentials removed; ensure the UI gives an accurate diagnosis.
- Build the Android app in Android Studio and test on a real device before release.
