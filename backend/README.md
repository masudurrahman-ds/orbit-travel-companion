# ORBIT backend

Zero-dependency Node.js server (Node 18+). It provides accounts, visits, the leaderboard, community spots,
the AI guide, and the Privacy / Terms pages.

## Run it
```
cd backend
cp .env.example .env        # then export the variables in your shell, or set them in your host dashboard
export ORBIT_SECRET=$(openssl rand -hex 32)
export ORBIT_DEV_OTP=1      # development only: returns the sign-up code in the response
# optional cloud guide: export ORBIT_CLOUD_AI=1 ANTHROPIC_API_KEY=...
npm start                   # http://localhost:8080
npm test                    # 46 tests, no network or key needed
```
Privacy page: `/privacy`. Terms: `/terms`. Health check: `/health`.

## What it does
| Area | Endpoints |
|---|---|
| Accounts | `POST /api/auth/signup` (email or Bangladesh mobile, required Terms + Privacy agreement, optional AI / location / leaderboard agreements), `/api/auth/verify` (6-digit code), `/api/auth/login` |
| Privacy rights | `GET /api/me`, `PUT /api/me/consent`, `GET /api/me/export`, `DELETE /api/me` |
| Visits | `GET/POST /api/visits`, `DELETE /api/visits/:destinationId` |
| Rankings | `GET /api/leaderboard` (only travellers who opted in; shows display name and counts only) |
| Community spots | `GET /api/destinations`, `POST /api/destinations/submit` (pending), `POST /api/admin/submissions/:id` with header `x-admin-token` |
| Guide | `POST /api/agent/chat` (ORBIT Brain by default), `GET/DELETE /api/memory` |
| Planner | `POST /api/plan` (budget / value / fancy ranking) |

## The guide: ORBIT Brain (native, no outside AI)
The default guide is **ORBIT Brain** (`src/brain.js`), the app's own agent. It needs no API key and makes no network calls.
It normalises the message, extracts budget, days, travellers, month, interests and tour style, finds named places,
detects the intent (plan, compare, cost, itinerary, best time, distance, progress, remember, forget, follow-ups), applies
saved preferences and visit history as defaults, calls the planner, and writes a short answer. It never asks questions:
it fills in sensible defaults and says what it assumed. The Kotlin and Swift apps run a line-by-line port of this file
on the phone, so the guide works offline and nothing leaves the device. Shared vocabulary lives in `data/brain-data.json`.

`POST /api/agent/chat` runs the same Brain on the server (for clients that want it). It is **not a trained language
model**: it is a rule-based engine, so it understands the kinds of requests it was built for, not open-ended chat.
Extend it by editing `data/brain-data.json` (words) and `src/brain.js` (behaviour), add a test, then port the change.

### Optional language layer (`src/translation.js`)
An on-device language model can be added to understand Bangla and loose wording. It is used only when the Brain replies
`understood: false`, and it only translates the message into one short English request. The output is sanitised, the Brain
re-checks it, and the model's own words are never shown, so every price, place and number still comes from the app's data.
Tests use a fake translator; the real models run only in the apps.

### Optional cloud guide (off by default)
Set `ORBIT_CLOUD_AI=1` and `ANTHROPIC_API_KEY` to route `/api/agent/chat` through a hosted model with the same tools
(`src/agent.js`). Travellers must also agree to "Cloud AI guide". Leave both unset to stay fully self-contained.

## Before real users
- Replace the JSON file store with a database (PostgreSQL) and add backups.
- Send the verification code through a real email / SMS provider (`sendOtp` hook in `createApp`). Never run with `ORBIT_DEV_OTP=1` in production.
- Serve only over HTTPS and set a strong `ORBIT_SECRET`.
- Replace `[team contact email]` in `web/privacy.html` and have both pages reviewed by a qualified person.
- Leaderboard visits are self-reported, so rankings can be gamed. Treat them as for fun.

## Optional AI travel-photo generation

ORBIT's Android Guide now includes a **Create a travel photo** panel. It calls the authenticated `POST /api/agent/image` endpoint; the provider key remains on the server and is never embedded in the APK. Image generation is disabled until explicitly configured.

Set `OPENAI_API_KEY` in the backend environment and optionally set `ORBIT_IMAGE_MODEL` (default `gpt-image-1`). Image generation requires the signed-in user to have enabled AI-guide consent. The endpoint is rate-limited and accepts a maximum 1,200-character prompt. Do not commit the API key to Git. Image-generation provider usage may incur charges; configure provider spending limits before enabling it publicly.

## Voice input

Android Guide includes speech input with English (`en-US`) and Bengali (`bn-BD`) recognition choices. Banglish is routed through English speech recognition because Android's speech service generally does not expose a separate Banglish locale; recognition quality and offline availability depend on the device's installed speech provider and language packs. The app requests microphone permission only when the user taps the microphone. Voice is transcribed on the device/service and the recognized text is inserted into the composer for review before sending.
