<div align="center">
  <img src="app/src/main/res/drawable/resqmesh_sublogo.png" alt="ResQMesh logo" width="520">

  <p><strong>Offline-first emergency communication for nearby Android devices.</strong></p>
  <p>Native Bluetooth Low Energy messaging, local persistence, private-message protection, SOS workflows, and experimental multi-hop routing—without depending on cellular service or internet access.</p>
</div>

> [!IMPORTANT]
> ResQMesh is a capstone research project and production-oriented prototype under active reliability validation. A successful build does not prove radio range, delivery reliability, connection capacity, security guarantees, or multi-hop behavior. Those claims require focused tests on physical Android devices.

## Overview

ResQMesh explores how nearby Android phones can exchange essential information when conventional infrastructure is unavailable or unreliable. Phones discover one another through native BLE advertising and scanning, establish dual-role GATT links, and exchange framed Protobuf payloads. Where the hardware and Android version support it, an optional L2CAP channel can carry payload traffic while GATT remains responsible for setup, readiness, liveness, and fallback.

The project prioritizes dependable text and SOS delivery, clear connection state, safe recovery, and fail-closed private messaging. It does **not** currently use Google Nearby Connections or Wi-Fi Direct.

## What ResQMesh includes

| Capability | Current implementation | Validation state |
| --- | --- | --- |
| Offline peer discovery | Native BLE advertising and scanning | Implemented; device-dependent |
| Direct communication | GATT client/server roles with payload-readiness tracking | Active physical validation |
| Optional faster payload path | L2CAP after GATT setup, with GATT fallback | Experimental; device support varies |
| Public and private text | Compose chat backed by repository and Room state | Implemented; lifecycle testing ongoing |
| Private-message protection | Per-message AES-GCM content encryption with Android Keystore RSA keys | Fail-closed policy implemented; authenticated E2EE is not yet claimed |
| SOS workflows | Broadcast, cancellation, monitoring, location, and map-oriented screens | Implemented; concurrent-alert ownership still needs validation |
| Radar and peer status | Direct, indirect, checking, and offline-oriented presentation | Implemented; accuracy depends on lifecycle evidence |
| Graph-based routing | Known-node graph and computed paths for relaying | Experimental; not a guarantee of self-healing delivery |
| Local persistence | Room-backed nodes and messages | Implemented |
| Audio and media paths | Live-audio and media-related payload/UI code | Experimental; not part of the current reliability claim |

The current priority is reliability of BLE text, private messaging, reconnect recovery, and SOS behavior—not adding more transports or large features.

## How it works

```text
User action in Jetpack Compose
        │
        ▼
ViewModel / use case
        │
        ▼
MeshRepository ─── Room persistence
        │
        ▼
PayloadFactory → Protobuf MeshPayload
        │
        ▼
NativeBleManager
        │
        ├── ready GATT client/server link
        └── owned L2CAP socket, when available
        │
        ▼
Peer PayloadDispatcher → handler → repository → UI
```

A radio connection is not automatically ready for application data. ResQMesh tracks discovery, configuration, subscription, payload readiness, liveness, and teardown separately so the UI and router do not treat a merely visible or half-configured phone as a usable peer.

For a source-aligned description of link ownership, routing, identity, cryptography, and persistence, see [Architecture](docs/architecture.md).

## Project structure

```text
test_resqmesh/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/testresqmesh/
│       │   │   ├── core/
│       │   │   │   ├── domain/       # Use cases and domain boundaries
│       │   │   │   ├── location/     # Location services
│       │   │   │   ├── model/        # Shared application models
│       │   │   │   ├── network/      # BLE, GATT, L2CAP, payload dispatch, crypto
│       │   │   │   ├── ui/           # Reusable Compose components and theme
│       │   │   │   └── utils/        # Logging, notifications, and helpers
│       │   │   ├── data/
│       │   │   │   ├── local/        # Room database, DAOs, and entities
│       │   │   │   ├── location/     # Location data implementation
│       │   │   │   └── repository/   # Mesh state, payload creation, and routing
│       │   │   ├── feature/
│       │   │   │   ├── comms/        # Public/private chat and walkie-talkie UI
│       │   │   │   ├── profile/      # Local identity and profile
│       │   │   │   ├── radar/        # Peer/routing visualization and tracking
│       │   │   │   ├── setup/        # Splash, identity, and permissions flow
│       │   │   │   └── sos/          # SOS broadcast, monitoring, and maps
│       │   │   └── ui/state/          # Shared immutable UI state
│       │   └── res/                    # Android resources and ResQMesh branding
│       └── test/                       # Focused JVM unit tests
├── docs/                               # Concise, active project documentation
├── scripts/                            # Documentation checks and Logcat capture
├── captures/                           # Local physical-device evidence
├── archive/                            # Preserved superseded documentation
├── AGENTS.md                           # Codex task and context router
└── README.md                           # Project landing page
```

## Technology stack

- **Language:** Kotlin, targeting Java 11 bytecode
- **UI:** Jetpack Compose and Material 3
- **Radio transport:** Android native BLE advertising/scanning, GATT, and optional L2CAP
- **Concurrency/state:** Kotlin coroutines and `StateFlow`
- **Payload encoding:** Kotlin Serialization with Protobuf
- **Persistence:** Room
- **Dependency injection:** Koin
- **Maps/location:** MapLibre Native and Google Play Services Location
- **Security building blocks:** Android Keystore RSA and AES-GCM
- **Android support:** minimum SDK 24, target SDK 36

## Getting started

### Prerequisites

- Android Studio with a compatible Android SDK and JDK
- Windows PowerShell for the commands below, or the equivalent Gradle command for your shell
- Two physical BLE-capable Android phones for meaningful transport validation
- USB debugging and ADB only when collecting focused device logs

### Build and run local tests

From the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install the same build on each test phone. Complete the in-app identity and permission flow, enable Bluetooth and location services when Android requires them, and begin with the two-phone smoke test in [Validation](docs/validation.md).

> [!NOTE]
> The user performs installation and physical phone interactions. Codex can build the APK, prepare exact test steps, and analyze focused captures, but producing an APK is not the same as testing it.

## Permissions

ResQMesh requests permissions according to the Android version and feature in use:

- Bluetooth scan, advertise, connect, and legacy Bluetooth permissions
- Coarse/fine location for BLE compatibility and location-sharing features
- Notifications and vibration for delivery and SOS alerts
- Microphone access for live-audio features
- Network access used by map-related components; core nearby BLE messaging is designed to work without internet service

Only grant permissions you intend to exercise during a test. Permission behavior can differ across Android versions and device manufacturers.

## Testing on physical phones

Start with two phones running the exact same APK:

1. Confirm both phones complete setup and reach a payload-ready peer state.
2. Send public text in both directions and confirm each message appears once.
3. Send private text in both directions; a missing usable key must cause a safe refusal, not plaintext fallback.
4. Restart one app, reconnect, and repeat delivery.
5. Toggle Bluetooth or move briefly out of range, return, and verify the stale link is replaced.
6. Confirm Radar distinguishes a ready peer from checking, recently seen, indirect, or offline state.

For device-facing changes, record the APK identity, phone models, Android versions, exact actions, visible result, and approximate failure time. Capture logs when needed with:

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

Analysis starts with the reported time window and ResQMesh markers for link generation, readiness, queue completion, heartbeat, key handling, routing, and delivery. System Bluetooth or crash logs are inspected only if those markers cannot explain the result. See the complete [physical validation workflow](docs/validation.md).

## Documentation

The active documentation is intentionally small and routed by purpose:

| Document | Use it for |
| --- | --- |
| [Architecture](docs/architecture.md) | Current system shape, data flow, BLE lifecycle, security, and source map |
| [Current status](docs/status.md) | Active objective, implemented work, blockers, and next actions |
| [Validation](docs/validation.md) | Build commands, physical tests, Logcat workflow, and concise evidence |
| [Decisions](docs/decisions.md) | Accepted scope and engineering rules |
| [Research](docs/research.md) | Capstone questions, methodology, metrics, and claim boundaries |
| [UI guidance](docs/ui.md) | Stable Compose and connection-state presentation rules |

Codex uses [AGENTS.md](AGENTS.md) to load only the context needed for a task. Superseded material is historical context, not current implementation guidance.

## Current project status

The working tree includes callback-driven GATT operations, server notification completion, heartbeat challenge ownership, endpoint cleanup, L2CAP fallback, persistent Keystore identity, fail-closed private messaging, and focused lifecycle/security unit tests.

The latest combined change set has local build/unit-test evidence and one encouraging two-phone physical run covering reconnects and varied traffic. Blocking remains a separate known policy/cleanup defect, and the result does not establish three-phone routing or production reliability. Remaining engineering gates include late server-callback ownership, bounded queues and frames, one consistent direct-link limit, route expiry/withdrawal, SOS ownership, and a defined production security threat model.

See [Current status](docs/status.md) before starting implementation or making capability claims.

## Research and contribution

ResQMesh is intended to produce measurable capstone evidence rather than rely on optimistic feature descriptions. Evaluation should report delivery success, latency, reconnect time, route recovery, duplicate rate, range conditions, battery behavior, SOS correctness, and device-specific failures. Results must identify the tested devices and conditions and must not be generalized into production guarantees without sufficient evidence.

## Contributing

Contributions should keep the reliability-first scope intact:

1. Check [Current status](docs/status.md) and the relevant architecture or decision page.
2. Make the smallest coherent change and add focused tests where practical.
3. Run the local checks appropriate to the change.
4. For BLE or device behavior, provide a physical test card and do not close the issue from unit tests alone.
5. Update only the canonical document affected by the change; do not add session transcripts or redundant planning files.

When reporting a bug, include the build identity, phone models, Android versions, reproduction steps, expected and actual behavior, and the narrow time window of the failure. Avoid sharing message contents, keys, or other sensitive payload data.

---

<div align="center">
  <strong>ResQMesh</strong><br>
  Communication research for when infrastructure cannot be assumed.
</div>
