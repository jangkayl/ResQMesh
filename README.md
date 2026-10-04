<div align="center">
  <img src="app/src/main/res/drawable/resqmesh_sublogo.png" alt="ResQMesh logo" width="520">

  <p><strong>Offline emergency communication and coordination for nearby Android devices.</strong></p>
  <p>Native Bluetooth Low Energy · Multi-hop messaging · Recorded voice · SOS · Incident coordination · Offline maps</p>
</div>

---

## Overview

ResQMesh is an Android capstone and production-oriented prototype for nearby messaging and emergency coordination without cellular service or internet.

Native BLE advertising/scanning discovers peers; GATT establishes client/server links. Optional L2CAP carries payloads; GATT retains setup, control and fallback. Peers relay traffic beyond direct neighbors. Wi-Fi Direct and Nearby Connections are inactive.

Persistent conversations, independent SOS alerts, reporter-selected helpers and local maps use offline identity without mandatory online login.

**Project stage:** working prototype with reported phone successes. Intermittent far-end delivery loss remains open; operational reliability is unproven.

### Explore the repository

- [Features](#features)
- [How the system works](#how-the-system-works)
- [Project structure](#project-structure)
- [Technology stack](#technology-stack)
- [Getting started](#getting-started)
- [Testing and diagnostics](#testing-and-diagnostics)
- [Current progress](#current-progress-and-known-limitations)
- [Documentation](#documentation)
- [Contributing](#contributing)

## Features

| Area | What the application provides |
| --- | --- |
| **Mesh discovery** | Nearby BLE discovery, direct-link readiness and recovery, with direct, relayed, checking, nearby and offline states |
| **Community messaging** | Public text and supported attachments, persistent history and visible pending/rejection feedback |
| **Private conversations** | Encrypted text and recorded notes, stable recipient identity, directed routing and application delivery/read receipts |
| **Radio channels** | Separate channel histories, recorded notes and monitoring of newly received notes on the selected channel |
| **SOS** | Alert creation, dedicated conversations, location/map access, receiver-local silence and origin-controlled termination |
| **Emergency incidents** | Reports, helper offers, reporter selection, helper confirmation, withdrawal/replacement and resolved/cancelled history |
| **Offline maps** | MapLibre rendering of local PMTiles packages, signed manifests, integrity checks and atomic package activation |
| **Local persistence** | Room-backed messages, conversations, incident events, SOS state and eligible outgoing work |
| **Background sessions** | Opt-in foreground-service anchoring, session status and an explicit Go offline action |
| **Presentation** | Compose screens, Night/Daylight appearances, setup guidance and peer/network visualization |

### Communication and emergency workflows

**Private messaging:** usable routes and trusted keys are required. Missing keys/key changes pause or refuse sends. Pending work persists; recipients store messages before issuing receipts. Encryption uses Keystore RSA and per-message AES-GCM.

**Recorded voice:** notes retain their conversation/channel. Large supported envelopes use resumable journaled pieces and integrity checks. Radio monitors new selected-channel notes; history/private notes play manually.

**SOS:** each alert owns its thread/lifecycle. Sender Back preserves it; receivers silence locally. Ending one alert leaves others active. Signed terminal state reconciles after reconnect.

**Incident coordination:**

```text
Report → Offer help → Reporter selects → Helper confirms
                                      → Withdraw / replace
                                      → Resolve or cancel → History
```

Stable identity controls reporter/helper permissions. Disconnection never replaces a helper automatically; signed events reconcile after reconnect.

## How the system works

### Application data flow

```text
Compose screen
    │ User action / observed state
    ▼
ViewModel / use case
    ▼
MeshRepository ─────────────── Room persistence
    ▼
PayloadFactory → Protobuf MeshPayload
    ▼
NativeBleManager
    ├── payload-ready GATT client/server role
    └── owned L2CAP socket when available
    ▼
Peer PayloadDispatcher → handler → repository → UI
```

### Direct and relayed communication

```text
Direct:       A ───────── B
Relayed:      A ─── B ─── C ─── D
                    Intermediate phones forward traffic
```

Each device allows **three direct neighbors**; total reachable nodes can exceed that limit. Verify indirect paths for relay tests because nearby phones can form shortcuts.

Radio connection, READY, indirect reachability and visibility differ. Acceptance means local ownership; custody means neighbor journal storage; private DELIVERED requires the recipient's receipt.

Blocking controls **direct-link eligibility** on both endpoints, with independent local release. Relaying may remain available; blocking provides neither content muting nor authenticated access control.

See [Architecture](docs/architecture.md) for lifecycle ownership, directed topology, persistence and synchronization.

## Project structure

```text
ResQMesh/
├── app/
│   ├── schemas/                         # Exported Room database schemas
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/                     # Resources, icons and branding
│       │   └── java/com/example/testresqmesh/
│       │       ├── MainActivity.kt      # Android activity entry point
│       │       ├── ResqMeshApplication.kt
│       │       ├── app/navigation/      # App composition and destinations
│       │       ├── core/
│       │       │   ├── di/              # Dependency injection
│       │       │   ├── domain/          # Shared use cases
│       │       │   ├── location/        # Location capture and status
│       │       │   ├── map/             # Offline packages and verification
│       │       │   ├── model/           # Shared application models
│       │       │   ├── network/         # BLE, GATT, L2CAP, payloads, crypto
│       │       │   ├── service/         # Session/background anchoring
│       │       │   ├── ui/              # Shared components and theme
│       │       │   └── utils/           # Diagnostics and helpers
│       │       ├── data/
│       │       │   ├── local/           # Room database, dao/ and entity/
│       │       │   └── repository/      # Mesh, routing, incident/SOS state
│       │       └── feature/
│       │           ├── comms/          # Community, private chat and Radio
│       │           ├── home/           # Mission/home presentation
│       │           ├── incident/       # Incident coordination screens
│       │           ├── profile/        # Settings, identity and offline maps
│       │           ├── radar/          # Peer/network visualization
│       │           ├── setup/          # Onboarding and permissions
│       │           └── sos/            # Alerts, threads and maps
│       ├── test/                       # JVM unit tests
│       └── androidTest/                # Instrumentation/UI/migration tests
├── docs/
│   ├── architecture.md                 # Implemented system
│   ├── status.md                       # Current progress and open issues
│   ├── validation.md                   # Procedures and bounded evidence
│   ├── decisions.md                    # Accepted engineering policies
│   ├── research.md                     # Evaluation and claim limits
│   ├── ui.md                           # Stable presentation rules
│   ├── plans/                          # Active preparation plans
│   └── testing/                        # Physical-phone test cards
├── scripts/                            # Docs/structure checks and capture
│   └── map/                            # Map packaging/signing procedures
├── tools/                              # Local development helpers
├── captures/                           # Local evidence; excluded from Git
├── archive/                            # Historical/deferred documentation
├── .github/workflows/                  # CI checks
├── gradle/                             # Wrapper and dependency catalog
├── AGENTS.md                           # Task/context routing
└── README.md
```

Features use `ui/`, `ui/components/`, `viewmodel/` and `model/` where applicable. Shared UI belongs in `core/ui/`; transport/business policy belongs in core/data.

## Technology stack

| Layer | Technology |
| --- | --- |
| Language and state | Kotlin, coroutines and StateFlow |
| UI | Jetpack Compose, Material 3 |
| Transport and encoding | Android native BLE/GATT, optional L2CAP, Kotlin Serialization/Protobuf |
| Persistence and injection | Room with KSP, Koin |
| Maps and location | MapLibre Native, PMTiles, Google Play Services Location |
| Security building blocks | Android Keystore RSA, AES-GCM, signed workflow events and map manifests |
| Android configuration | Minimum SDK 24, compile SDK 36.1, target SDK 36; Java 11 bytecode |
| Current app version | 1.0.1 / version code 2 |

## Getting started

### Prerequisites

Use Android Studio, JDK 17 or a compatible newer runtime, and the configured Android SDK. Gradle is included through the wrapper. Radio tests require physical BLE-capable phones; ADB supports focused captures.

### Clone and build

```powershell
git clone https://github.com/jangkayl/ResQMesh.git
cd ResQMesh
.\gradlew.bat :app:assembleDebug --console=plain
```

Open the repository root in Android Studio and complete Gradle/SDK setup. Local SDK configuration belongs in the ignored `local.properties`.

Output: **`app/build/outputs/apk/debug/app-debug.apk`**.

### First phone session

1. Install the same APK on participating phones.
2. Complete onboarding, identity and permission setup.
3. Enable Bluetooth and any Android-version-specific location services needed for discovery.
4. Start mesh sessions and wait for a payload-ready peer.
5. Exchange Community/private text; verify arrival and returning private receipts.
6. Add a verified relay path and test the relevant SOS/incident workflows.

Preserve app data/keys for restart and upgrade tests. Enable background mesh explicitly for locked-screen runs. Download a supported map package before offline map use.

Permissions cover Bluetooth discovery/connection, location features, microphone, notifications and vibration as applicable. Initial map download needs network access; activated map resources render locally. Core mesh messaging requires no internet/server connection.

## Testing and diagnostics

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1
git diff --check
```

Android-test compilation is separate from execution; builds/emulators do not prove BLE behavior. The local mixed workspace has a recorded untracked reconnect-fixture compilation blocker; historical passes do not waive it.

Use the [physical checklist](docs/testing/physical-reliability-tests.md) and [Validation](docs/validation.md) for build identities and exact cases. Users install and operate physical phones.

```powershell
.\scripts\capture_ble_logcat.ps1 -DurationMinutes 10
```

Report APK hash, models/API, topology, actual transport, background setting, counts, exact failure time and focused capture. Measure arrival, receipt confirmation and playback separately. Never share message/audio content, key material or ciphertext previews.

## Current progress and known limitations

Reviewed **2026-10-04** against source baseline **`2e27013`**.

User-reported successes include four-phone text/receipts, complete notes/SOS, restart/relay recovery, toggles/background, mutual blocking, incident lifecycle/reconciliation, independent SOS cancellation and offline maps. These qualitative reports lack a complete device/count/timing/transport matrix.

**DELIVERY-01 remains open:** some public/private messages or voice notes fail to reach D through A→B→C→D. Cause unknown.

Measured scale, latency, range, battery/OEM behavior, accessibility, novice usability and release/upgrade readiness remain pending. Authenticated E2EE, forward secrecy, verified personal identity and guaranteed self-healing/delivery are not claimed. Recorded-note success does not establish live-PTT reliability.

## Documentation

| Reference | Purpose |
| --- | --- |
| [Status](docs/status.md) | Current objective, actual failures and next actions |
| [Architecture](docs/architecture.md) | Behavior, boundaries and source navigation |
| [Validation](docs/validation.md) | Candidate identity, commands, procedures and evidence |
| [Decisions](docs/decisions.md) | Accepted policy and deferred scope |
| [Research](docs/research.md) | Evaluation methodology and defensible claims |
| [UI guidance](docs/ui.md) | Presentation and interaction rules |
| [Capstone preparation](docs/plans/capstone-demo-readiness.md) | Demo, evidence and installation preparation |

[AGENTS.md](AGENTS.md) routes engineering tasks to minimum context. [Archived material](archive/README.md) preserves superseded plans; it is historical reference. Resource requests, safety check-ins, expanded replication and further transport/repository extraction remain deferred proposals.

## Contributing

Read status/relevant docs, preserve unrelated work, make focused changes and run checks. Update canonical docs and provide device test cards. Failed checks block readiness; PR creation/merge requires explicit instruction.

Report build, devices, steps, expected/actual outcomes and failure times without private contents. Preserve achievements alongside failures.
