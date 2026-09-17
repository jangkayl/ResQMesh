---
tags: [codebase, feature, current]
---
# Feature layer

The feature/ packages hold Compose screens and ViewModels:

- feature/comms/: public/private messaging, active chat, media/voice UI, and CommunicationViewModel.
- feature/radar/: RadarScreen, NetworkGraphVisualizer, responder tracking, and RadarViewModel. Current Radar/repository changes are uncommitted; review their direct-link labels with BLE collision tests.
- feature/sos/: distress broadcast, monitoring, alarms, location, and offline-map UI.
- feature/setup/: splash, runtime permissions, and node identity setup.
- feature/profile/: profile and settings screens.

ViewModels expose repository state through flows. UI labels should distinguish a direct physical link, a reachable mesh hop, an unverified provisional socket, and a recently seen peer. Do not claim delivery or a live route solely from a discovery row.
