# ORBIT GitHub Actions APK build

## Upload the project to the empty repository

From the extracted `orbit` project root in PowerShell, run:

```powershell
git init -b main
git add .
git commit -m "Upload ORBIT travel companion source and Android APK workflow"
git remote add origin https://github.com/masudurrahman-ds/orbit-travel-companion.git
git push -u origin main
```

If Git says this folder is already a Git repository, do not run `git init`; instead run `git remote -v`, add the origin only if it is missing, then commit and push:

```powershell
git add .
git commit -m "Fix Android startup state and add APK workflow"
git remote add origin https://github.com/masudurrahman-ds/orbit-travel-companion.git
git push -u origin main
```

The GitHub repository was empty when this workflow was prepared, so no remote history needs to be preserved.

## Download the APK

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Open the newest successful run (green check mark).
4. In **Artifacts**, download `ORBIT-debug-apk` and extract it.
5. Install `app-debug.apk` on the Android device. If Android asks, allow installation from the app used to open the file.

The workflow builds a debug APK. The APK is not signed for Play Store release. If the build fails, open the failed run and expand **Build debug APK** to see the first Kotlin/Gradle error; an artifact is only uploaded after a successful build.
