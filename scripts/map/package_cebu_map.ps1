<#
.SYNOPSIS
    Packages and signs a Cebu offline vector map release artifact for ResQMesh.
.DESCRIPTION
    1. Validates that cebu.pmtiles and style.json exist and contain no external network URLs.
    2. Inspects style.json and verifies every referenced sprite, glyph font, and vector asset exists locally.
    3. Recursively compresses all package assets into cebu-vN.zip preserving directory structure.
    4. Computes exact byte size and SHA-256 hash.
    5. Generates cebu-vN.manifest.json targeting repository jangkayl/ResQMesh.
    6. Signs the manifest using Ed25519 to produce cebu-vN.manifest.sig.
.PARAMETER AssetDir
    Directory containing cebu.pmtiles, style.json, sprites/, glyphs/, and LICENSE.txt.
.PARAMETER OutputDir
    Directory where the packaged zip, manifest, and signature will be written.
.PARAMETER PrivateKeyPath
    Path to the maintainer Ed25519 private key (.pem or raw binary).
.PARAMETER Version
    Map package version integer (default: 1).
#>
param(
    [Parameter(Mandatory=$true)]
    [string]$AssetDir,

    [Parameter(Mandatory=$true)]
    [string]$OutputDir,

    [Parameter(Mandatory=$false)]
    [string]$PrivateKeyPath,

    [int]$Version = 1,

    [switch]$ValidateOnly,

    [double]$MinLat = 9.40,
    [double]$MinLng = 123.30,
    [double]$MaxLat = 11.40,
    [double]$MaxLng = 124.20,
    [int]$MinZoom = 6,
    [int]$MaxZoom = 15
)

$ErrorActionPreference = "Stop"

Write-Host "=== ResQMesh Cebu Offline Map Packaging & Signing ===" -ForegroundColor Cyan

# 1. Validate Core Input Files
$pmtilesPath = Join-Path $AssetDir "cebu.pmtiles"
$stylePath = Join-Path $AssetDir "style.json"

if (-not (Test-Path $pmtilesPath)) {
    throw "Missing required asset: $pmtilesPath"
}
if (-not (Test-Path $stylePath)) {
    throw "Missing required asset: $stylePath"
}
if (-not $ValidateOnly) {
    if ([string]::IsNullOrWhiteSpace($PrivateKeyPath) -or -not (Test-Path $PrivateKeyPath)) {
        throw "Private key not found at: '$PrivateKeyPath'. Please provide a valid -PrivateKeyPath, or pass -ValidateOnly to validate and package without signing."
    }
}

# 2. Verify style.json contains zero network requests
$styleContent = Get-Content -Path $stylePath -Raw
if ($styleContent -match "https?://") {
    throw "Policy Violation: style.json contains remote network URLs (http:// or https://). Offline map styles must be fully self-contained!"
}

# 3. Parse style.json and verify all referenced local dependencies exist
Write-Host "[1/5] Verifying style dependencies..." -ForegroundColor Green
$styleJson = $styleContent | ConvertFrom-Json

# Check Vector Sources
if ($styleJson.sources) {
    foreach ($sourceProp in $styleJson.sources.PSObject.Properties) {
        $src = $sourceProp.Value
        if ($src.url) {
            $rawUrl = [string]$src.url
            if ($rawUrl -match "\.pmtiles") {
                $cleanPmtiles = $rawUrl -replace '^pmtiles://', '' -replace '^file://', '' -replace '^\./', '' -replace '^\{PACKAGE_DIR\}/', ''
                $srcPmtilesPath = Join-Path $AssetDir $cleanPmtiles
                if (-not (Test-Path $srcPmtilesPath)) {
                    throw "Referenced PMTiles file missing in source '$($sourceProp.Name)': $srcPmtilesPath"
                }
                Write-Host "      Verified vector source: $cleanPmtiles" -ForegroundColor DarkGreen
            }
        }
    }
}

# Check Sprites
if ($styleJson.sprite) {
    $spriteBase = [string]$styleJson.sprite -replace '^file://', '' -replace '^\./', '' -replace '^\{PACKAGE_DIR\}/', ''
    $spriteJsonFile = Join-Path $AssetDir "$spriteBase.json"
    $spritePngFile = Join-Path $AssetDir "$spriteBase.png"

    if (-not (Test-Path $spriteJsonFile)) {
        throw "Missing referenced sprite JSON: $spriteJsonFile"
    }
    if (-not (Test-Path $spritePngFile)) {
        throw "Missing referenced sprite PNG: $spritePngFile"
    }
    Write-Host "      Verified sprite assets: $spriteBase (.json + .png)" -ForegroundColor DarkGreen
}

# Check Glyphs
if ($styleJson.glyphs) {
    $glyphsTemplate = [string]$styleJson.glyphs
    $glyphsBase = ($glyphsTemplate -split '\{')[0].TrimEnd('/') -replace '^file://', '' -replace '^\./', '' -replace '^\{PACKAGE_DIR\}/', ''
    if ([string]::IsNullOrWhiteSpace($glyphsBase)) {
        $glyphsBase = "glyphs"
    }

    $glyphsDirPath = Join-Path $AssetDir $glyphsBase
    if (-not (Test-Path $glyphsDirPath)) {
        throw "Missing referenced glyphs directory: $glyphsDirPath"
    }

    $pbfFiles = Get-ChildItem -Path $glyphsDirPath -Filter "*.pbf" -Recurse
    if ($pbfFiles.Count -eq 0) {
        throw "Referenced glyphs directory contains no .pbf font files: $glyphsDirPath"
    }
    Write-Host "      Verified glyph font assets: $glyphsBase ($($pbfFiles.Count) .pbf files)" -ForegroundColor DarkGreen
}

# Check Attribution / License
$licensePath = Join-Path $AssetDir "LICENSE.txt"
if (-not (Test-Path $licensePath)) {
    $licensePath = Join-Path $AssetDir "LICENSE"
}
if (Test-Path $licensePath) {
    Write-Host "      Verified license/attribution asset: $(Split-Path $licensePath -Leaf)" -ForegroundColor DarkGreen
} else {
    Write-Host "      Note: No LICENSE.txt file in asset directory; attribution will be carried via manifest." -ForegroundColor Yellow
}

Write-Host "[2/5] Verified local assets and zero-network style integrity." -ForegroundColor Green

# 4. Create Output Directory and Package Zip
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$zipName = "cebu-v$Version.zip"
$zipPath = Join-Path $OutputDir $zipName

if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Write-Host "[3/5] Recursively compressing vector package to $zipName..." -ForegroundColor Green
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$resolvedAssetDir = (Resolve-Path $AssetDir).Path.TrimEnd('\') + '\'
$zipArchive = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -Path $AssetDir -Recurse -File | ForEach-Object {
        $fullPath = $_.FullName
        $relPath = $fullPath.Substring($resolvedAssetDir.Length).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $fullPath, $relPath, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zipArchive.Dispose()
}

# 5. Compute exact byte size and SHA-256 hash
$zipFileItem = Get-Item $zipPath
$byteLength = $zipFileItem.Length
$sha256Hex = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash.ToLower()

Write-Host "      Package size: $byteLength bytes (~$([math]::Round($byteLength / 1MB, 2)) MB)" -ForegroundColor Green
Write-Host "      SHA-256:      $sha256Hex" -ForegroundColor Green

# 6. Generate Manifest JSON
$manifestName = "cebu-v$Version.manifest.json"
$manifestPath = Join-Path $OutputDir $manifestName

$releaseDate = (Get-Date).ToString("yyyy-MM-dd")
$assetUrl = "https://github.com/jangkayl/ResQMesh/releases/download/map-cebu-v$Version/$zipName"

# Auto-detect exact bounds and zoom ranges from cebu.pmtiles using pmtiles CLI if available
$pmtilesCmd = $null
$pmtilesExe = Get-Command pmtiles -ErrorAction SilentlyContinue
if ($pmtilesExe) {
    $pmtilesCmd = $pmtilesExe.Source
} elseif (Test-Path "C:\Users\Kyle\AppData\Local\Temp\go-pmtiles-bin\pmtiles.exe") {
    $pmtilesCmd = "C:\Users\Kyle\AppData\Local\Temp\go-pmtiles-bin\pmtiles.exe"
}

if ($pmtilesCmd) {
    try {
        $showOutput = (& $pmtilesCmd show $pmtilesPath) -join "`n"
        if ($showOutput -match "bounds:\s*\(long:\s*([0-9.-]+),\s*lat:\s*([0-9.-]+)\)\s*\(long:\s*([0-9.-]+),\s*lat:\s*([0-9.-]+)\)") {
            $MinLng = [math]::Round([double]$matches[1], 4)
            $MinLat = [math]::Round([double]$matches[2], 4)
            $MaxLng = [math]::Round([double]$matches[3], 4)
            $MaxLat = [math]::Round([double]$matches[4], 4)
        }
        if ($showOutput -match "min zoom:\s*([0-9]+)") { $MinZoom = [int]$matches[1] }
        if ($showOutput -match "max zoom:\s*([0-9]+)") { $MaxZoom = [int]$matches[1] }
        Write-Host "      Detected PMTiles coverage: Bounds ($MinLat to $MaxLat, $MinLng to $MaxLng), Zooms $MinZoom-$MaxZoom" -ForegroundColor Green
    } catch {
        Write-Host "      (pmtiles show auto-detection failed, using specified defaults: Bounds ($MinLat to $MaxLat, $MinLng to $MaxLng), Zooms $MinZoom-$MaxZoom)" -ForegroundColor DarkGray
    }
} else {
    Write-Host "      (pmtiles CLI not found, using specified defaults: Bounds ($MinLat to $MaxLat, $MinLng to $MaxLng), Zooms $MinZoom-$MaxZoom)" -ForegroundColor DarkGray
}

$manifestObj = [ordered]@{
    packageId   = "cebu-offline"
    version     = $Version
    minLat      = $MinLat
    minLng      = $MinLng
    maxLat      = $MaxLat
    maxLng      = $MaxLng
    minZoom     = $MinZoom
    maxZoom     = $MaxZoom
    byteLength  = $byteLength
    sha256      = $sha256Hex
    releaseDate = $releaseDate
    attribution = "$([char]0x00A9) OpenStreetMap contributors, ODbL 1.0"
    assetUrl    = $assetUrl
}

$manifestJson = $manifestObj | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText($manifestPath, $manifestJson, [System.Text.Encoding]::UTF8)
Write-Host "[4/5] Generated manifest: $manifestName (Bounds: $MinLat-$MaxLat, $MinLng-$MaxLng, Zooms: $MinZoom-$MaxZoom)" -ForegroundColor Green

# 7. Sign Manifest with Ed25519 (or stop before signing if -ValidateOnly)
if ($ValidateOnly) {
    Write-Host "[5/5] Skipping manifest signing (-ValidateOnly mode)..." -ForegroundColor Yellow
    Write-Host "      Private signing key was not accessed, printed, or modified." -ForegroundColor Green
    Write-Host ""
    Write-Host "=== Validation & Packaging Successful (Unsigned) ===" -ForegroundColor Cyan
    Write-Host "1. $zipName (Package size: $byteLength bytes, SHA-256: $sha256Hex)"
    Write-Host "2. $manifestName (Bounds: $MinLat-$MaxLat, $MinLng-$MaxLng, Zooms: $MinZoom-$MaxZoom)"
    Write-Host ""
    Write-Host "To sign this manifest for release, run:" -ForegroundColor White
    Write-Host ".\scripts\map\package_cebu_map.ps1 -AssetDir `"$AssetDir`" -OutputDir `"$OutputDir`" -PrivateKeyPath `"<path_to_private_key>`" -Version $Version" -ForegroundColor Yellow
    return
}

$sigName = "cebu-v$Version.manifest.sig"
$sigPath = Join-Path $OutputDir $sigName

Write-Host "[5/5] Signing manifest with Ed25519..." -ForegroundColor Green
$hasOpenssl = (Get-Command openssl -ErrorAction SilentlyContinue) -ne $null

if ($hasOpenssl) {
    openssl pkeyutl -sign -rawin -inkey $PrivateKeyPath -in $manifestPath -out $sigPath
} else {
    # Java Ed25519 fallback
    $javaSigner = @"
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

public class Signer {
    public static void main(String[] args) throws Exception {
        byte[] keyBytes = Files.readAllBytes(Paths.get(args[0]));
        byte[] dataBytes = Files.readAllBytes(Paths.get(args[1]));
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(priv);
        sig.update(dataBytes);
        byte[] signature = sig.sign();
        Files.write(Paths.get(args[2]), signature);
    }
}
"@
    $tempJava = Join-Path $env:TEMP "Signer.java"
    Set-Content -Path $tempJava -Value $javaSigner
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($javac -and $java) {
        & javac $tempJava
        $classDir = [System.IO.Path]::GetDirectoryName($tempJava)
        & java -cp $classDir Signer $PrivateKeyPath $manifestPath $sigPath
    } else {
        throw "Could not find openssl or javac/java to compute Ed25519 signature."
    }
}

Write-Host "Generated signature: $sigName" -ForegroundColor Green
Write-Host ""
Write-Host "=== Release Artifacts Ready in $OutputDir ===" -ForegroundColor Cyan
Write-Host "1. $zipName"
Write-Host "2. $manifestName"
Write-Host "3. $sigName"
Write-Host "Target GitHub Release Tag: map-cebu-v$Version" -ForegroundColor Yellow
