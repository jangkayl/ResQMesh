# ResQMesh

ResQMesh is a decentralized emergency messaging system designed to facilitate communication during severe natural disasters and infrastructure collapses. It enables offline communication without cellular or internet connectivity by establishing a resilient, opportunistic mesh network directly between mobile devices.

## Core Features

- **Offline Node Discovery:** Automated, battery-efficient background scanning to detect nearby compatible devices using Bluetooth 5.4 and WiFi Direct.
- **Decentralized Messaging:** Direct device-to-device private message transmission utilizing a localized transport layer.
- **Data Hopping (Store-Carry-Forward):** An automated multi-hop relay mechanism where intermediate devices cache encrypted message payloads and physically transport them to out-of-range recipients.
- **Emergency Public Broadcasting:** A specialized flooding protocol designed to rapidly saturate the local network with high-priority SOS alerts.
- **End-to-End Encryption:** Strict cryptographic enforcement ensuring that intermediate relay nodes cannot intercept or decipher private communications.
- **Zero Cloud Dependency:** Operates entirely off-grid and transmits zero user data to external servers.

## Technical Stack

- **Platform:** Android
- **Wireless Protocols:** Bluetooth Core Specification 5.4 (BLE PAwR), WiFi Direct
- **Storage:** Android Room Persistence Library (SQLite)
- **Background Processing:** Android WorkManager API
- **Security:** End-to-End Encryption (E2EE), Cryptographic Identity (unique hashed IDs)

## Target Users

- **Civilian Survivors:** Intuitive, low-friction interface for broadcasting SOS signals and sharing status.
- **Emergency Responders:** Technical command interface for aggregating broadcasts and injecting evacuation directives.

## Performance Requirements

- **Discovery Speed:** Secure P2P connection established within ≤15 seconds.
- **Latency:** Direct D2D transmission within ≤5 seconds.
- **Reliability:** ≥85% successful relay rate under optimal conditions.
- **Hop Limit:** Maximum of 5 intermediate devices to maintain network stability.
