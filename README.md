<!--
ORBIT Travel Companion
Research-oriented project documentation. Replace illustrative assets with verified data sources.
-->
<div align="center">

# ORBIT Travel Companion

### An offline-first, research-informed travel companion for Bangladesh

**Explore responsibly · Plan locally · Understand the evidence**

[![Android CI](https://github.com/masudurrahman-ds/orbit-travel-companion/actions/workflows/android-apk.yml/badge.svg)](https://github.com/masudurrahman-ds/orbit-travel-companion/actions/workflows/android-apk.yml)
[![Backend Tests](https://github.com/masudurrahman-ds/orbit-travel-companion/actions/workflows/backend-tests.yml/badge.svg)](https://github.com/masudurrahman-ds/orbit-travel-companion/actions)
[![Kotlin](https://img.shields.io/badge/Android-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Backend](https://img.shields.io/badge/Backend-Node.js-339933?logo=nodedotjs&logoColor=white)](https://nodejs.org/)
[![Status](https://img.shields.io/badge/Project-active%20development-orange)](#-project-status)

*A student-led software engineering project exploring offline-first travel planning, destination discovery, and evidence-aware AI assistance.*

</div>

---

## Abstract

ORBIT Travel Companion is a mobile application project designed to support travel discovery and trip planning in Bangladesh. Its engineering direction emphasizes an offline-first experience, a clear distinction between locally available functionality and network-dependent services, and transparent communication about data provenance and system limitations.

This repository contains the Android application, backend services, automated workflows, and project documentation. ORBIT is an evolving prototype: capabilities described as planned or externally configured should not be interpreted as validated production services.

## Research overview

| Field | Description |
|---|---|
| Domain | Mobile computing, travel information systems, human–AI interaction |
| Geographic scope | Bangladesh |
| Primary platform | Android |
| Architectural focus | Offline-capable client with optional network services |
| AI approach | Local deterministic assistance plus separately configured server-side capabilities |
| Evaluation focus | Reliability, usability, offline resilience, transparency and data quality |
| Current maturity | Prototype / active development |

## Research questions

1. Which travel-planning tasks can be completed reliably without a network connection?
2. How should an app distinguish verified destination information from inferred or user-provided content?
3. How can AI-assisted recommendations communicate uncertainty and avoid presenting synthetic outputs as factual evidence?
4. Which failure states most affect trust in account verification, voice input, maps and media generation?
5. What minimum evaluation protocol is appropriate before describing the app as reliable for real-world travel?

These questions guide development; they are not claims that a formal user study has already been conducted.

## System overview

```text
┌──────────────────────────────────────────────┐
│                 ORBIT Android                │
│  Discovery · Trip planning · Map · Assistant │
└───────────────────────┬──────────────────────┘
                        │ HTTPS (when configured)
           ┌────────────┴─────────────┐
           │                          │
┌──────────▼───────────┐   ┌──────────▼───────────┐
│ Local capabilities   │   │ Optional backend     │
│ Offline brain/rules  │   │ Auth · Data · AI API │
│ Cached/local content │   │ Provider integrations│
└──────────────────────┘   └──────────────────────┘
```

The diagram is conceptual. The exact availability of each feature depends on the current source implementation, permissions, device services, deployment configuration and external provider credentials.

## Core principles

- **Offline-first:** retain useful local behavior when network services are unavailable.
- **Evidence-aware:** record source, date, licensing and verification status for factual travel data.
- **Honest AI disclosure:** label generated, inferred, cached and user-submitted content appropriately.
- **Privacy by design:** keep secrets server-side; request permissions only when needed.
- **Accessible interaction:** readable contrast, understandable errors, predictable navigation and responsive layouts.
- **Reproducible engineering:** use automated tests, documented configuration and explicit release checks.

## Repository structure

| Path | Purpose |
|---|---|
| `android/` | Android application source and build configuration |
| `backend/` | API service and automated backend tests |
| `.github/workflows/` | CI and APK build workflows |
| `render.yaml` | Backend deployment blueprint (requires provider configuration) |
| `DEPLOYMENT_AND_RELEASE_CHECKLIST.md` | Deployment and release validation steps |
| `docs/` | Research plan, data governance and evaluation protocol |

## Getting started

### Backend

Requirements: Node.js 20 or compatible supported runtime.

```bash
cd backend
npm install
npm test
npm start
```

Check the health endpoint locally at `http://localhost:8080/health` if the configured server port is 8080.

### Android

Open the `android/` directory in Android Studio, allow Gradle sync to finish, and build the debug variant. For a phone to use a remote API, configure the build with the deployed HTTPS API base URL as described in [the release checklist](DEPLOYMENT_AND_RELEASE_CHECKLIST.md).

### Automated APK

Use the GitHub Actions workflow to inspect the latest build and download its artifact only after the workflow completes successfully. A successful CI build does not replace testing on a physical device.

## Scientific integrity and limitations

- A repository README, badge or architecture diagram is not evidence of validated effectiveness.
- The local assistant is not equivalent to a general-purpose on-device large language model.
- Media generation depends on backend availability and valid server-side provider credentials.
- Video generation must not be advertised as operational unless a working pipeline is implemented and tested.
- A stylized map is not an authoritative administrative boundary dataset.
- Destination facts, travel advice, opening hours, prices, safety notices and routes require trustworthy, current sources.
- Do not add fabricated citations, synthetic survey results or invented performance metrics.

## Documentation

- [Research plan and hypotheses](docs/RESEARCH_PLAN.md)
- [Data provenance and governance](docs/DATA_GOVERNANCE.md)
- [Evaluation and reproducibility protocol](docs/EVALUATION_PROTOCOL.md)
- [Deployment and release checklist](DEPLOYMENT_AND_RELEASE_CHECKLIST.md)

## Contributing

Please include the motivation, implementation details, test evidence, known limitations and any relevant data provenance in pull requests. Avoid committing API keys, OTPs, private user data, unlicensed map data or credentials.

## Citation

No peer-reviewed publication is associated with this repository unless one is explicitly added and linked by the maintainers. For now, cite it as software:

> ORBIT Travel Companion. (2026). *ORBIT Travel Companion: an offline-first travel companion prototype for Bangladesh* [Computer software]. GitHub repository: https://github.com/masudurrahman-ds/orbit-travel-companion

Update this citation if the project obtains a versioned release, DOI or formal publication.

---

<div align="center">

**ORBIT · Research-informed engineering, with limitations made explicit**

</div>
