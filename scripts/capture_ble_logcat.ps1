param(
    [string]$AdbPath = 'C:\Users\Kyle\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$OutputDir = (Join-Path $PSScriptRoot '..\captures\ble-logcat'),
    [ValidateRange(1, 60)][int]$PollSeconds = 3,
    [ValidateRange(0, 1440)][int]$DurationMinutes = 0
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw "ADB not found at $AdbPath. Pass -AdbPath with your SDK platform-tools adb.exe path."
}

$sessionStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sessionDir = Join-Path $OutputDir $sessionStamp
New-Item -ItemType Directory -Path $sessionDir -Force | Out-Null
$captures = @{}
$segments = @{}
$startedAt = Get-Date

function Get-ConnectedSerials {
    $lines = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) { throw 'adb devices failed.' }
    @($lines | ForEach-Object {
        if ($_ -match '^([^\s]+)\s+device\s*$') { $Matches[1] }
    })
}

function Start-DeviceCapture([string]$serial) {
    $safeSerial = $serial -replace '[^A-Za-z0-9._-]', '_'
    $segment = if ($segments.ContainsKey($serial)) { $segments[$serial] + 1 } else { 1 }
    $segments[$serial] = $segment
    $prefix = Join-Path $sessionDir ("{0}-part{1:D2}" -f $safeSerial, $segment)
    $logPath = "$prefix.logcat.txt"
    $errorPath = "$prefix.adb-error.txt"
    $metadataPath = "$prefix.device.txt"

    $model = (& $AdbPath -s $serial shell getprop ro.product.model).Trim()
    $release = (& $AdbPath -s $serial shell getprop ro.build.version.release).Trim()
    $sdk = (& $AdbPath -s $serial shell getprop ro.build.version.sdk).Trim()
    @(
        "serial=$serial"
        "model=$model"
        "android_release=$release"
        "android_sdk=$sdk"
        "capture_started_local=$((Get-Date).ToString('o'))"
        'buffers=main,system,events'
        'format=threadtime'
        'initial_backlog=1000 lines'
    ) | Set-Content -LiteralPath $metadataPath -Encoding UTF8

    # Keep a short pre-capture backlog, then stream all new lines. Full buffers retain
    # Bluetooth stack and app context even when a failure has no BLE_MESH entry.
    $process = Start-Process -FilePath $AdbPath -ArgumentList @(
        '-s', $serial, 'logcat', '-v', 'threadtime', '-b', 'main', '-b', 'system', '-b', 'events', '-T', '1000'
    ) -PassThru -WindowStyle Hidden -RedirectStandardOutput $logPath -RedirectStandardError $errorPath
    $captures[$serial] = $process
    Write-Host "Recording $serial ($model, Android $release) -> $logPath"
}

Write-Host "BLE Logcat session: $sessionDir"
Write-Host 'Monitoring ADB devices. Press Ctrl+C to stop.'
try {
    while ($DurationMinutes -eq 0 -or (Get-Date) -lt $startedAt.AddMinutes($DurationMinutes)) {
        try {
            $connected = Get-ConnectedSerials
            foreach ($serial in $connected) {
                if (-not $captures.ContainsKey($serial) -or $captures[$serial].HasExited) {
                    Start-DeviceCapture $serial
                }
            }
            foreach ($serial in @($captures.Keys)) {
                if ($connected -notcontains $serial -or $captures[$serial].HasExited) {
                    if (-not $captures[$serial].HasExited) {
                        Stop-Process -Id $captures[$serial].Id -ErrorAction SilentlyContinue
                    }
                    $captures.Remove($serial)
                    Write-Host "Capture paused for $serial; monitoring for reconnection."
                }
            }
        } catch {
            Write-Warning "ADB polling failed: $($_.Exception.Message). Retrying."
        }
        Start-Sleep -Seconds $PollSeconds
    }
} finally {
    foreach ($process in $captures.Values) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
        }
    }
    Write-Host "Capture stopped. Files: $sessionDir"
}
