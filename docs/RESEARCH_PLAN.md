# Research Plan

**Project:** ORBIT Travel Companion  
**Version:** 0.1 — planning document  
**Status:** Proposed; not a report of completed experiments

## 1. Background

Travel applications combine location information, planning tools, user-generated content and network services. In environments where connectivity may be inconsistent, the ability to complete selected tasks offline can improve continuity. At the same time, inaccurate destination data or overconfident AI-generated advice can mislead users. ORBIT explores the engineering trade-offs between offline utility, information quality and transparent AI interaction for travel within Bangladesh.

## 2. Problem statement

A travel companion may fail users when core flows depend on network access, when map visualizations are mistaken for authoritative geography, or when generated recommendations do not disclose their evidence. This project aims to establish a measurable and reproducible approach to offline task support and trustworthy travel information.

## 3. Aim

Design and evaluate an Android travel companion prototype that provides useful offline planning for supported tasks while clearly identifying services that require connectivity or external providers.

## 4. Objectives

1. Define which user tasks must work offline and which require a server.
2. Separate verified destination records, cached content, user submissions and AI-generated material.
3. Make network, authentication, permission and provider failures understandable to users.
4. Measure task completion, response time, crash-free sessions and offline behavior.
5. Document data sources, licenses, limitations and reproducible test procedures.

## 5. Research questions

- RQ1: What proportion of predefined planning tasks can be completed in airplane mode?
- RQ2: How often do users correctly understand whether a recommendation is sourced, cached, inferred or generated?
- RQ3: Which failure states cause the greatest task abandonment?
- RQ4: How do response time and task completion differ between offline and online modes?

## 6. Proposed hypotheses

These are hypotheses to test, not established findings.

- H1: A well-defined local task set will allow completion of selected planning tasks without connectivity.
- H2: Explicit provenance labels will improve users' ability to distinguish sourced facts from generated suggestions.
- H3: Specific recovery guidance will reduce repeated failed actions compared with generic error messages.

## 7. Scope

**In scope:** Android UX, local planning logic, optional API services, destination-data provenance, offline behavior and usability testing.

**Out of scope unless separately implemented:** emergency dispatch, authoritative navigation, guaranteed real-time transport data, medical or legal advice, production-scale safety certification, and claims that the assistant is a general-purpose local LLM.

## 8. Method

1. Create a versioned dataset of test tasks and expected outcomes.
2. Define online, offline, slow-network, permission-denied and provider-unavailable test conditions.
3. Execute the same scripted tasks across conditions on documented devices.
4. Record success/failure, time-to-completion, error category and recovery outcome.
5. Conduct a small usability study only after obtaining appropriate informed consent and institutional approval where required.
6. Report all exclusions, missing observations, limitations and deviations from the protocol.

## 9. Ethics and privacy

Do not collect precise location, contact information, voice recordings or travel history for research unless necessary, consented to, secured and covered by applicable institutional requirements. Use synthetic test accounts for automated testing. Avoid publishing personal data in issues, logs or test fixtures.

## 10. Expected outputs

- Reproducible test suite for offline and network-dependent tasks.
- Data dictionary and provenance register.
- Failure-mode analysis and usability findings.
- Versioned technical documentation.

No empirical result should be reported until the relevant tests or study have actually been conducted.
