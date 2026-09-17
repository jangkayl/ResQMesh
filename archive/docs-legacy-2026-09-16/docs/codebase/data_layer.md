---
tags: [codebase, data, current]
---
# Data layer

- data/local/AppDatabase.kt, dao/, and entity/: Room database, chat messages, and known/blocked node records.
- data/repository/MeshRepository.kt: joins incoming network events, local storage, direct/indirect node state, and outgoing send decisions.
- data/repository/MeshRouter.kt: maintains a neighbor topology graph, last-seen information, breadth-first shortest paths, and a Kruskal spanning-tree calculation. It is not a complete distributed distance-vector protocol.
- data/repository/PayloadFactory.kt: creates Protobuf MeshPayload bytes and private-message envelopes through CryptoManager.

Local storage and route calculation are separate from the GATT lifecycle. When a socket remains READY but forwarding stops, inspect repository/router/dispatcher behavior rather than assuming a Bluetooth disconnect.
