<#
.SYNOPSIS
    Installs a verified Cebu offline map package to a connected Android device via ADB for local development testing.
.DESCRIPTION
    Zero-network developer installation helper:
    1. Extracts package zip on the host machine (avoids relying on missing device-side unzip binaries).
    2. Pushes extracted package tree and manifest to device staging in /data/local/tmp/.
    3. Copies assets into the app sandbox via run-as com.example.testresqmesh.
    4. Writes active_package.json pointer file for immediate offline map activation.
    5. Enables testing SosMapScreen and OfflineMapSettingsScreen in Airplane Mode with zero network calls.
.PARAMETER PackageZip
    Path to cebu-vN.zip.
.PARAMETER ManifestJson
    Path to cebu-vN.manifest.json.
.PARAMETER PackageVersion
    Package version integer (default: 1).
#>
param(
    [Parameter(Mandatory=$true)]
    [string]$PackageZip,

    [Parameter(Mandatory=$true)]
    [string]$ManifestJson,

    [int]$PackageVersion = 1,

    [string]$DeviceId = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=== ResQMesh Local Map Developer Installer (Zero-Network) ===" -ForegroundColor Cyan

if (-not (Test-Path $PackageZip)) {
    throw "Package zip not found: $PackageZip"
}
if (-not (Test-Path $ManifestJson)) {
    throw "Manifest JSON not found: $ManifestJson"
}

$adbExe = Get-Command adb -ErrorAction SilentlyContinue
$adbCmd = if ($adbExe) {
    $adbExe.Source
} elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") {
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
} else {
    throw "adb command not found. Please ensure Android Platform Tools are in PATH or installed at $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe."
}

# 1. Check connected devices
$rawLines = & $adbCmd devices
$deviceLines = @($rawLines | Where-Object { $_ -match "\s+device$" } | ForEach-Object { ($_ -split "\s+")[0] })
if ($deviceLines.Count -eq 0) {
    throw "No active Android devices or emulators found via ADB."
}

$targetDevice = $DeviceId
if ([string]::IsNullOrWhiteSpace($targetDevice)) {
    $targetDevice = $deviceLines[0]
    if ($deviceLines.Count -gt 1) {
        Write-Host "Multiple devices detected ($($deviceLines -join ', ')). Targeting device: $targetDevice (use -DeviceId to select another)." -ForegroundColor Yellow
    }
} else {
    if ($deviceLines -notcontains $targetDevice) {
        throw "Specified DeviceId '$targetDevice' is not in active devices list: $($deviceLines -join ', ')"
    }
}

Write-Host "Target Device: $targetDevice" -ForegroundColor Green

function Invoke-AdbCommand {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$cmdArgs)
    & $adbCmd -s $targetDevice @cmdArgs
}

$pkgName = "com.example.testresqmesh"
Add-Type -AssemblyName System.IO.Compression.FileSystem

# 2. Extract package on host machine to avoid device-side unzip dependencies
$guid = [guid]::NewGuid().ToString("N")
$tempExtractDir = Join-Path $env:TEMP "resqmesh_map_$guid"
Write-Host "[1/4] Extracting package on host machine..." -ForegroundColor Green
[System.IO.Compression.ZipFile]::ExtractToDirectory($PackageZip, $tempExtractDir)

try {
    # 3. Prepare staging on device
    Write-Host "[2/4] Preparing device staging in /data/local/tmp/..." -ForegroundColor Green
    Invoke-AdbCommand shell "rm -rf /data/local/tmp/resqmesh_pkg /data/local/tmp/active_package.json && mkdir -p /data/local/tmp/resqmesh_pkg"

    Write-Host "      Pushing map assets to device..." -ForegroundColor Green
    Invoke-AdbCommand push "$tempExtractDir/." "/data/local/tmp/resqmesh_pkg/"
    Invoke-AdbCommand push $ManifestJson "/data/local/tmp/active_package.json"

    # Ensure files are readable by the app's UID
    Invoke-AdbCommand shell "chmod -R 777 /data/local/tmp/resqmesh_pkg /data/local/tmp/active_package.json"

    # 4. Copy into app storage sandbox via run-as
    Write-Host "[3/4] Installing into app sandbox via run-as..." -ForegroundColor Green
    $installScript = @"
mkdir -p files/offline_maps/packages/cebu-v$PackageVersion
rm -rf files/offline_maps/packages/cebu-v$PackageVersion/*
cp -r /data/local/tmp/resqmesh_pkg/* files/offline_maps/packages/cebu-v$PackageVersion/
rm -f files/offline_maps/active_package.json*
cp /data/local/tmp/active_package.json files/offline_maps/active_package.json
"@
    $unixScript = $installScript.Replace("`r`n", "`n").Replace("`r", "`n")

    $tempSh = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tempSh, $unixScript, (New-Object System.Text.UTF8Encoding($false)))
    Invoke-AdbCommand push $tempSh "/data/local/tmp/install_map.sh"
    Remove-Item $tempSh -Force
    Invoke-AdbCommand shell "chmod 777 /data/local/tmp/install_map.sh"

    Invoke-AdbCommand shell "run-as $pkgName sh /data/local/tmp/install_map.sh"
    Invoke-AdbCommand shell "rm -rf /data/local/tmp/resqmesh_pkg /data/local/tmp/active_package.json /data/local/tmp/install_map.sh"

    Write-Host "[4/4] Package installed successfully!" -ForegroundColor Green
    Write-Host "Verification Steps:" -ForegroundColor White
    Write-Host "1. Put phone in Airplane Mode (no Wi-Fi, no mobile data)."
    Write-Host "2. Open ResQMesh -> Settings -> Offline maps. Verify status is ACTIVE & VERIFIED."
    Write-Host "3. View an SOS alert with coordinates to verify MapLibre vector rendering."
    Write-Host "=============================================================" -ForegroundColor Cyan
} finally {
    if (Test-Path $tempExtractDir) {
        Remove-Item -Path $tempExtractDir -Recurse -Force
    }
}
