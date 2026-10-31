<#
.SYNOPSIS
    Interactive Device Manager for ResQMesh
    Connect, disconnect, build, install, launch, clear and uninstall ResQMesh on Android devices without Android Studio.
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
# Install options pick the variant; debug stays the default for testing.
$variant = "debug"
$apkPath = "$repoRoot\app\build\outputs\apk\debug\app-debug.apk"
# Install options set this from the target phones; empty builds every ABI.
$buildAbis = @()
# Read the applicationId from Gradle so a package rename needs no script change.
$gradleFile = "$repoRoot\app\build.gradle.kts"
$package = "edu.cit.resqmesh"
if (Test-Path $gradleFile) {
    $idMatch = Select-String -LiteralPath $gradleFile -Pattern 'applicationId\s*=\s*"([^"]+)"' | Select-Object -First 1
    if ($idMatch) { $package = $idMatch.Matches[0].Groups[1].Value }
}

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

# Reads the identity fields in one shell call so a menu action does not repeat getprop per field.
function Get-DeviceInfo {
    param ([string]$DeviceId)
    $props = @(& $adb -s $DeviceId shell "getprop ro.serialno; getprop ro.product.model; getprop ro.product.cpu.abilist" 2>$null)
    return [PSCustomObject]@{
        Id = $DeviceId
        Serial = "$($props[0])".Trim()
        Model = "$($props[1])".Trim()
        Abi = ("$($props[2])".Trim() -split ",")[0]
    }
}

function Get-DeduplicatedActiveDevices {
    $all = @(Get-ConnectedDevices)
    $active = @($all | Where-Object { $_.Status -eq "device" })
    if ($active.Count -eq 0) { return @() }

    # One phone can appear over USB, Wi-Fi and mDNS aliases; keep one entry per hardware serial.
    # USB entries (no ':' and no mDNS suffix) sort first, so USB is preferred.
    $ordered = @($active | Sort-Object { if ($_.Id -match ":|\._tcp|_adb-tls") { 1 } else { 0 } })
    $seen = @{}
    $result = @()
    foreach ($d in $ordered) {
        $info = Get-DeviceInfo -DeviceId $d.Id
        $key = if ($info.Serial) { $info.Serial } else { $d.Id }
        if (-not $seen.ContainsKey($key)) {
            $seen[$key] = $true
            $result += $info
        }
    }
    return $result
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

# Lists phones advertising Wireless Debugging. Pairing services are skipped because adb connect cannot use them.
function Get-MdnsEndpoints {
    $list = @()
    foreach ($line in (& $adb mdns services 2>&1)) {
        if ("$line" -match "^(\S+)\s+_adb(-tls-connect)?\._tcp\S*\s+(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+)") {
            $list += [PSCustomObject]@{ Name = $matches[1]; Endpoint = $matches[3] }
        }
    }
    return @($list | Sort-Object Endpoint -Unique)
}

# Android picks a new port whenever Wireless Debugging restarts, so old addresses stay behind as offline.
function Remove-StaleWirelessDevices {
    foreach ($d in @(Get-ConnectedDevices)) {
        if ($d.Status -eq "offline" -and $d.Id -match ":") {
            & $adb disconnect $d.Id 2>&1 | Out-Null
        }
    }
}

# Connects every advertised phone. SettleSeconds keeps scanning for phones that appear late, such as after adb starts.
function Sync-WirelessDevices {
    param ([int]$SettleSeconds = 0)
    Write-Host "Scanning local Wi-Fi for Wireless Debugging devices (mDNS)..." -ForegroundColor Cyan
    Remove-StaleWirelessDevices
    $settleUntil = (Get-Date).AddSeconds($SettleSeconds)
    $deadline = (Get-Date).AddSeconds([Math]::Max($SettleSeconds, 4))
    $tried = @{}
    $failed = @{}

    do {
        $online = @(Get-ConnectedDevices | Where-Object { $_.Status -eq "device" } | ForEach-Object { $_.Id })
        $services = @(Get-MdnsEndpoints)
        $missing = @($services | Where-Object {
            $online -notcontains $_.Endpoint -and
            $online -notcontains "$($_.Name)._adb-tls-connect._tcp" -and
            -not $tried.ContainsKey($_.Endpoint)
        })
        foreach ($svc in $missing) {
            $tried[$svc.Endpoint] = $true
            $out = "$(& $adb connect $svc.Endpoint 2>&1)"
            if ($out -match "connected to") {
                Write-Host "  Connected $($svc.Endpoint)" -ForegroundColor Green
            } else {
                $failed[$svc.Endpoint] = $out
            }
        }
        $settled = ((Get-Date) -ge $settleUntil) -and ($services.Count -gt 0) -and ($missing.Count -eq 0)
        if (-not $settled) { Start-Sleep -Milliseconds 500 }
    } while (-not $settled -and (Get-Date) -lt $deadline)

    foreach ($ep in $failed.Keys) {
        Write-Host "  Could not connect $ep : $($failed[$ep])" -ForegroundColor Yellow
        Write-Host "  If this phone was never paired with this PC, use Option 11 once." -ForegroundColor DarkGray
    }
}

function Connect-AllMdnsDevices {
    Sync-WirelessDevices -SettleSeconds 3
    $devs = Show-ConnectedDevices
    if ($devs.Count -eq 0) {
        Write-Host "No mDNS wireless devices found automatically." -ForegroundColor Yellow
        Write-Host "Tips:" -ForegroundColor DarkGray
        Write-Host "  1. Turn Wireless Debugging ON in phone Developer Options." -ForegroundColor DarkGray
        Write-Host "  2. Ensure your PC and phone are on the exact same Wi-Fi network." -ForegroundColor DarkGray
        Write-Host "  3. Pair a new phone once using Option 11." -ForegroundColor DarkGray
        Write-Host "  4. You can also connect manually using Option 2." -ForegroundColor DarkGray
    }
}

# Pairing is needed once per phone and PC; after that, scans reconnect it on any port.
function Pair-NewDevice {
    Write-Host "On the phone: Wireless debugging > Pair device with pairing code." -ForegroundColor Cyan
    $ipPort = Read-Host "Enter the IP and pairing port shown there (e.g. 192.168.1.3:37099)"
    if (-not $ipPort) { return }
    $code = Read-Host "Enter the 6-digit pairing code"
    & $adb pair $ipPort.Trim() $code.Trim()
    Sync-WirelessDevices -SettleSeconds 3
    Show-ConnectedDevices | Out-Null
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

# Asks Debug or Release and points $apkPath at that variant; returns $false if release cannot be signed.
function Select-BuildVariant {
    $answer = Read-Host "Install which build? [D]ebug / [r]elease (Press Enter for Debug)"
    if ($answer -match "^[rR]") {
        if (-not (Test-Path "$repoRoot\keystore.properties")) {
            Write-Host "keystore.properties is missing, so a release build would be unsigned and cannot install." -ForegroundColor Red
            Write-Host "Copy keystore.properties.example to keystore.properties and fill in your keystore details." -ForegroundColor Yellow
            return $false
        }
        $script:variant = "release"
    } else {
        $script:variant = "debug"
    }
    $script:apkPath = "$repoRoot\app\build\outputs\apk\$variant\app-$variant.apk"
    Write-Host "Using the $variant build." -ForegroundColor Cyan
    return $true
}

# Sets the ABIs to package from the target phones; any unknown ABI falls back to a universal build.
function Set-BuildAbis {
    param ([object[]]$Devices)
    $abis = @($Devices | ForEach-Object { $_.Abi })
    if ($abis -contains "") { $abis = @() }
    $script:buildAbis = @($abis | Select-Object -Unique)
}

# Lists the ABIs inside the APK, so a phone-targeted build is not mistaken for a universal one.
function Get-ApkAbis {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        return @($zip.Entries | ForEach-Object { if ($_.FullName -match "^lib/([^/]+)/") { $matches[1] } } | Sort-Object -Unique)
    } finally {
        $zip.Dispose()
    }
}

function Show-BuildIdentity {
    if (-not (Test-Path $apkPath)) { return }
    Push-Location $repoRoot
    try {
        $commit = (& git rev-parse --short HEAD 2>$null)
        $branch = (& git rev-parse --abbrev-ref HEAD 2>$null)
        $dirty = (& git status --porcelain 2>$null)
    } finally {
        Pop-Location
    }
    $hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
    $built = (Get-Item -LiteralPath $apkPath).LastWriteTime
    $abis = @(Get-ApkAbis)
    $state = if ($dirty) { " (uncommitted changes present)" } else { "" }
    Write-Host "`n--- Build identity (record this on the test card) ---" -ForegroundColor Cyan
    Write-Host "  Package : $package"
    Write-Host "  Variant : $variant"
    Write-Host "  Branch  : $branch"
    Write-Host "  Commit  : $commit$state"
    Write-Host "  Built   : $built"
    Write-Host "  ABIs    : $($abis -join ', ')"
    Write-Host "  SHA-256 : $hash"
    Write-Host "------------------------------------------------------" -ForegroundColor Cyan
    # MapLibre ships four ABIs, so fewer means a test build for specific phones.
    if ($abis.Count -lt 4) {
        Write-Host "Test build for these phones only. To share an APK, run .\gradlew.bat :app:assemble$((Get-Culture).TextInfo.ToTitleCase($variant)) for all ABIs." -ForegroundColor Yellow
    }
}

function Ensure-ApkBuilt {
    param([switch]$Force)
    if ($Force -or (-not (Test-Path $apkPath))) {
        Write-Host "`nBuilding latest $variant APK with Gradle..." -ForegroundColor Cyan
        $gradleCmd = if ($env:OS -match "Windows") { ".\gradlew.bat" } else { "./gradlew" }
        Push-Location $repoRoot
        try {
            # Out-Host shows Gradle output without turning it into this function's return value.
            $task = if ($variant -eq "release") { "assembleRelease" } else { "assembleDebug" }
            $gradleArgs = @($task)
            if ($buildAbis.Count -gt 0) { $gradleArgs += "-PdevAbi=$($buildAbis -join ',')" }
            $buildTimer = [System.Diagnostics.Stopwatch]::StartNew()
            & $gradleCmd @gradleArgs | Out-Host
            if ($LASTEXITCODE -ne 0) {
                Write-Host "Gradle build failed with exit code $LASTEXITCODE." -ForegroundColor Red
                return $false
            }
            Write-Host ("Build took {0:N1} s." -f $buildTimer.Elapsed.TotalSeconds) -ForegroundColor DarkGray
        } finally {
            Pop-Location
        }
    }
    if (-not (Test-Path $apkPath)) { return $false }
    Show-BuildIdentity
    return $true
}

# Asks whether to rebuild; Enter means yes so a stale APK is not installed by accident.
function Confirm-ApkBuild {
    $buildChoice = Read-Host "Build latest $variant APK before installing? [Y/n] (Press Enter for Yes)"
    $force = ($buildChoice -eq "" -or $buildChoice -match "^[yY]")
    if (-not $force -and (Test-Path $apkPath)) {
        Write-Host "Using the existing APK; it may be older than your current code." -ForegroundColor Yellow
    }
    $built = Ensure-ApkBuilt -Force:$force
    if (-not $built) { Write-Host "Aborting installation due to build failure." -ForegroundColor Red }
    return $built
}

# Wakes the screen, starts the launcher activity and reports whether Android accepted it.
# Uses am start instead of monkey, because monkey switches auto-rotate back on.
function Start-App {
    param ([string]$DeviceId)
    & $adb -s $DeviceId shell input keyevent 224 2>$null | Out-Null
    $component = (& $adb -s $DeviceId shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $package 2>$null |
        Where-Object { $_ -match "/" } | Select-Object -Last 1)
    if ($component) {
        $out = (& $adb -s $DeviceId shell am start -n $component.Trim() 2>&1) -join "`n"
    } else {
        $out = (& $adb -s $DeviceId shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $package 2>&1) -join "`n"
    }
    return ($out -match "Starting: Intent" -and $out -notmatch "Error")
}

# Runs one adb install per phone at once and returns each phone's output by device ID.
# Prompts cannot share the console with parallel installs, so callers handle conflicts afterwards.
function Install-ApkInParallel {
    param ([object[]]$Devices)
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $tempDir = New-Item -ItemType Directory -Path (Join-Path $env:TEMP "resqmesh-install-$PID")
    $pending = @()
    for ($i = 0; $i -lt $Devices.Count; $i++) {
        $outFile = Join-Path $tempDir "$i.out"
        $errFile = Join-Path $tempDir "$i.err"
        $proc = Start-Process -FilePath $adb -ArgumentList @("-s", "`"$($Devices[$i].Id)`"", "install", "-r", "`"$apkPath`"") `
            -NoNewWindow -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        $pending += [PSCustomObject]@{ Device = $Devices[$i]; Process = $proc; OutFile = $outFile; ErrFile = $errFile }
    }

    $results = @{}
    while ($pending.Count -gt 0) {
        foreach ($job in @($pending | Where-Object { $_.Process.HasExited })) {
            $text = ((Get-Content -LiteralPath $job.OutFile, $job.ErrFile -ErrorAction SilentlyContinue) -join " ").Trim()
            $results[$job.Device.Id] = $text
            $outcome = if ($text -match "\bSuccess\b") { "finished" } else { "needs attention" }
            Write-Host ("  -> {0} ({1}): {2} after {3:N1} s" -f $job.Device.Model, $job.Device.Id, $outcome, $timer.Elapsed.TotalSeconds) -ForegroundColor Cyan
        }
        $pending = @($pending | Where-Object { -not $_.Process.HasExited })
        if ($pending.Count -gt 0) { Start-Sleep -Milliseconds 200 }
    }
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    return $results
}

function Install-App {
    param (
        [string]$DeviceId,
        # Output of an install that already ran; empty means install here.
        [string]$PriorInstallOutput = ""
    )

    if (-not (Test-Path $apkPath)) {
        Write-Host "APK not found at $apkPath." -ForegroundColor Red
        return $false
    }

    $model = (& $adb -s $DeviceId shell getprop ro.product.model 2>$null)
    $modelStr = if ($model) { $model.Trim() } else { "Android Device" }

    $installOut = $PriorInstallOutput
    if (-not $installOut) {
        Write-Host "  -> Installing to $DeviceId ($modelStr)..." -ForegroundColor Cyan
        $installTimer = [System.Diagnostics.Stopwatch]::StartNew()
        $installOut = & $adb -s $DeviceId install -r $apkPath 2>&1
        Write-Host ("     Install step took {0:N1} s." -f $installTimer.Elapsed.TotalSeconds) -ForegroundColor DarkGray
    }
    if ("$installOut" -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        # The other variant is signed with a different key; replacing it means uninstalling, which wipes app data.
        Write-Host "     $modelStr has a build signed with another key (debug vs release)." -ForegroundColor Yellow
        $confirm = Read-Host "     Type YES to uninstall it (erases node ID, messages and keys) and install the $variant build"
        if ($confirm -cne "YES") {
            Write-Host "     Skipped $modelStr." -ForegroundColor DarkGray
            return $false
        }
        & $adb -s $DeviceId uninstall $package 2>&1 | Out-Null
        $installOut = & $adb -s $DeviceId install -r $apkPath 2>&1
    }
    if ("$installOut" -notmatch "\bSuccess\b") {
        Write-Host "     Install failed on ${modelStr}: $installOut" -ForegroundColor Red
        return $false
    }
    Write-Host "     Install succeeded." -ForegroundColor Green

    Write-Host "  -> Waking screen & launching ResQMesh..." -ForegroundColor Cyan
    if (Start-App -DeviceId $DeviceId) {
        Write-Host "     Launched on $modelStr." -ForegroundColor Green
    } else {
        Write-Host "     Installed, but launch was not confirmed on $modelStr; open the app by hand." -ForegroundColor Yellow
    }
    return $true
}

function Install-AllDevices {
    Write-Host "`n=== Install & Launch on ALL Devices ===" -ForegroundColor Green

    if (-not (Select-BuildVariant)) { return }

    Sync-WirelessDevices

    $devs = @(Get-DeduplicatedActiveDevices)
    if ($devs.Count -eq 0) {
        Write-Host "No active devices available to install." -ForegroundColor Red
        return
    }

    Write-Host "`nTargeting $($devs.Count) distinct active device(s):" -ForegroundColor Cyan
    for ($i = 0; $i -lt $devs.Count; $i++) {
        $mName = if ($devs[$i].Model) { $devs[$i].Model } else { "Unknown" }
        Write-Host "  [$($i+1)] $($devs[$i].Id) ($mName)" -ForegroundColor DarkCyan
    }

    Set-BuildAbis -Devices $devs
    if (-not (Confirm-ApkBuild)) { return }

    Write-Host "`nInstalling on $($devs.Count) device(s) at once..." -ForegroundColor Cyan
    $installTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $installResults = Install-ApkInParallel -Devices $devs
    Write-Host ("All installs finished in {0:N1} s." -f $installTimer.Elapsed.TotalSeconds) -ForegroundColor DarkGray

    $successCount = 0
    for ($i = 0; $i -lt $devs.Count; $i++) {
        $targetId = [string]$devs[$i].Id
        Write-Host "`n[$($i + 1)/$($devs.Count)] Device: $targetId" -ForegroundColor Yellow
        $ok = Install-App -DeviceId $targetId -PriorInstallOutput $installResults[$targetId]
        if ($ok) { $successCount++ }
    }

    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "Deployment Complete: $successCount of $($devs.Count) device(s) updated and launched." -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
}

function Install-SpecificDevice {
    Sync-WirelessDevices
    $devs = @(Show-ConnectedDevices)
    $active = @($devs | Where-Object { $_.Status -eq "device" })
    if ($active.Count -eq 0) {
        Write-Host "No active devices available." -ForegroundColor Red
        return
    }

    Write-Host "Ready devices:" -ForegroundColor Cyan
    for ($i = 0; $i -lt $active.Count; $i++) { Write-Host "  [$($i + 1)] $($active[$i].Id)" }
    $selection = Read-Host "Enter the device number to install to (1-$($active.Count))"
    if ($selection -match "^\d+$") {
        $idx = [int]$selection - 1
        if ($idx -ge 0 -and $idx -lt $active.Count) {
            if (Select-BuildVariant) {
                Set-BuildAbis -Devices @(Get-DeviceInfo -DeviceId ([string]$active[$idx].Id))
                if (Confirm-ApkBuild) {
                    Install-App -DeviceId ([string]$active[$idx].Id) | Out-Null
                    Write-Host "`nDone!" -ForegroundColor Green
                }
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
        if (-not (Start-App -DeviceId $targetId)) {
            Write-Host "  Launch not confirmed on $mStr; is the app installed?" -ForegroundColor Yellow
        }
    }
    Write-Host "`nLaunch requests sent." -ForegroundColor Green
}

# Runs one adb package action on every distinct phone after a typed confirmation.
function Invoke-AllDevicesPackageAction {
    param (
        [string]$Title,
        [string]$Warning,
        [scriptblock]$Action
    )
    Write-Host "`n=== $Title on ALL Devices ===" -ForegroundColor Yellow
    Sync-WirelessDevices
    $devs = @(Get-DeduplicatedActiveDevices)
    if ($devs.Count -eq 0) {
        Write-Host "No active devices connected." -ForegroundColor Red
        return
    }

    Write-Host "Targeting $($devs.Count) distinct active device(s):" -ForegroundColor Cyan
    foreach ($d in $devs) {
        $m = (& $adb -s $d.Id shell getprop ro.product.model 2>$null)
        $mName = if ($m) { $m.Trim() } else { "Unknown" }
        Write-Host "  $($d.Id) ($mName)" -ForegroundColor DarkCyan
    }
    Write-Host $Warning -ForegroundColor Yellow
    $confirm = Read-Host "Type YES to continue"
    if ($confirm -cne "YES") {
        Write-Host "Cancelled." -ForegroundColor DarkGray
        return
    }

    $okCount = 0
    foreach ($d in $devs) {
        $targetId = [string]$d.Id
        $out = (& $Action $targetId) -join " "
        if ($out -match "Success") {
            Write-Host "  $targetId : done" -ForegroundColor Green
            $okCount++
        } else {
            Write-Host "  $targetId : $out" -ForegroundColor Red
        }
    }
    Write-Host "`n$Title finished on $okCount of $($devs.Count) device(s)." -ForegroundColor Green
}

function Clear-AllDevicesData {
    Invoke-AllDevicesPackageAction -Title "Clear $package data" `
        -Warning "This erases node ID, messages and keys; the app stays installed." `
        -Action { param($id) & $adb -s $id shell pm clear $package 2>&1 }
}

function Uninstall-AllDevices {
    Invoke-AllDevicesPackageAction -Title "Uninstall $package" `
        -Warning "This removes the app (debug or release) and all its data." `
        -Action { param($id) & $adb -s $id uninstall $package 2>&1 }
}

# --- Interactive Main Loop ---
Sync-WirelessDevices -SettleSeconds 3
Show-ConnectedDevices | Out-Null

do {
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "         ResQMesh Device Manager" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host " [1] Auto-connect all Wi-Fi devices (mDNS)"
    Write-Host " [2] Connect device manually (IP:Port)"
    Write-Host " [3] Disconnect / Remove all devices"
    Write-Host " [4] Disconnect / Remove a specific device"
    Write-Host " [5] Install APK (debug/release) & Launch on ALL devices"
    Write-Host " [6] Install APK (debug/release) & Launch on a SPECIFIC device"
    Write-Host " [7] Launch app on ALL devices (no reinstall)"
    Write-Host " [8] Show connected devices status"
    Write-Host " [9] Clear app data on ALL devices"
    Write-Host " [10] Uninstall app (and data) from ALL devices"
    Write-Host " [11] Pair a new phone (once per phone)"
    Write-Host " [0] Exit"
    Write-Host "============================================" -ForegroundColor Green

    $choice = Read-Host "Select an option (0-11)"

    switch ($choice) {
        "1" { Connect-AllMdnsDevices }
        "2" { Connect-ManualDevice }
        "3" { Disconnect-AllDevices }
        "4" { Disconnect-SpecificDevice }
        "5" { Install-AllDevices }
        "6" { Install-SpecificDevice }
        "7" { Launch-AllDevices }
        "8" { Sync-WirelessDevices; Show-ConnectedDevices | Out-Null }
        "9" { Clear-AllDevicesData }
        "10" { Uninstall-AllDevices }
        "11" { Pair-NewDevice }
        "0" { Write-Host "`nGoodbye!" -ForegroundColor Cyan; break }
        default { Write-Host "Invalid option. Please choose between 0 and 11." -ForegroundColor Yellow }
    }
} while ($choice -ne "0")
