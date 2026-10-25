# Contributing to ORBIT

Thank you for helping improve ORBIT Travel Companion.

## Pull request expectations

Include:

1. **Motivation:** the user or engineering problem addressed.
2. **Approach:** important design decisions and trade-offs.
3. **Verification:** commands run, workflow links and device conditions tested.
4. **Limitations:** known gaps and provider configuration required.
5. **Data provenance:** source, license, retrieval date and attribution for new datasets.
6. **Privacy impact:** any new collection, transmission or retention of user data.

## Engineering standards

- Keep offline functionality independent from optional server integrations where practical.
- Make network and permission failures explicit and recoverable.
- Never embed provider secrets in Android source or committed configuration.
- Do not present generated output as verified fact.
- Add regression tests for bug fixes.
- Keep docs aligned with actual implementation; mark planned functionality as planned.

## Before opening a PR

- Run backend tests if backend code changes.
- Build the Android app if Android code changes.
- Test critical flows on a device when possible.
- Remove credentials, OTPs, personal data and local machine paths from logs.
- Include screenshots only when they demonstrate the changed behavior.
