# Cebu offline map packaging and release walkthrough

Last reviewed: 2026-10-04. Status: active packaging procedure. Source baseline: `2e27013`; existing map scripts and verifier inspected without execution.

This guide documents packaging/signing of the Cebu offline vector artifact. Offline map/SOS display was reported working on phones; package corruption/update/rollback tests remain unconfirmed. Historical artifact sizes below are original build measurements, not newly reproduced results.

---

## 1. Data Sources & Licensing

| Component | Source / Authority | License | Attribution Requirement |
| :--- | :--- | :--- | :--- |
| **Geospatial Data** | [OpenStreetMap](https://www.openstreetmap.org/copyright) | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1.0/) | `© OpenStreetMap contributors, ODbL 1.0` |
| **Vector Tiles** | [Protomaps](https://protomaps.com) Basemap Extracts | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1.0/) / BSD-3-Clause | Carried via `LICENSE.txt` |
| **Tactical Style** | MapLibre Style Specification | BSD 3-Clause | Embedded in `style.json` |
| **Fonts / Glyphs** | [Noto Sans](https://fonts.google.com/noto) (MapLibre SDF) | SIL Open Font License 1.1 | Included locally in `glyphs/` |
| **Icons / Sprites** | Tactical Vector SVG Icons | CC-BY 4.0 / MIT | Included locally in `sprites/` |

> [!IMPORTANT]
> Map style resources must be self-contained: tiles, sprites and glyphs resolve from local storage (`file://` and `pmtiles://file://`) after activation. The style validator rejects remote resource URLs; license/attribution text may still contain web links. Offline phone display was reported working, not newly measured during this documentation change.

---

## 2. Tool Prerequisites

1. **PowerShell 5.1+ or PowerShell Core (pwsh 7+)** (standard on Windows).
2. **Python 3.8+** in `PATH` (used by `build_cebu_assets.ps1` for local sprite and glyph configuration).
3. **OpenSSL or JDK 17+ (`javac`/`java`)** in `PATH` (for default ECDSA P-256 manifest signing; packaging also supports Ed25519 fallback).
4. **Android SDK Platform Tools (`adb`)** (for developer testing with `install_local_map_dev.ps1`).
5. **(Optional) `pmtiles` CLI**:
   * Official standalone binary from [protomaps/go-pmtiles releases](https://github.com/protomaps/go-pmtiles/releases).
   * Needed only if extracting vector tiles directly from Protomaps daily planetary builds over HTTP Range requests.

---

## 3. Geographic Coverage & Size Estimates

* **Target Region:** Cebu Province & Metropolitan Area (Cebu City, Mandaue, Lapu-Lapu, Talisay, Consolacion, Minglanilla, and provincial arterial corridors).
* **Bounding Box:**
  * Minimum Latitude: `9.40°N`
  * Minimum Longitude: `123.30°E`
  * Maximum Latitude: `11.40°N`
  * Maximum Longitude: `124.20°E`
* **Zoom Levels:** `6 to 15` (tactical road network, waterways, building footprints, and emergency POIs; MapLibre overzooms to 16+ seamlessly).
* **Package Metrics (Phase 3.5b Real Artifact):**
  * `cebu.pmtiles`: **27.68 MB** (20,672 addressed tiles, 13,516 tile entries).
  * `cebu-v1.zip`: **26.22 MB** (compressed vector package).
  * `glyphs/`: ~450 KB (real MapLibre Signed Distance Field font stacks for Noto Sans Regular and Bold).
  * Device storage required: >= 100 MB available (enforced by `MapStorageGuard` safety buffer).

---

## 4. Tile Schema & Landmark Verification

The vector tiles adhere to the standard Protomaps / OpenMapTiles schema with the following source layers:
* `earth`: Land polygon basemap
* `water`: Ocean, seas, rivers, lakes, reservoirs
* `landuse`: Parks, commercial, industrial, healthcare zones
* `buildings`: Structural footprints with height metadata
* `roads`: Highways, arterials, residential roads, service alleys
* `boundaries`: Municipal and provincial borders
* `places`: City, town, and neighborhood labels
* `pois`: Emergency facilities, hospitals, clinics, police, shelters

### Inspection at Cebu City Location
Inspection of tile coordinate `14/13830/7719` (centered over Cebu Provincial Capitol / Fuente Osmeña) confirms real-world landmarks:
* **Roads:** `Osmeña Boulevard`, `Gorordo Avenue`, `N. Escario Street`, `Archbishop Reyes Avenue`, `Bohol Avenue`, `Acacia Street`, `Andres Abellana Street`.
* **Landmarks & Facilities:** `Cebu Provincial Capitol`, `Velez Hospital`, `Guadalupe`, `Capitol Site`, `Capitol Centrum`.

---

## 5. Exact Step-by-Step Commands

### Step 1: Generate maintainer ECDSA P-256 keypair (once)
```powershell
.\scripts\map\generate_signing_keypair.ps1 -OutputDir "$HOME\.resqmesh\keys"
```
* Generates `map_signing_private_ec.pem` (kept secure and outside the repository).
* Generates `map_signing_public_ec.der`.
* Outputs formatted Kotlin code for embedding into `MapCatalogConfig.kt` during separately authorized map-release work. Do not replace existing keys merely to follow this documentation update.
* The current generator creates P-256 keys. Existing Ed25519 packages remain a supported fallback; the packaging script prefers an available EC key and falls back to Ed25519, while the app verifier tries ECDSA then Ed25519. Use the public key corresponding to the selected signing key.

### Step 2: Assemble the Real Asset Directory
To assemble the complete asset folder (`cebu_assets/`):
```powershell
.\scripts\map\build_cebu_assets.ps1 -AssetDir "build\cebu_assets"
```
* Automatically acquires real `cebu.pmtiles` (~26.4 MB) from daily OpenStreetMap builds via `pmtiles extract`.
* Downloads authentic MapLibre SDF glyphs (`Noto Sans Regular` and `Noto Sans Bold`).
* Writes real-schema `style.json`, tactical `sprites/`, and `LICENSE.txt`.

### Step 3: Validate and Assemble Package (Without Signing)
```powershell
.\scripts\map\package_cebu_map.ps1 `
    -AssetDir "build\cebu_assets" `
    -OutputDir "build\cebu_dist" `
    -ValidateOnly `
    -Version 1
```
* **What this does:**
  1. Validates that `cebu.pmtiles` and `style.json` exist.
  2. Enforces zero `http://` or `https://` URLs in `style.json`.
  3. Verifies that all sprites and real glyphs (`0-255.pbf`, etc.) exist.
  4. Compresses all files recursively into `build\cebu_dist\cebu-v1.zip` (~26.22 MB).
  5. Computes exact package byte size and SHA-256 hash.
  6. Generates `build\cebu_dist\cebu-v1.manifest.json`.
  7. **STOPS before signing.** The private key is never accessed, printed, or modified.

### Step 4: Maintainer Offline Signing (Local Maintainer Only)
When ready to produce the official signed release artifacts:
```powershell
.\scripts\map\package_cebu_map.ps1 `
    -AssetDir "build\cebu_assets" `
    -OutputDir "build\cebu_dist" `
    -PrivateKeyPath "$HOME\.resqmesh\keys\map_signing_private_ec.pem" `
    -Version 1
```
* Produces three release files in `build\cebu_dist\`:
  1. `cebu-v1.zip` (tens of MB: ~26.22 MB)
  2. `cebu-v1.manifest.json`
  3. `cebu-v1.manifest.sig`

### Step 5: Optional developer-device test (explicit authorization required)
```powershell
.\scripts\map\install_local_map_dev.ps1 `
    -PackageZip "build\cebu_dist\cebu-v1.zip" `
    -ManifestJson "build\cebu_dist\cebu-v1.manifest.json" `
    -PackageVersion 1
```
* Extracts package on developer PC (avoiding device-side `unzip` limitations).
* Pushes files into app sandbox storage via `adb` and `run-as com.example.testresqmesh`.
* Writes `active_package.json` pointer file.
* Allows verification of `SosMapScreen` and `OfflineMapSettingsScreen` with internet unavailable. Physical installation/ADB writes require explicit user direction under the repository phone boundary. This synchronization ran none of these commands.

For the current APK identity and qualitative offline-use report, see [validation](../../docs/validation.md). The app supports older Android through the P-256 default; Ed25519 availability is not a universal Android assumption.
