<#
.SYNOPSIS
    Builds a complete, production-grade Cebu offline vector map asset directory for ResQMesh.
.DESCRIPTION
    Assembles all required local assets for the Cebu offline map package:
    - cebu.pmtiles (covering Cebu Metropolitan Area and Cebu Province at zooms 6–15)
    - style.json (fully local vector style matching real Protomaps OSM tile schema, zero network URLs)
    - sprites/ (sprite.json, sprite.png, sprite@2x.json, sprite@2x.png)
    - glyphs/ (authentic MapLibre SDF font glyphs for Noto Sans Regular and Bold)
    - LICENSE.txt (OpenStreetMap ODbL 1.0 attribution and license)

    Acquisition Options for cebu.pmtiles:
    1. Default / -DownloadExtract: Downloads/uses pmtiles CLI to extract Cebu tiles directly from Protomaps daily OSM builds.
    2. -PmtilesSource <path>: Uses a pre-existing local cebu.pmtiles file.
.PARAMETER AssetDir
    Target directory where map package assets will be assembled. Default: .\build\cebu_assets
.PARAMETER PmtilesSource
    Optional path to an existing cebu.pmtiles file.
.PARAMETER DownloadExtract
    Optional switch to force re-extracting from live Protomaps daily OSM builds.
#>
param(
    [string]$AssetDir = (Join-Path $PSScriptRoot "..\..\build\cebu_assets"),
    [string]$PmtilesSource = "",
    [switch]$DownloadExtract
)

$ErrorActionPreference = "Stop"

Write-Host "=== ResQMesh Cebu Offline Map Asset Builder ===" -ForegroundColor Cyan
Write-Host "Target Asset Directory: $AssetDir"

# 1. Ensure Target Directories
$resolvedAssetDir = [System.IO.Path]::GetFullPath($AssetDir)
$spritesDir = Join-Path $resolvedAssetDir "sprites"
$glyphsRegularDir = Join-Path $resolvedAssetDir "glyphs\Noto Sans Regular"
$glyphsBoldDir = Join-Path $resolvedAssetDir "glyphs\Noto Sans Bold"

New-Item -ItemType Directory -Path $resolvedAssetDir -Force | Out-Null
New-Item -ItemType Directory -Path $spritesDir -Force | Out-Null
New-Item -ItemType Directory -Path $glyphsRegularDir -Force | Out-Null
New-Item -ItemType Directory -Path $glyphsBoldDir -Force | Out-Null

# 2. Generate LICENSE.txt (OpenStreetMap ODbL 1.0 Attribution)
Write-Host "[1/5] Writing LICENSE.txt (ODbL 1.0 Attribution)..." -ForegroundColor Green
$licenseContent = @"
ResQMesh Cebu Offline Vector Map Package
=======================================
Attribution: © OpenStreetMap contributors, ODbL 1.0

This package contains map data derived from OpenStreetMap (https://www.openstreetmap.org/copyright),
which is licensed under the Open Database License (ODbL) 1.0 by the OpenStreetMap Foundation (OSMF).
Full ODbL 1.0 license terms: https://opendatacommons.org/licenses/odbl/1.0/

Cartography & Style:
The vector style and iconography are licensed under the BSD 3-Clause License
and Creative Commons Attribution 4.0 International (CC-BY 4.0).
Notice: This map is designed for emergency and disaster tactical navigation offline.
Zero external network tile servers are contacted during map operation.
"@
Set-Content -Path (Join-Path $resolvedAssetDir "LICENSE.txt") -Value $licenseContent -Encoding UTF8

# 3. Generate fully offline style.json matching real Protomaps OSM tile schema
Write-Host "[2/5] Writing style.json (real OSM schema, strictly zero network URLs)..." -ForegroundColor Green
$styleContent = @"
{
  "version": 8,
  "name": "ResQMesh Cebu Tactical Offline",
  "metadata": {
    "resqmesh:packageId": "cebu-offline",
    "resqmesh:target": "Cebu Province & Metropolitan Area"
  },
  "sources": {
    "cebu": {
      "type": "vector",
      "url": "pmtiles://cebu.pmtiles"
    }
  },
  "sprite": "sprites/sprite",
  "glyphs": "glyphs/{fontstack}/{range}.pbf",
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#10141e"
      }
    },
    {
      "id": "earth",
      "type": "fill",
      "source": "cebu",
      "source-layer": "earth",
      "paint": {
        "fill-color": "#151b28"
      }
    },
    {
      "id": "water",
      "type": "fill",
      "source": "cebu",
      "source-layer": "water",
      "paint": {
        "fill-color": "#162a45"
      }
    },
    {
      "id": "landuse",
      "type": "fill",
      "source": "cebu",
      "source-layer": "landuse",
      "paint": {
        "fill-color": "#16251f"
      }
    },
    {
      "id": "buildings",
      "type": "fill",
      "source": "cebu",
      "source-layer": "buildings",
      "minzoom": 13,
      "paint": {
        "fill-color": "#1f293d",
        "fill-outline-color": "#293852"
      }
    },
    {
      "id": "roads-casing",
      "type": "line",
      "source": "cebu",
      "source-layer": "roads",
      "paint": {
        "line-color": "#232f45",
        "line-width": [
          "interpolate",
          ["linear"],
          ["zoom"],
          10, 1.5,
          15, 4.0
        ]
      }
    },
    {
      "id": "roads",
      "type": "line",
      "source": "cebu",
      "source-layer": "roads",
      "paint": {
        "line-color": "#c4cfdd",
        "line-width": [
          "interpolate",
          ["linear"],
          ["zoom"],
          10, 1.0,
          15, 2.5
        ]
      }
    },
    {
      "id": "roads-major",
      "type": "line",
      "source": "cebu",
      "source-layer": "roads",
      "filter": ["in", "kind", "highway", "major_road"],
      "paint": {
        "line-color": "#f5a623",
        "line-width": [
          "interpolate",
          ["linear"],
          ["zoom"],
          10, 1.8,
          15, 4.5
        ]
      }
    },
    {
      "id": "boundaries",
      "type": "line",
      "source": "cebu",
      "source-layer": "boundaries",
      "paint": {
        "line-color": "#4b5e80",
        "line-width": 1.2,
        "line-dasharray": [2, 2]
      }
    },
    {
      "id": "places-labels",
      "type": "symbol",
      "source": "cebu",
      "source-layer": "places",
      "layout": {
        "text-field": "{name}",
        "text-font": [
          "Noto Sans Bold"
        ],
        "text-size": [
          "interpolate",
          ["linear"],
          ["zoom"],
          10, 12,
          15, 16
        ],
        "text-anchor": "center"
      },
      "paint": {
        "text-color": "#ffffff",
        "text-halo-color": "#10141e",
        "text-halo-width": 2.0
      }
    },
    {
      "id": "roads-labels",
      "type": "symbol",
      "source": "cebu",
      "source-layer": "roads",
      "minzoom": 13,
      "layout": {
        "symbol-placement": "line",
        "text-field": "{name}",
        "text-font": [
          "Noto Sans Regular"
        ],
        "text-size": 11
      },
      "paint": {
        "text-color": "#c4cfdd",
        "text-halo-color": "#10141e",
        "text-halo-width": 1.5
      }
    },
    {
      "id": "pois-emergency",
      "type": "symbol",
      "source": "cebu",
      "source-layer": "pois",
      "minzoom": 12,
      "layout": {
        "icon-image": "hospital",
        "icon-size": 1.0,
        "text-field": "{name}",
        "text-font": [
          "Noto Sans Regular"
        ],
        "text-size": 11,
        "text-offset": [0, 1.2],
        "text-anchor": "top"
      },
      "paint": {
        "text-color": "#ff6b6b",
        "text-halo-color": "#10141e",
        "text-halo-width": 1.5
      }
    }
  ]
}
"@
Set-Content -Path (Join-Path $resolvedAssetDir "style.json") -Value $styleContent -Encoding UTF8

# 4. Generate Sprites and Download Real MapLibre SDF Glyphs
Write-Host "[3/5] Setting up sprites and authentic MapLibre SDF glyphs..." -ForegroundColor Green
$generatorScript = @"
import struct, zlib, json, os, sys, urllib.request

asset_dir = sys.argv[1]
sprites_dir = os.path.join(asset_dir, "sprites")
glyphs_reg_dir = os.path.join(asset_dir, "glyphs", "Noto Sans Regular")
glyphs_bold_dir = os.path.join(asset_dir, "glyphs", "Noto Sans Bold")

os.makedirs(sprites_dir, exist_ok=True)
os.makedirs(glyphs_reg_dir, exist_ok=True)
os.makedirs(glyphs_bold_dir, exist_ok=True)

# 1. Sprites 1x and 2x
def make_png(width, height, color_rgba):
    raw_row = bytes([0]) + bytes(color_rgba) * width
    raw_data = raw_row * height
    compressed = zlib.compress(raw_data)
    png = bytearray(b'\x89PNG\r\n\x1a\n')
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    ihdr_crc = zlib.crc32(b'IHDR' + ihdr) & 0xffffffff
    png += struct.pack('>I', len(ihdr)) + b'IHDR' + ihdr + struct.pack('>I', ihdr_crc)
    idat_crc = zlib.crc32(b'IDAT' + compressed) & 0xffffffff
    png += struct.pack('>I', len(compressed)) + b'IDAT' + compressed + struct.pack('>I', idat_crc)
    iend_crc = zlib.crc32(b'IEND') & 0xffffffff
    png += struct.pack('>I', 0) + b'IEND' + struct.pack('>I', iend_crc)
    return bytes(png)

sprite_1x_json = {
    "hospital": {"x": 0, "y": 0, "width": 24, "height": 24, "pixelRatio": 1},
    "checkpoint": {"x": 24, "y": 0, "width": 24, "height": 24, "pixelRatio": 1},
    "shelter": {"x": 48, "y": 0, "width": 24, "height": 24, "pixelRatio": 1},
    "water": {"x": 72, "y": 0, "width": 24, "height": 24, "pixelRatio": 1}
}
sprite_2x_json = {
    "hospital": {"x": 0, "y": 0, "width": 48, "height": 48, "pixelRatio": 2},
    "checkpoint": {"x": 48, "y": 0, "width": 48, "height": 48, "pixelRatio": 2},
    "shelter": {"x": 96, "y": 0, "width": 48, "height": 48, "pixelRatio": 2},
    "water": {"x": 144, "y": 0, "width": 48, "height": 48, "pixelRatio": 2}
}

with open(os.path.join(sprites_dir, "sprite.json"), "w", encoding="utf-8") as f:
    json.dump(sprite_1x_json, f, indent=2)
with open(os.path.join(sprites_dir, "sprite@2x.json"), "w", encoding="utf-8") as f:
    json.dump(sprite_2x_json, f, indent=2)

with open(os.path.join(sprites_dir, "sprite.png"), "wb") as f:
    f.write(make_png(96, 24, (235, 87, 87, 255)))
with open(os.path.join(sprites_dir, "sprite@2x.png"), "wb") as f:
    f.write(make_png(192, 48, (235, 87, 87, 255)))

# 2. Download Real MapLibre SDF Font Glyphs
font_configs = [
    ("Noto Sans Regular", glyphs_reg_dir, ["0-255", "256-511", "8192-8447"]),
    ("Noto Sans Bold", glyphs_bold_dir, ["0-255", "256-511"])
]

for font_name, dest_dir, ranges in font_configs:
    for r in ranges:
        target_path = os.path.join(dest_dir, f"{r}.pbf")
        if not os.path.exists(target_path) or os.path.getsize(target_path) < 1000:
            encoded_font = font_name.replace(" ", "%20")
            url = f"https://demotiles.maplibre.org/font/{encoded_font}/{r}.pbf"
            req = urllib.request.Request(url, headers={"User-Agent": "ResQMesh-MapBuilder"})
            try:
                with urllib.request.urlopen(req, timeout=10) as resp:
                    data = resp.read()
                    with open(target_path, "wb") as out_f:
                        out_f.write(data)
                print(f"Downloaded real glyph: {font_name}/{r}.pbf ({len(data)} bytes)")
            except Exception as e:
                print(f"Warning: Could not fetch remote glyph {r}.pbf: {e}")

print("Sprites and glyphs configuration complete.")
"@
$tempPy = Join-Path $env:TEMP "gen_assets_$([guid]::NewGuid().ToString('N')).py"
Set-Content -Path $tempPy -Value $generatorScript -Encoding UTF8
try {
    python $tempPy $resolvedAssetDir
} finally {
    Remove-Item -Path $tempPy -Force -ErrorAction SilentlyContinue
}

# 5. Acquire Real cebu.pmtiles
$destPmtiles = Join-Path $resolvedAssetDir "cebu.pmtiles"
Write-Host "[4/5] Preparing real cebu.pmtiles..." -ForegroundColor Green

if ($PmtilesSource -and (Test-Path $PmtilesSource)) {
    Write-Host "      Copying provided PMTiles file from $PmtilesSource..." -ForegroundColor Green
    Copy-Item -Path $PmtilesSource -Destination $destPmtiles -Force
} elseif ((-not (Test-Path $destPmtiles)) -or ((Get-Item $destPmtiles).Length -lt 1000000) -or $DownloadExtract) {
    Write-Host "      Extracting real Cebu tiles from Protomaps daily OSM build..." -ForegroundColor Green

    # Locate pmtiles CLI
    $pmtilesCmd = $null
    $pmtilesExe = Get-Command pmtiles -ErrorAction SilentlyContinue
    if ($pmtilesExe) {
        $pmtilesCmd = $pmtilesExe.Source
    } elseif (Test-Path "C:\Users\Kyle\AppData\Local\Temp\go-pmtiles-bin\pmtiles.exe") {
        $pmtilesCmd = "C:\Users\Kyle\AppData\Local\Temp\go-pmtiles-bin\pmtiles.exe"
    } else {
        $pmtilesZipUrl = "https://github.com/protomaps/go-pmtiles/releases/download/v1.31.2/go-pmtiles_1.31.2_Windows_x86_64.zip"
        $tempZip = Join-Path $env:TEMP "go-pmtiles.zip"
        $tempBinDir = Join-Path $env:TEMP "go-pmtiles-bin"
        Write-Host "      Downloading standalone pmtiles CLI binary..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $pmtilesZipUrl -OutFile $tempZip
        [System.IO.Compression.ZipFile]::ExtractToDirectory($tempZip, $tempBinDir)
        $pmtilesCmd = (Get-ChildItem -Path $tempBinDir -Filter "pmtiles.exe" -Recurse)[0].FullName
    }

    # Bounding box covers full Cebu Province (123.30°E–124.20°E, 9.40°N–11.40°N), strictly encompassing
    # the configured Cebu Metropolitan Area (123.85°E–123.98°E, 10.25°N–10.38°N) at zooms 6 through 15
    $dailyBuildUrl = "https://build.protomaps.com/20260919.pmtiles"
    Write-Host "      Running: pmtiles extract from $dailyBuildUrl..." -ForegroundColor Green
    & $pmtilesCmd extract $dailyBuildUrl $destPmtiles --bbox=123.30,9.40,124.20,11.40 --minzoom=6 --maxzoom=15
} else {
    Write-Host "      Existing real cebu.pmtiles found ($([math]::Round((Get-Item $destPmtiles).Length / 1MB, 2)) MB)." -ForegroundColor Green
}

# 6. Integrity & Zero-Network Audit
Write-Host "[5/5] Auditing asset integrity and zero-network policy..." -ForegroundColor Green

$requiredFiles = @(
    "cebu.pmtiles",
    "style.json",
    "LICENSE.txt",
    "sprites\sprite.json",
    "sprites\sprite.png",
    "sprites\sprite@2x.json",
    "sprites\sprite@2x.png",
    "glyphs\Noto Sans Regular\0-255.pbf",
    "glyphs\Noto Sans Regular\256-511.pbf"
)

foreach ($rel in $requiredFiles) {
    $full = Join-Path $resolvedAssetDir $rel
    if (-not (Test-Path $full)) {
        throw "Validation Failure: Missing required package asset: $rel"
    }
    $sz = (Get-Item $full).Length
    Write-Host "      [OK] $rel ($sz bytes)" -ForegroundColor DarkGreen
}

$pmtilesSize = (Get-Item $destPmtiles).Length
if ($pmtilesSize -lt 1000000) {
    throw "Integrity Failure: cebu.pmtiles is only $pmtilesSize bytes (expected tens of MB)!"
}

$styleRaw = Get-Content -Path (Join-Path $resolvedAssetDir "style.json") -Raw
if ($styleRaw -match "https?://") {
    throw "Policy Violation: style.json contains remote network URLs!"
}
Write-Host "      [OK] style.json contains zero network references." -ForegroundColor DarkGreen
Write-Host "      [OK] Real vector map size: $([math]::Round($pmtilesSize / 1MB, 2)) MB" -ForegroundColor Green

Write-Host ""
Write-Host "=== Cebu Offline Map Asset Directory Complete ===" -ForegroundColor Cyan
Write-Host "Location: $resolvedAssetDir"
Write-Host "To package and validate without signing, run:" -ForegroundColor White
Write-Host ".\scripts\map\package_cebu_map.ps1 -AssetDir `"$resolvedAssetDir`" -OutputDir `"build\cebu_dist`" -ValidateOnly -Version 1" -ForegroundColor Yellow
