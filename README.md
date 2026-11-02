# A.U.R.I.X. TV — lightweight Android TV launcher

A minimal native Kotlin launcher for the Redmi `MiTV-AXFR0` / Android 11 device.
It has no network permission, analytics, ads, recommendation feed, WebView, or
third-party runtime libraries. The four tiles are TV/HDMI, YouTube, Hotstar,
and Netflix.

## Important HDMI note

The TV/HDMI tile currently opens Android's TV app (`com.android.tv`). On this
device, `com.droidlogic.tvinput` is the low-level TV-input service and is not
necessarily a launchable activity. The exact activity that opens the HDMI
input chooser has not yet been verified. Test this tile before making A.U.R.I.X.
the permanent Home app. If it opens Live TV rather than the HDMI chooser, use
the TV's input/source button on the remote, or provide the output of:

```powershell
adb shell dumpsys package com.android.tv
adb shell dumpsys package com.droidlogic.tvinput
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER com.android.tv
```

Do not disable `com.droidlogic.tvinput`, `com.droidlogic`, or Android TV system
packages. This launcher does not modify or replace those services.

## Build on Windows

1. Install Android Studio (current stable) and its Android SDK.
2. Open this folder in Android Studio.
3. Allow Gradle sync. Install Android SDK Platform 35 if prompted.
4. Use JDK 17 (Android Studio's bundled JBR is suitable).
5. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. The APK will be at:

   `app\build\outputs\apk\debug\app-debug.apk`

You can also build from PowerShell in this folder after Android Studio has
installed the SDK and Gradle dependencies:

```powershell
.\gradlew.bat assembleDebug
```

This project ZIP does not include the Gradle wrapper executable/JAR. Use the
Android Studio Gradle sync/build UI, or generate a wrapper with the Gradle
installation available on your machine (`gradle wrapper`).

## Install and test over ADB

Enable Network debugging / ADB debugging on the TV and connect:

```powershell
adb connect 192.168.1.6:5555
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Launch it once without changing the default Home app:

```powershell
adb shell monkey -p com.aurix.tv 1
```

Test all four tiles with the TV remote. Check the TV/HDMI tile and verify
Airtel HDMI playback before changing the default launcher.

## Set A.U.R.I.X. as Home

First inspect the Home activities:

```powershell
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME com.aurix.tv
```

If the TV supports the command, set A.U.R.I.X. as Home:

```powershell
adb shell cmd package set-home-activity com.aurix.tv/.MainActivity
```

Some Android TV firmware does not expose `set-home-activity`; if the command
fails, press Home and choose A.U.R.I.X. as the default launcher if prompted.
Do not disable the stock launcher.

## Roll back

```powershell
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
```

If that command is unsupported, use Settings > Apps > Default apps > Home app
(if available), or clear A.U.R.I.X. defaults:

```powershell
adb shell pm clear com.aurix.tv
adb uninstall com.aurix.tv
```

The original Google launcher remains installed and enabled.

## Package IDs used

- YouTube: `com.google.android.youtube.tv`
- Hotstar: `in.startv.hotstar` (verify on this firmware)
- Netflix: `com.netflix.ninja`
- Android TV app: `com.android.tv`

If an app tile reports that its app is missing/disabled, inspect the package:

```powershell
adb shell pm list packages -d
adb shell pm list packages | findstr /i "youtube hotstar netflix"
```

Then enable the relevant installed package with `adb shell pm enable PACKAGE`.


## Build without installing Android Studio (GitHub Actions)

This project includes `.github/workflows/build-apk.yml`. GitHub builds the APK
on its hosted runner, so Android Studio and the Android SDK are not needed on
your laptop.

1. Create a new GitHub repository (private is fine), e.g. `aurix-tv-launcher`.
2. Upload the contents of this project folder to the repository root. Make sure
   `.github/workflows/build-apk.yml` is included. GitHub's web upload can upload
   files/folders; if the hidden `.github` folder is not included, create the
   workflow file in GitHub using **Add file > Create new file** and use the
   exact path `.github/workflows/build-apk.yml`.
3. Open the repository's **Actions** tab.
4. Select **Build AURIX TV APK**, then **Run workflow** (or push to `main`).
5. When the run finishes, open it and download the
   `aurix-tv-debug-apk` artifact. Extract it to get `app-debug.apk`.

Then install with ADB as described above:

```powershell
adb connect 192.168.1.6:5555
adb install -r .\app-debug.apk
adb shell monkey -p com.aurix.tv 1
```

GitHub Actions availability and usage limits depend on your GitHub account and
repository settings. Do not put secrets or unrelated project code in this repo.
