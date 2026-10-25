# ORBIT Android build unblock — 25 October 2026

## Why the previous build failed
The Android project used Kotlin `2.0.21` and KSP `2.0.21-1.0.25`, but `app/build.gradle.kts` declared LiteRT-LM with a floating `latest.release` version. The screenshot's compiler output reports Kotlin metadata `2.4.0` while KSP is processing generated Room code, followed by `unexpected jvm signature V`. This is consistent with a dependency/toolchain metadata mismatch, not a source edit needed in generated `ActivityDao_Impl.kt`.

## Changes in this archive
- Removed the floating LiteRT-LM runtime dependency from the Android build.
- Replaced the direct LiteRT-LM adapter with a compile-safe disabled adapter preserving the ViewModel API.
- Kept the deterministic offline ORBIT Brain intact; model-based Bangla/Banglish translation through an imported `.litertlm` file is temporarily unavailable in this profile.
- Did not edit generated build output.

## Test on Windows / Android Studio
Open the `orbit/android` folder in Android Studio and use the configured JDK 17 if available. In the terminal, from `orbit/android`, run:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean
.\gradlew.bat :app:assembleDebug --no-daemon
```

If the build succeeds, APK path:
`android/app/build/outputs/apk/debug/app-debug.apk`

If it still fails, capture the **first** error above the stack trace (not the last stack-trace lines), or run:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace *> build-error.txt
Select-String -Path .\build-error.txt -Pattern 'What went wrong|Could not resolve|e: file:|error:' | Select-Object -First 80
```

## Important limitations
This is a targeted dependency-conflict mitigation, not a claim that an Android build has passed. The archive still needs to be compiled with your installed Android SDK and tested on your device. Re-enable LiteRT-LM only after choosing a fixed version verified against the project's Kotlin/KSP versions; do not use `latest.release`.
