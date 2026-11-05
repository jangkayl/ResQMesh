<div align="center">
  <img src="docs/assets/resqmesh_sublogo.png" alt="ResQMesh logo" width="520">

  <p><strong>Offline emergency communication and coordination for nearby Android devices.</strong></p>
  <p>Native Bluetooth Low Energy · Multi-hop messaging · Recorded voice · SOS · Incident coordination · Offline maps</p>
</div>

---

## Overview

ResQMesh is an Android capstone and production-oriented prototype for nearby messaging and emergency coordination without cellular service or internet.

Native BLE advertising/scanning discovers peers; GATT establishes client/server links. Optional L2CAP carries payloads; GATT retains setup, control and fallback. Peers relay traffic beyond direct neighbors. Wi-Fi Direct and Nearby Connections are inactive.

All features use offline identity without mandatory online login.

**Project stage:** working prototype with reported phone successes. Intermittent far-end delivery loss remains open; operational reliability is unproven.

[Features](#features) · [How it works](#how-the-system-works) · [Structure](#project-structure) · [Stack](#technology-stack) · [Setup](#getting-started) · [Testing](#testing-and-diagnostics) · [Progress](#current-progress-and-known-limitations) · [Docs](#documentation) · [Contributing](#contributing)

## Features

| Area | What the application provides |
| --- | --- |
| **Mesh discovery** | Nearby BLE discovery, direct-link readiness and recovery, with direct, relayed, checking, nearby, offline and blocked states |
| **Community messaging** | Public text and attachments, including camera/gallery WebP images at three byte-capped qualities, persistent history and visible pending/rejection feedback |
| **Private conversations** | Encrypted text and recorded notes, stable recipient identity, directed routing and application delivery/read receipts |
| **Radio channels** | Separate channel histories, recorded notes and monitoring of newly received notes on the selected channel; no live push-to-talk |
| **SOS** | Alert creation, dedicated conversations with voice and images, per-thread message mute (never alarms), location/map access, receiver-local silence and origin-controlled termination |
| **Chat sync** | Silent pull of recent Community/SOS text and attachments from direct neighbors; never private or Radio |
| **Emergency incidents** | Reports, helper offers, reporter selection, helper confirmation, withdrawal/replacement and resolved/cancelled history |
| **Location** | User-controlled auto-detection, warm GPS, live map position and paced SOS updates |
| **Offline maps** | MapLibre rendering of local PMTiles packages, resumable downloads, signed manifests, integrity checks and atomic package activation |
| **Local persistence** | Room-backed messages, conversations, incident events, SOS state and eligible outgoing work |
| **Background sessions** | Opt-in foreground-service anchoring, session status and an explicit Go offline action |
| **Presentation** | Compose screens, Night/Daylight appearances, setup guidance and peer/network visualization |
| **Developer console** | Hidden diagnostics unlocked by a build-set PIN |

### Communication and emergency workflows

**Private messaging:** usable routes and trusted keys are required. Missing keys/key changes pause or refuse sends. Pending work persists; recipients store messages before issuing receipts. Encryption uses Keystore RSA and per-message AES-GCM.

**Recorded voice:** notes stop at 30 seconds and retain their conversation/channel. Large supported envelopes use resumable journaled pieces and integrity checks. Radio monitors new selected-channel notes; history/private notes play manually.

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

Each device allows **three direct neighbors**; total reachable nodes can exceed that limit. Verify indirect paths for relay tests because nearby phones can form shortcuts. Relays keep public media 10 minutes for later READY neighbors, behind live traffic.

Radio connection, READY, indirect reachability and visibility differ. Acceptance means local ownership; custody means neighbor journal storage; private DELIVERED requires the recipient's receipt.

Blocking controls **direct-link eligibility** on both endpoints, with independent local release. Relaying may remain available; blocking provides neither content muting nor authenticated access control.

See [Architecture](docs/architecture.md) for details.

## Project structure

```text
ResQMesh/
├── app/
│   ├── schemas/                         # Room schemas
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/                     # Resources
│       │   └── java/edu/cit/resqmesh/
│       │       ├── MainActivity.kt
│       │       ├── ResqMeshApplication.kt
│       │       ├── app/
│       │       │   ├── di/              # Dependency injection
│       │       │   └── navigation/      # App composition and destinations
│       │       ├── core/
│       │       │   ├── audio/           # Media policy
│       │       │   ├── domain/          # Shared use cases
│       │       │   ├── location/        # Location capture and status
│       │       │   ├── logging/         # Logging and traces
│       │       │   ├── map/             # Offline packages and verification
│       │       │   ├── model/           # Shared application models
│       │       │   ├── network/         # BLE, GATT, L2CAP, payloads, crypto
│       │       │   ├── notification/    # Alerts
│       │       │   ├── security/        # Input validation
│       │       │   ├── service/         # Session/background anchoring
│       │       │   ├── ui/              # Shared components and theme
│       │       │   └── utils/           # Diagnostics and helpers
│       │       ├── data/
│       │       │   ├── local/           # Room database, dao/ and entity/
│       │       │   ├── location/        # Location client
│       │       │   ├── mapper/          # Entity/model mappers
│       │       │   └── repository/      # Mesh, routing, incident/SOS state
│       │       └── feature/
│       │           ├── comms/          # Community, private chat and Radio
│       │           ├── devtools/       # Developer console
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
│   ├── demo/                           # Guide and limitations
│   ├── plans/                          # Active preparation plans
│   └── testing/                        # Physical-phone test cards
├── scripts/
│   ├── ci/                             # CI checks
│   ├── device/                         # Device tools
│   └── map/                            # Map packaging (walkthrough.md)
├── captures/                           # Local evidence (ignored)
├── archive/                            # Historical/deferred documentation
├── config/detekt/                      # Static-analysis rules
├── .github/workflows/                  # CI checks
├── gradle/                             # Wrapper and dependency catalog
├── AGENTS.md                           # Shared AI-tool router
├── CLAUDE.md, GEMINI.md                # Import AGENTS.md
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

Use Android Studio, JDK 17 or a compatible newer runtime, and the configured Android SDK. Radio tests require physical BLE-capable phones; ADB supports focused captures.

### Clone and build

```powershell
git clone https://github.com/jangkayl/ResQMesh.git
cd ResQMesh
.\gradlew.bat :app:assembleDebug --console=plain
```

Local SDK configuration and the optional `resqmesh.devPin` belong in the ignored `local.properties`.

Output: **`app/build/outputs/apk/debug/app-debug.apk`**. `scripts/device/manage_devices.ps1` builds and installs without Android Studio.

### Release build

For demo phones, copy `keystore.properties.example` to `keystore.properties`, then run `:app:assembleRelease` ([card](docs/testing/pre-demo-cleanup-card.md)).

### First phone session

1. Install the same APK on participating phones.
2. Complete onboarding, identity and permission setup.
3. Enable Bluetooth and any Android-version-specific location services needed for discovery.
4. Start mesh sessions and wait for a payload-ready peer.
5. Exchange Community/private text; verify arrival and returning private receipts.
6. Add a verified relay path and test the relevant SOS/incident workflows.

Preserve app data/keys for restart and upgrade tests. Enable background mesh explicitly for locked-screen runs. Download a supported map package before offline map use.

Permissions cover Bluetooth discovery/connection, location features, microphone, notifications and vibration. Initial map download needs network access; activated map resources render locally. Core mesh messaging requires no internet/server connection.

## Testing and diagnostics

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest detekt --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\ci\check_boundaries.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\ci\check_docs.ps1
git diff --check
```

Android-test compilation is separate from execution; builds/emulators do not prove BLE behavior.

Use the [physical checklist](docs/testing/physical-reliability-tests.md) and [Validation](docs/validation.md) for build identities and exact cases. Users install and operate physical phones.

```powershell
.\scripts\device\capture_ble_logcat.ps1 -DurationMinutes 10
```

Report APK hash, models/API, topology, transport, background setting, counts, failure time and a focused capture. Measure arrival, receipts and playback separately. Never share message content, keys or ciphertext.

## Current progress and known limitations

Open issues (including intermittent relay loss, DELIVERY-01) and next actions: [status](docs/status.md). Build identity and evidence: [validation](docs/validation.md).

Measured scale, latency, range, battery/OEM behavior, accessibility, novice usability and release/upgrade readiness remain pending. Authenticated E2EE, forward secrecy, verified personal identity and guaranteed self-healing/delivery are not claimed.

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
| [User guide](docs/demo/user-guide.md), [known limitations](docs/demo/known-limitations.md) | Demo usage and honest limits |

[AGENTS.md](AGENTS.md) routes AI-tool tasks. [Archived material](archive/README.md) is historical reference only.

## Contributing

Follow [AGENTS.md](AGENTS.md): focused changes, passing checks, updated docs and device test cards. PRs require explicit instruction.
