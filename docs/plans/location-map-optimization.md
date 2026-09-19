# Location confidence and Cebu offline map pilot

## Purpose

Replace the slow, policy-incompatible raster tile download with a Cebu-first offline vector map, and make every shared location explicitly carry its accuracy and capture time. This is a planned enhancement, not evidence of GPS accuracy, map completeness, or production readiness.

## Guardrails

- Finish the current P0 BLE, relay, and SOS validation work before implementation begins.
- Keep BLE as the active mesh/control transport. This work adds no Wi-Fi Direct, background live tracking, navigation, satellite imagery, or new routing policy.
- Location sharing is user-requested or SOS-only. Do not request background location permission.
- Never present a coordinate as precise or live without supporting accuracy/freshness data; never log coordinates or private payload contents in the diagnostic terminal.
- OSM public raster tiles must not be bulk downloaded. The current `MapDownloadManager` path must be removed rather than tuned. See <https://operations.osmfoundation.org/policies/tiles/>.

## Accepted design

### Offline map package

Use MapLibre Native Android in the existing Compose `AndroidView` boundary. Render a verified local Cebu City vector PMTiles package with a fully local style, sprites, glyphs, and required attributions. MapLibre can read PMTiles from app storage; do not use it to prefetch public OSM raster tiles. See <https://maplibre.org/maplibre-native/android/examples/data/PMTiles/>.

The initial package has a balanced 80--150 MB budget and is downloaded over Wi-Fi before deployment. Publish versioned GitHub Release assets:

```text
cebu-vN.zip
cebu-vN.manifest.json
cebu-vN.manifest.sig
```

`MapPackageManifest` contains package ID/version, bounding box, min/max zoom, byte length, SHA-256, release date, attribution, and asset URL. The app embeds an Ed25519 public key; the maintainer signs manifests offline. Download to `.part`, resume only when supported, verify hash and signature, extract to a versioned directory, then atomically activate it. Preserve the last verified package on failed, interrupted, corrupt, or low-storage updates.

Build each package reproducibly from a properly attributed redistributable Cebu OSM-derived extract. Keep every style dependency local, show `© OpenStreetMap contributors` on-map, and make no network request after installation.

### Location confidence

Replace direct fused-location calls in `CommunicationViewModel` with a testable `LocationClient` API returning:

```kotlin
LocationFix(latitude, longitude, accuracyMeters, capturedAtMillis)
LocationCaptureResult.Fresh | Cached | Approximate |
    PermissionDenied | LocationDisabled | TimedOut | Unavailable
```

Maintain only an in-memory emergency cache while the active foreground mesh node runs: balanced accuracy every 60 seconds, minimum movement 25 m; never automatically persist, display, or broadcast it.

For a manual location share, request a fine, high-accuracy fix for up to 10 seconds. Send a fresh fix only at <=50 m accuracy. If only a lower-confidence recent fix exists, display its radius and age and require explicit confirmation; otherwise send no coordinate.

For SOS, broadcast immediately. Attach a cached coordinate only when no older than two minutes and <=250 m accuracy, then request one refined fix for up to eight seconds. Send one related update only if it improves the original and is <=100 m. Do not delay SOS for GPS and do not manufacture or silently reuse stale coordinates. Android users may grant approximate rather than precise location, so the UI must disclose it. See <https://developer.android.com/develop/sensors-and-location/location/permissions>.

Add optional location accuracy and capture-time fields to public `MeshPayload`, the private encrypted envelope, `ChatMessage`, and `MessageEntity`; migrate Room forward with nullable columns. Existing peers/messages remain readable and show `Accuracy unavailable`. Add a distinct `SOS_LOCATION_UPDATE` with a new envelope ID and a reference to the source SOS ID. Display it as a related update; do not merge/cancel alerts by mutable sender name while `SOS-01` remains open.

## UX and implementation sequence

1. Add the domain/package interfaces, manifest verification, package downloader, and storage guards. Add MapLibre and replace osmdroid only after an installed local package can render without network.
2. Rework `SosMapScreen`: high-contrast tactical road/POI map, map package state, visible attribution, SOS marker, accuracy circle, capture/received time, and text-plus-icon confidence status. Keep 48 dp controls and safe-area-aware overlays in both themes.
3. Add a mapless fallback with decimal coordinates, radius, age, sender, and optional local-device bearing. Map absence must never hide or delay SOS information.
4. Add a Profile/Settings map-management entry before an emergency: coverage, package size/version, Wi-Fi-only download, update, and delete. Do not require a user to open an SOS alert first.
5. Introduce the location-confidence capture flow and wire metadata through persistence and mesh payloads. Keep private sends fail-closed and do not change BLE/routing policy.
6. Add related SOS update presentation only after metadata and message persistence are tested; automatic alert replacement remains deferred behind `SOS-01`.

## Verification gates

- Unit tests: precision/freshness selection; approximate, denied, disabled, timeout, and stale-cache cases; immediate SOS plus one eligible update; protobuf backward compatibility; Room migration.
- Package tests: invalid signature/hash, interruption, insufficient storage, update rollback, and no installed map fallback.
- UI tests: confidence wording, map package states, accessible controls, and no-coordinate fallback using fakes rather than a real GPU/GPS.
- Phone card: open sky, urban, indoor, precise-only, approximate-only, denied, location-disabled, and Battery Saver conditions. Record Android-reported accuracy and capture age; do not claim fixed GPS accuracy.
- Fresh-install card: download Cebu over Wi-Fi; switch to airplane mode; verify local map open/pan/zoom/labels/attribution/accuracy circle during an active BLE session. Interrupt/corrupt an update and prove the previous map still opens.

## Handoff protocol for Antigravity

Before editing, read `AGENTS.md`, `docs/status.md`, `docs/architecture.md`, `docs/validation.md`, and this plan. Return a read-only implementation map listing affected models, wire fields, Room migration, map package paths, and test targets. Wait for approval. Implement one numbered sequence step at a time; run relevant deterministic checks; do not commit, push, create a PR, or claim physical/GPS validation without explicit user direction.
