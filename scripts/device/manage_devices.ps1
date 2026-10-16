<#
.SYNOPSIS
    Interactive Device Manager for ResQMesh
    Connect, disconnect, build, install, and launch ResQMesh on Android devices without Android Studio.
#>

$ErrorActionPreference = "Continue"

# Locate adb.exe
$adbCandidates = @(
    "adb",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
)

$adb = $null
foreach ($cand in $adbCandidates) {
    if (Get-Command $cand -ErrorAction SilentlyContinue) {
        $adb = $cand
        break
    } elseif (Test-Path $cand) {
        $adb = $cand
        break
    }
}

if (-not $adb) {
    Write-Host "ERROR: adb.exe could not be found." -ForegroundColor Red
    Write-Host "Please ensure Android SDK platform-tools is installed." -ForegroundColor Yellow
    exit 1
}

# Resolve APK path relative to repo root
$repoRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$apkPath = "$repoRoot\app\build\outputs\apk\debug\app-debug.apk"
$package = "com.example.testresqmesh"
$activity = "$package/.MainActivity"

function Get-ConnectedDevices {
    $raw = & $adb devices
    $list = @()
    foreach ($line in $raw) {
        if ($line -match "^(\S+)\s+(device|offline|unauthorized)\b") {
            $list += [PSCustomObject]@{
                Id = $matches[1].Trim()
                Status = $matches[2].Trim()
            }
        }
    }
    return $list
}

function Get-DeduplicatedActiveDevices {
    $all = @(Get-ConnectedDevices)
    $active = @($all | Where-Object { $_.Status -eq "device" })
    if ($active.Count -eq 0) { return @() }

    # Group devices by physical IP if present to prevent mDNS alias duplicates
    $ipDevices = @($active | Where-Object { $_.Id -match "^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+" })
    $otherDevices = @($active | Where-Object { $_.Id -notmatch "^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+" })

    if ($ipDevices.Count -gt 0) {
        # Filter out mDNS aliases like adb-xxx._adb-tls-connect._tcp that point to same endpoints
        $filteredOthers = @($otherDevices | Where-Object { $_.Id -notmatch "(_adb-tls-connect|\._tcp|\._adb)" })
        return @($ipDevices + $filteredOthers)
    }

    return $active
}

function Show-ConnectedDevices {
    $devs = @(Get-ConnectedDevices)
    Write-Host "`n--- Current Connected Devices ---" -ForegroundColor Cyan
    if ($devs.Count -eq 0) {
        Write-Host "No devices currently connected." -ForegroundColor Yellow
    } else {
        for ($i = 0; $i -lt $devs.Count; $i++) {
            $color = if ($devs[$i].Status -eq "device") { "Green" } else { "Red" }
            $model = ""
            if ($devs[$i].Status -eq "device") {
                $m = (& $adb -s $devs[$i].Id shell getprop ro.product.model 2>$null)
                if ($m) { $model = " ($($m.Trim()))" }
            }
            Write-Host "  [$($i + 1)] $($devs[$i].Id)$model  [$($devs[$i].Status)]" -ForegroundColor $color
        }
    }
    Write-Host "---------------------------------`n" -ForegroundColor Cyan
    return $devs
}

function Sync-WirelessDevices {
    Write-Host "Scanning local Wi-Fi for Wireless Debugging devices (mDNS)..." -ForegroundColor Cyan
    $mdnsOutput = & $adb mdns services 2>&1
    $endpoints = @()

    foreach ($line in $mdnsOutput) {
        if ($line -match "(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+)") {
            $endpoints += $matches[1]
        }
    }

    $endpoints = @($endpoints | Select-Object -Unique)
    if ($endpoints.Count -gt 0) {
        foreach ($ep in $endpoints) {
            & $adb connect $ep | Out-Null
        }
    }
}

function Connect-AllMdnsDevices {
    Sync-WirelessDevices
    $devs = Show-ConnectedDevices
    if ($devs.Count -eq 0) {
        Write-Host "No mDNS wireless devices found automatically." -ForegroundColor Yellow
        Write-Host "Tips:" -ForegroundColor DarkGray
        Write-Host "  1. Turn Wireless Debugging ON in phone Developer Options." -ForegroundColor DarkGray
        Write-Host "  2. Ensure your PC and phone are on the exact same Wi-Fi network." -ForegroundColor DarkGray
        Write-Host "  3. You can also connect manually using Option 2." -ForegroundColor DarkGray
    }
}

function Connect-ManualDevice {
    $ipPort = Read-Host "Enter device IP and Port (e.g. 192.168.1.3:41369)"
    if ($ipPort) {
        Write-Host "Connecting to $ipPort..." -ForegroundColor Cyan
        & $adb connect $ipPort.Trim()
        Show-ConnectedDevices | Out-Null
    }
}

function Disconnect-AllDevices {
    Write-Host "`nDisconnecting all wireless devices..." -ForegroundColor Yellow
    & $adb disconnect
    Show-ConnectedDevices | Out-Null
}

function Disconnect-SpecificDevice {
    $devs = @(Show-ConnectedDevices)
    if ($devs.Count -eq 0) { return }

    $selection = Read-Host "Enter the device number to disconnect (1-$($devs.Count))"
    if ($selection -match "^\d+$") {
        $idx = [int]$selection - 1
        if ($idx -ge 0 -and $idx -lt $devs.Count) {
            $target = $devs[$idx].Id
            Write-Host "Disconnecting $target..." -ForegroundColor Yellow
            & $adb disconnect $target
            Show-ConnectedDevices | Out-Null
            return
        }
    }
    Write-Host "Invalid selection." -ForegroundColor Red
}

function Ensure-ApkBuilt {
    param([switch]$Force)
    if ($Force -or (-not (Test-Path $apkPath))) {
        Write-Host "`nBuilding latest debug APK with Gradle..." -ForegroundColor Cyan
        $gradleCmd = if ($env:OS -match "Windows") { ".\gradlew.bat" } else { "./gradlew" }
        Push-Location $repoRoot
        try {
            & $gradleCmd assembleDebug
            if ($LASTEXITCODE -ne 0) {
                Write-Host "Gradle build failed with exit code $LASTEXITCODE." -ForegroundColor Red
                return $false
            }
        } finally {
            Pop-Location
        }
    }
    return (Test-Path $apkPath)
}

function Install-App {
    param (
        [string]$DeviceId
    )

    if (-not (Test-Path $apkPath)) {
        Write-Host "APK not found at $apkPath." -ForegroundColor Red
        return $false
    }

    $model = (& $adb -s $DeviceId shell getprop ro.product.model 2>$null)
    $modelStr = if ($model) { $model.Trim() } else { "Android Device" }

    Write-Host "  -> Installing to $DeviceId ($modelStr)..." -ForegroundColor Cyan
    $installOut = & $adb -s $DeviceId install -r $apkPath 2>&1
    if ($LASTEXITCODE -ne 0 -or ($installOut -match "Failure|INSTALL_FAILED")) {
        Write-Host "     Install failed: $installOut" -ForegroundColor Red
        return $false
    }
    Write-Host "     Install succeeded." -ForegroundColor Green

    Write-Host "  -> Waking screen & launching ResQMesh..." -ForegroundColor Cyan
    # Wake screen if sleeping
    & $adb -s $DeviceId shell input keyevent 224 2>$null | Out-Null
    # Bring app to foreground
    & $adb -s $DeviceId shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $activity --activity-brought-to-front 2>$null | Out-Null
    # Fallback to ensure app is foregrounded
    & $adb -s $DeviceId shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
    Write-Host "     Launched successfully on $modelStr." -ForegroundColor Green
    return $true
}

function Install-AllDevices {
    Write-Host "`n=== Install & Launch on ALL Devices ===" -ForegroundColor Green

    $buildChoice = Read-Host "Build latest debug APK before installing? [Y/n] (Press Enter for Yes)"
    if ($buildChoice -eq "" -or $buildChoice -match "^[yY]") {
        $built = Ensure-ApkBuilt -Force
        if (-not $built) {
            Write-Host "Aborting installation due to build failure." -ForegroundColor Red
            return
        }
    } elseif (-not (Test-Path $apkPath)) {
        $built = Ensure-ApkBuilt -Force
        if (-not $built) { return }
    }

    Sync-WirelessDevices

    $devs = @(Get-DeduplicatedActiveDevices)
    if ($devs.Count -eq 0) {
        Write-Host "No active devices available to install." -ForegroundColor Red
        return
    }

    Write-Host "`nTargeting $($devs.Count) distinct active device(s):" -ForegroundColor Cyan
    for ($i = 0; $i -lt $devs.Count; $i++) {
        $m = (& $adb -s $devs[$i].Id shell getprop ro.product.model 2>$null)
        $mName = if ($m) { $m.Trim() } else { "Unknown" }
        Write-Host "  [$($i+1)] $($devs[$i].Id) ($mName)" -ForegroundColor DarkCyan
    }

    $successCount = 0
    for ($i = 0; $i -lt $devs.Count; $i++) {
        $targetId = [string]$devs[$i].Id
        Write-Host "`n[$($i + 1)/$($devs.Count)] Device: $targetId" -ForegroundColor Yellow
        $ok = Install-App -DeviceId $targetId
        if ($ok) { $successCount++ }
    }

    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "Deployment Complete: $successCount of $($devs.Count) device(s) updated and launched." -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
}

function Install-SpecificDevice {
    $devs = @(Show-ConnectedDevices)
    $active = @($devs | Where-Object { $_.Status -eq "device" })
    if ($active.Count -eq 0) {
        Write-Host "No active devices available." -ForegroundColor Red
        return
    }

    $selection = Read-Host "Enter the device number to install to (1-$($devs.Count))"
    if ($selection -match "^\d+$") {
        $idx = [int]$selection - 1
        if ($idx -ge 0 -and $idx -lt $devs.Count) {
            $built = Ensure-ApkBuilt
            if ($built) {
                Install-App -DeviceId ([string]$devs[$idx].Id) | Out-Null
                Write-Host "`nDone!" -ForegroundColor Green
            }
            return
        }
    }
    Write-Host "Invalid selection." -ForegroundColor Red
}

function Launch-AllDevices {
    Sync-WirelessDevices
    $devs = @(Get-DeduplicatedActiveDevices)
    if ($devs.Count -eq 0) {
        Write-Host "No active devices connected." -ForegroundColor Red
        return
    }

    foreach ($d in $devs) {
        $targetId = [string]$d.Id
        $m = (& $adb -s $targetId shell getprop ro.product.model 2>$null)
        $mStr = if ($m) { $m.Trim() } else { $targetId }
        Write-Host "Launching ResQMesh on $mStr ($targetId)..." -ForegroundColor Cyan
        & $adb -s $targetId shell input keyevent 224 2>$null | Out-Null
        & $adb -s $targetId shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $activity --activity-brought-to-front 2>$null | Out-Null
        & $adb -s $targetId shell monkey -p $package -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
    }
    Write-Host "`nResQMesh launched on all devices." -ForegroundColor Green
}

# --- Interactive Main Loop ---
do {
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "         ResQMesh Device Manager" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host " [1] Auto-connect all Wi-Fi devices (mDNS)"
    Write-Host " [2] Connect device manually (IP:Port)"
    Write-Host " [3] Disconnect / Remove all devices"
    Write-Host " [4] Disconnect / Remove a specific device"
    Write-Host " [5] Install APK & Launch on ALL devices"
    Write-Host " [6] Install APK & Launch on a SPECIFIC device"
    Write-Host " [7] Launch app on ALL devices (no reinstall)"
    Write-Host " [8] Show connected devices status"
    Write-Host " [0] Exit"
    Write-Host "============================================" -ForegroundColor Green

    $choice = Read-Host "Select an option (0-8)"

    switch ($choice) {
        "1" { Connect-AllMdnsDevices }
        "2" { Connect-ManualDevice }
        "3" { Disconnect-AllDevices }
        "4" { Disconnect-SpecificDevice }
        "5" { Install-AllDevices }
        "6" { Install-SpecificDevice }
        "7" { Launch-AllDevices }
        "8" { Show-ConnectedDevices | Out-Null }
        "0" { Write-Host "`nGoodbye!" -ForegroundColor Cyan; break }
        default { Write-Host "Invalid option. Please choose between 0 and 8." -ForegroundColor Yellow }
    }
} while ($choice -ne "0")
