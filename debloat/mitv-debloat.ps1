<#
.SYNOPSIS
  Reversible debloat for a Xiaomi/Redmi TV over adb (run on your laptop).

.DESCRIPTION
  Dry run by default: it only prints what it WOULD change. Add -Apply to act.
  It never uninstalls anything. It uses `pm disable-user --user 0`, which is
  fully reversible with -Restore. Only packages that are actually installed and
  still enabled on your TV are touched, so packages you already removed are skipped.

  Tiers
    1  Low risk      Unused apps (Prime Video, Play Games, gallery, music, ...)
    2  Feeds / ads   Recommendation and content-feed services
    3  Launcher/store Stock launcher + Play Store. ONLY after A.U.R.I.X. is the
                      default Home app. The script refuses otherwise.

.EXAMPLES
  .\mitv-debloat.ps1                  # dry run, tiers 1 and 2
  .\mitv-debloat.ps1 -Apply           # disable tiers 1 and 2
  .\mitv-debloat.ps1 -Report          # list other candidates you may have missed
  .\mitv-debloat.ps1 -Tier 3 -Apply   # launcher/store (needs A.U.R.I.X. as Home)
  .\mitv-debloat.ps1 -Restore -Apply  # re-enable everything this script disables
#>
[CmdletBinding()]
param(
    [int[]]$Tier = @(1, 2),
    [switch]$Apply,
    [switch]$Restore,
    [switch]$Report,
    [string]$Serial
)

# Not 'Stop': Windows PowerShell 5.1 treats any adb stderr text (for example
# "* daemon started") as a terminating error under 2>&1. Errors are checked explicitly.
$ErrorActionPreference = 'Continue'

# Candidate packages. Source: a published Mi TV debloat write-up
# (https://bhupixb.github.io/blog/11-minimal-mitv-launcher/). Your model
# (MiTV-AXFR0) may not have all of them; missing ones are simply skipped.
$Candidates = @{
    1 = @(
        'com.amazon.amazonvideo.livingroom'   # Prime Video
        'com.google.android.play.games'
        'com.google.android.youtube.tvmusic'
        'com.google.android.videos'           # Google TV / Play Movies
        'com.google.android.backdrop'         # Ambient-mode screensaver
        'com.xiaomi.mimusic2'
        'com.mitv.gallery'
        'com.mitv.videoplayer'
        'com.xiaomi.mitv.mediaexplorer'
        'org.chromium.webview_shell'
        'com.android.gallery3d'
        'com.android.printspooler'
        'com.android.camera2'
        'com.android.smspush'
    )
    2 = @(
        'com.google.android.tvrecommendations'
        'com.google.android.leanbacklauncher.recommendations'
        'com.xm.webcontent'                   # PatchWall content feed
        'com.mitv.tvhome.mitvplus'
    )
    3 = @(
        'com.mitv.tvhome.atv'                 # Mi home sources
        'com.google.android.tvlauncher'       # stock Google TV launcher
        'com.xiaomi.mitv.tvmanager'           # may affect Mi updates/cleaner
        'com.android.vending'                 # Play Store: no more app installs/updates
    )
}

# Never touched, even if somebody adds them to a list above.
$Protected = @(
    'com.aurix.tv', 'com.nitesh.rhenium', 'com.netflix.ninja',
    'com.google.android.youtube.tv', 'com.google.android.gms', 'com.google.android.gsf',
    'com.android.tv', 'com.android.tv.settings', 'com.android.providers.tv',
    'com.android.providers.settings', 'com.android.systemui', 'com.android.settings',
    'com.droidlogic.tvinput', 'com.droidlogic.droidlivetv', 'com.droidlogic.tv.settings'
)
function Test-Protected([string]$pkg) {
    return ($Protected -contains $pkg) -or ($pkg -like 'com.droidlogic*')
}

$Log = Join-Path $PSScriptRoot 'debloat-log.txt'

function Invoke-Adb {
    $a = @()
    if ($Serial) { $a += @('-s', $Serial) }
    $a += $args
    (& adb @a 2>&1) | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' }
}

function Get-Packages([string[]]$flags) {
    Invoke-Adb shell pm list packages @flags |
        Where-Object { $_ -like 'package:*' } |
        ForEach-Object { $_ -replace '^package:', '' }
}

# ---- sanity checks -------------------------------------------------------
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw 'adb not found. Install Android platform-tools and add it to PATH.'
}
$state = Invoke-Adb get-state
if ("$state" -ne 'device') {
    throw "No adb device ready (adb get-state said: $state). Run: adb connect <tv-ip>:5555"
}
$model = Invoke-Adb shell getprop ro.product.model
Write-Host "Connected to: $model" -ForegroundColor Cyan
if (-not $Apply) { Write-Host 'DRY RUN - nothing will change. Add -Apply to act.' -ForegroundColor Yellow }

$installed = Get-Packages @()
$disabled  = Get-Packages @('-d')

# ---- restore -------------------------------------------------------------
if ($Restore) {
    $all = $Candidates.Values | ForEach-Object { $_ }
    $toRestore = $all | Where-Object { ($installed -contains $_) -and ($disabled -contains $_) }
    if (-not $toRestore) { Write-Host 'Nothing to restore.'; return }
    foreach ($pkg in $toRestore) {
        if ($Apply) {
            $out = Invoke-Adb shell pm enable $pkg
            Write-Host "enable  $pkg  ->  $out"
            Add-Content $Log "$(Get-Date -Format s) enabled $pkg"
        } else { Write-Host "would enable  $pkg" }
    }
    return
}

# ---- report: things you may have missed ----------------------------------
if ($Report) {
    $known = $Candidates.Values | ForEach-Object { $_ }
    $pattern = '^(com\.xiaomi|com\.mitv|com\.mi\.|com\.miui|com\.xm\.|com\.mediatek|com\.google\.android\.(?!gms|gsf|youtube\.tv))'
    $enabled = $installed | Where-Object { $disabled -notcontains $_ }
    $extra = $enabled | Where-Object {
        ($_ -match $pattern) -and ($known -notcontains $_) -and -not (Test-Protected $_)
    } | Sort-Object
    $file = Join-Path $PSScriptRoot 'debloat-unreviewed.txt'
    $extra | Set-Content $file
    Write-Host "`n$($extra.Count) enabled Xiaomi/Google packages are not in any tier list." -ForegroundColor Cyan
    Write-Host "Saved to $file - paste that file to Claude to classify them."
    $extra | ForEach-Object { Write-Host "  $_" }
    return
}

# ---- tier 3 guard --------------------------------------------------------
if ($Tier -contains 3 -and $Apply) {
    $homeInfo = (Invoke-Adb shell cmd package resolve-activity '--brief' '-c' android.intent.category.HOME '-a' android.intent.action.MAIN) -join ' '
    if ($homeInfo -notmatch 'com\.aurix\.tv') {
        throw "Tier 3 refused: A.U.R.I.X. is not the default Home app (current: $homeInfo). Set it as Home first."
    }
}

# ---- disable -------------------------------------------------------------
$changed = 0
foreach ($t in ($Tier | Sort-Object)) {
    if (-not $Candidates.Contains($t)) { Write-Warning "Unknown tier $t"; continue }
    Write-Host "`n== Tier $t ==" -ForegroundColor Cyan
    foreach ($pkg in $Candidates[$t]) {
        if (Test-Protected $pkg)            { Write-Host "protected      $pkg"; continue }
        if ($installed -notcontains $pkg)   { Write-Host "not present    $pkg" -ForegroundColor DarkGray; continue }
        if ($disabled -contains $pkg)       { Write-Host "already off    $pkg" -ForegroundColor DarkGray; continue }
        if (-not $Apply)                    { Write-Host "would disable  $pkg"; continue }

        $out = (Invoke-Adb shell pm disable-user --user 0 $pkg) -join ' '
        if ($out -match 'disabled-user') {
            Write-Host "disabled       $pkg" -ForegroundColor Green
            Add-Content $Log "$(Get-Date -Format s) disabled $pkg"
            $changed++
        } else {
            Write-Host "FAILED         $pkg  ($out)" -ForegroundColor Red
        }
    }
}
if ($Apply) {
    Write-Host "`nDone: $changed package(s) disabled. Restore with: .\mitv-debloat.ps1 -Restore -Apply"
    Write-Host 'Reboot the TV, then check HDMI, YouTube, Netflix and Settings still work.'
}
