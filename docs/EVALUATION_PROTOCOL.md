# Evaluation and Reproducibility Protocol

**Status:** Proposed protocol; results must be added only after execution.

## 1. Purpose

Evaluate ORBIT's supported workflows under reproducible online, offline and failure conditions. A build passing CI is necessary but does not demonstrate usability or field reliability.

## 2. Test environment record

For each run, record:

- Git commit SHA and app version.
- Device model, Android version and screen size.
- Network condition and whether airplane mode is enabled.
- Backend URL environment (never publish credentials).
- App permissions and speech-service configuration.
- Test date, tester role and test-data version.

## 3. Functional test matrix

| Area | Scenario | Expected behavior | Evidence |
|---|---|---|---|
| Navigation | Back from nested screen | Return to prior screen or safe home fallback | Screen recording / test |
| Offline brain | Supported planning prompt with airplane mode | Local response, with limits stated | Test output |
| Network failure | API unreachable | Clear error and retry path; no false success | Logs + screen |
| Signup | Email provider configured | Code delivered and verification succeeds | Redacted test record |
| Signup | Email provider missing | Clear configuration error; no false delivery claim | API response |
| Voice | Permission denied | Explain permission and provide text fallback | Device test |
| Voice | Speech service unavailable | Recoverable error; text input remains available | Device test |
| Images | Provider configured | Image returned and rendered | Redacted request/result |
| Images | Provider unavailable | Actionable error, no misleading placeholder | Device test |
| Maps | Destination marker selected | Selected destination details correspond to marker | Screenshot + test data |
| Map data | Unknown provenance | Mark as unverified; do not imply authoritative accuracy | Data audit |
| Layout | Small screen and large font | Controls remain readable and usable | Screenshot suite |
| Release | Clean CI build | APK artifact is created and installable | Workflow run + device install |

## 4. Suggested metrics

- Task success rate = successful task completions / attempted tasks.
- Median and 95th percentile response time for supported actions.
- Offline task coverage = tasks completed without network / all designated offline tasks.
- Recovery rate = failed tasks recovered without restarting / failed tasks.
- Provenance comprehension measured with a pre-defined question set.
- Crash and ANR rate during a defined test session.

Define denominators, exclusion criteria and measurement windows before collecting results. Report sample size and uncertainty; do not invent values.

## 5. Procedure

1. Reset the test environment and use a known test account.
2. Execute a fixed, versioned task script.
3. Capture pass/fail and time-to-completion.
4. Repeat in each network and permission condition.
5. Record defects with reproducible steps, expected behavior and actual behavior.
6. Re-run regression tests after fixes.
7. Publish results with device details, commit SHA and known limitations.

## 6. Release acceptance criteria

A release candidate should not be described as validated until:

- Automated backend tests pass.
- The Android build succeeds from a clean CI runner.
- The APK installs and launches on a physical device.
- Critical flows are tested against a deployed backend.
- Offline behavior is verified in airplane mode.
- Signup, OTP delivery, voice, map and image-generation failure states are exercised.
- No secrets or real user data are exposed in logs or repository history.

## 7. Results template

| Test ID | Commit | Device / OS | Condition | Outcome | Duration | Evidence reference | Notes |
|---|---|---|---|---|---:|---|---|
| TBD | TBD | TBD | TBD | Not run | — | — | Populate after execution |

Do not replace “Not run” with a pass without evidence.
