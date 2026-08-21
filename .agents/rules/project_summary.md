---
description: High-level overview of the ResQMesh project, protocols, and architecture limits.
trigger: "When the user asks about the overall project, specifications, or general architecture rules."
tags: [resqmesh, architecture, overview]
---

# Project Summary: ResQMesh

## Overview
ResQMesh is a decentralized emergency messaging platform for Android, designed for disaster scenarios where infrastructure (cellular/internet) is unavailable. It uses Bluetooth 5.4 and WiFi Direct to create an opportunistic mesh network.

## Key Technical Specs
- **Protocols:** BLE PAwR, WiFi Direct.
- **Data Persistence:** Room/SQLite for DTN buffering.
- **Relay Mechanism:** Store-Carry-Forward with a 5-hop limit.
- **Security:** 100% E2E Encryption for private messages; Hashed User IDs for anonymity.
- **Performance:** <15s discovery, <5s latency (1 hop), >85% relay success rate.

## Documentation
- Detailed requirements are in `app/references/RESQMESH SRS.pdf`.
- Network specifics are documented in [bluetooth_architecture.md](bluetooth_architecture.md).
- UI constraints are located in [ui_guidelines.md](ui_guidelines.md).
