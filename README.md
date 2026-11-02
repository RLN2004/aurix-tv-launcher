# A.U.R.I.X. TV — lightweight Android TV launcher

A minimal native Kotlin launcher for the Redmi `MiTV-AXFR0` / Android 11 device.
It has no network permission, analytics, ads, recommendation feed, WebView, or
third-party runtime libraries. The four tiles are YouTube, TV/HDMI, Netflix,
and Browser. The Browser tile launches Rhenium (`com.nitesh.rhenium`), which
must be installed separately.

## Browser note

The Browser tile targets Rhenium, an independent Android TV browser built on
Brave/Chromium technology with a TV remote-oriented interface. It is not the
official Brave Android app or an official Brave TV release. Its current
pre-release states Android 10 (API 29) as its minimum; this TV runs Android 11.
The project reports ARM32 physical-device testing, while ARM64 hardware support
remains unverified. Check the TV ABI before choosing its APK.

Project: https://github.com/smolcars/rhenium

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

## Build without Android Studio

The GitHub Actions workflow builds the APK on GitHub-hosted runners.

1. Open the repository's **Actions** tab.
2. Select **Build AURIX TV APK**, then **Run workflow** (or push to `main`).
3. When the run finishes, open it and download the
   `aurix-tv-debug-apk` artifact. Extract it to get `app-debug.apk`.

## UI preview

Open `preview.html` in a browser to see the launcher layout (arrow keys move,
Up focuses Settings). It mirrors the dp values in `MainActivity.kt`.
Add `?missing=3` to preview the "Not installed" state of a tile.

## Debloat (reversible)

`debloat/mitv-debloat.ps1` disables unused Mi TV / Google apps over ADB using
`pm disable-user`, so everything can be restored. It is a dry run unless you
pass `-Apply`.

```powershell
adb connect 192.168.1.6:5555
.\debloat\mitv-debloat.ps1              # dry run
.\debloat\mitv-debloat.ps1 -Apply       # tiers 1-2
.\debloat\mitv-debloat.ps1 -Report      # list Xiaomi/Google packages not yet reviewed
.\debloat\mitv-debloat.ps1 -Restore -Apply
```

Tier 3 (stock launcher, Play Store) is refused unless A.U.R.I.X. is already the
default Home app.

## Install and test over ADB

```powershell
adb connect 192.168.1.6:5555
adb install -r .\app-debug.apk
adb shell am start -n com.aurix.tv/.MainActivity
```

Install Rhenium separately before testing the Browser tile. Test all four tiles
with the TV remote. Check the TV/HDMI tile and verify HDMI playback before
changing the default launcher. Do not disable the stock launcher.
