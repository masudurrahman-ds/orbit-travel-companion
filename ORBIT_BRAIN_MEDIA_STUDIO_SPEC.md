# ORBIT Brain & Travel Media Studio — Engineering Specification

Status: architecture and implementation contract. This document does not claim that external generation providers, video rendering, or production model training are already operational.

## Product goal

Evolve ORBIT from a Bangladesh destination recommender into a traveller-facing assistant and media workspace. Preserve the existing Kotlin/Jetpack Compose app, Room storage, destination corpus, and backend. Extend them through modular capabilities rather than replacing the application.

## Experience modules

1. **Travel companion** — natural-language destination discovery, itinerary drafting, budget-aware comparisons, seasonal guidance, packing suggestions, safety/accessibility notes, and Bengali/English responses.
2. **Traveller memory** — explicit opt-in profile containing budget bands, interests, preferred pace, group type, accessibility preferences, language, saved destinations, and trip history. Users can inspect, edit, export, and clear this profile. Never infer sensitive traits.
3. **Destination research** — retrieve destination records and source references; show verified facts separately from estimates and community tips. Each fact carries source, retrieval date, and confidence/status.
4. **Photo studio** — import/capture, crop, rotate, straighten, exposure, contrast, saturation, temperature, preset filters, text/stickers, watermark, undo/redo, and export. Keep the original immutable and edit non-destructively.
5. **Video studio** — import clips, trim/split/reorder, simple transitions, captions, soundtrack selection with rights metadata, destination templates, preview, and export. Start with on-device Media3/MediaCodec capabilities; do not promise unsupported AI video generation.
6. **Image generation** — provider adapter for original poster/postcard/concept generation. Require an explicit user prompt, return provider/model metadata, and label generated media. If no provider/key/network is configured, report unavailable; do not fake a generated image.
7. **Stock image discovery** — search licensed catalogues (initial candidate adapter: Wikimedia Commons API). Persist asset URL, thumbnail, title, creator, source page, exact licence identifier, attribution text, download URL, retrieval date, and review status. A candidate is not “licensed/approved” until its licence and intended use are checked. Never remove attribution or use a thumbnail as a substitute for a full-resolution asset.
8. **AI activity / explanation panel** — a truthful event trace: request parsed, profile fields used, corpus queries, candidate count, ranking features/score, source links, tools called, elapsed time, and result status. This is an explanation of observable operations, not hidden chain-of-thought. Never fabricate progress or claim a tool succeeded when it failed.

## Architecture

### Android client

- Kotlin + Jetpack Compose + Material 3, MVVM, Room.
- TravelProfileRepository: consented preference storage.
- OrbitAgentCoordinator: plans and dispatches typed tool requests.
- DestinationRetriever: local JSON/Room retrieval and optional remote search.
- RankingRepository: invokes the exported PyTorch-trained model when a valid model artifact exists; deterministic baseline remains an explicitly labelled fallback.
- MediaStudioRepository: photo edit state, video project state, import/export.
- ImageSearchRepository: licensed image catalogue search and licence metadata.
- GenerationProvider: interface only; provider implementation requires configuration and user-visible disclosure.
- AgentEventRepository: stores observable event records for the UI; no private chain-of-thought storage.

### Backend

Keep provider secrets server-side. Android must never embed image/video provider API keys. Add authenticated endpoints behind the existing API server:

- POST /v1/agent/plan — returns typed plan and event IDs.
- POST /v1/agent/execute — executes an allowed tool request.
- GET /v1/agent/events/{sessionId} — observable activity events.
- POST /v1/media/images/search — catalogue search and licence metadata.
- POST /v1/media/images/generate — provider-backed generation; returns job/status and provenance.
- POST /v1/media/video/jobs — optional remote render job; initial editing can remain local.
- GET /v1/media/jobs/{jobId} — job state and output metadata.
- GET/PUT/DELETE /v1/profile — consented traveller profile.

All endpoints validate authentication, payload size, MIME type, rate limits, and ownership. Uploads use signed short-lived URLs; media files are private by default.

## Agent tool contract

Use a strict allow-listed tool registry, not arbitrary code execution:

- search_destinations(query, filters)
- get_destination(destination_id)
- search_stock_images(query, destination_id, licence_allowlist)
- get_itinerary(destination_ids, days, budget, interests)
- rank_destinations(profile, candidates)
- edit_photo(asset_id, operations)
- render_video(project_id, export_preset)
- generate_image(prompt, style, aspect_ratio)

Every tool result includes status, data, source_refs, and error (nullable). The UI renders events from actual tool lifecycle callbacks. A failed or unconfigured tool is shown as unavailable with a reason.

## PyTorch learning strategy

### Destination ranker

Train a pairwise or pointwise ranking model from explicit preference feedback and curated destination comparisons. Candidate input features can include budget fit, interest match, travel-time fit, season suitability, accessibility fit, popularity, and source completeness. Split by destination/user group to reduce leakage. Use a held-out test set, ranking metrics (NDCG@K, MAP@K, pairwise accuracy), calibration, and baseline comparison. Do not train on four synthetic examples and call it production-trained.

The Android adapter may run a small exported MLP. The model artifact must include schema version, feature order, normalization values, training-data version, evaluation metrics, and checksum. If unavailable or schema-mismatched, show a clearly labelled baseline ranking.

### Image understanding

Use pretrained visual embeddings (e.g. an openly licensed, appropriately attributed image encoder) to index approved destination images for semantic retrieval and deduplication. This is an embedding/indexing task, not ranking-model supervision. Store image embedding model/version and asset provenance. Do not train a visual model on every downloaded stock image without rights and a legitimate labelled dataset.

### Personalisation

Do not fine-tune a model on one person's profile or on team members' private data. Use an opt-in profile and explicit feedback features at inference time. The app's project identity may include public, user-approved facts: ORBIT, The Crown Council, and the named creator/team roles only where confirmed by the project owners. Do not infer or expose private biographies.

## Media data model

### MediaAsset
id, kind (photo/video/generated/stock), local_uri, remote_uri, thumbnail_uri, title, creator, source_name, source_page, licence_id, attribution, retrieved_at, review_status, generated_by, model_version, destination_id, created_at.

### EditProject
id, source_asset_ids, operations_json, timeline_json, export_uri, created_at, updated_at.

### TravellerProfile
user_id, consent_version, language, budget_band, interests, travel_pace, group_type, accessibility_preferences, saved_destination_ids, updated_at.

### AgentEvent
id, session_id, sequence, event_type, label, status, input_summary, output_summary, source_refs, started_at, finished_at, error_code.

Do not store secrets or hidden reasoning in AgentEvent.

## Licensing and safety

- Keep licence allowlists configurable by intended use; attribution requirements vary.
- Preserve creator, licence, and source-page information in the UI and exported media credits.
- Treat generated outputs as generated; do not imply a generated scene is a documentary photograph of a real destination.
- Respect catalogue API terms, rate limits, and attribution rules.
- Scan uploads, enforce size/duration limits, and strip location metadata from shared exports unless the user opts in.
- Obtain consent before storing profile preferences; provide clear delete/export controls.
- Avoid face recognition and biometric identification. No model training on team portraits or traveller photos by default.

## Delivery phases and acceptance criteria

### Phase 1 — honest AI foundation
- Agent coordinator, typed tool registry, event trace UI, profile consent and persistence.
- Destination search and ranking with source-backed explanations.
- Tests prove failed tools display failure, not success; event records match actual tool calls.

### Phase 2 — photo studio
- Import, non-destructive edits, undo/redo, export, metadata controls.
- Instrumented tests for import, edit stack, and export.

### Phase 3 — licensed image discovery
- Search adapter, licence/attribution records, review workflow, attribution display.
- Test unknown/missing licence is never marked approved.

### Phase 4 — image generation and video editor
- Configured provider adapter with job status, quotas, error states, and generated-media labels.
- Local video timeline, trim, captions, export, and device compatibility tests.
- Do not advertise generation until a real provider is configured and tested.

### Phase 5 — PyTorch ranker and visual retrieval
- Curated, documented training data; reproducible training; held-out evaluation; exported artifact; Android parity tests.
- Image embeddings indexed with provenance and retrieval evaluation.

## Release gates

- Debug APK builds in CI and installs on an Android Virtual Device.
- No API secrets in APK or repository.
- All external calls have timeout, retry policy, cancellation, and visible error states.
- Accessibility, offline fallback, Bengali/English, and low-bandwidth behavior tested.
- README and demo explicitly distinguish implemented, provider-configured, and planned capabilities.
