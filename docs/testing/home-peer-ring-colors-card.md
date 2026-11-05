# Home peer ring colors: phone card

Status: active procedure. Local JVM tests pass; device behavior is unverified.

Fix: the Home `People Nearby` ring drew every state other than direct, offline and blocked in the relay orange. A relayed peer that lost its route but was still briefly visible over Bluetooth (DISCOVERED), or a link being checked or connected, therefore kept the relay color. Ring colors now match the Network screen: green direct, orange routed multi-hop (RELAY/HOPPED), amber checking/connecting, grey nearby-only or offline, red blocked. Presentation only; BLE, routing and delivery are unchanged. `ModernSky` was renamed to `RelayOrange` (same value).

## Build identity

Branch `fix/stale-relay-offline`. Record the commit, version and APK hash used.

## Devices and setup

Debug builds on three phones A, B and C in a line: A and C out of each other's radio range, B in range of both. Bluetooth and the mesh on for all three. The user installs, launches and operates the phones.

## Steps and expected results

1. **Relayed:** wait until A's Network screen lists C under Multi-Hop Mesh Relays. On A's Home, expect C with an orange ring and B with a green ring.
2. **Relayed peer offline:** turn off Bluetooth on C. Within about 30 s on A's Home, expect C grey (or gone after the offline row expires), never amber or still orange once Network no longer lists C as relayed.
3. **Relayed peer drops but stays visible:** restore C, wait for step 1, then move C so it is barely in A's radio range while B is turned off. When A's Network screen shows C under Nearby & Discovered, expect C grey on Home.
4. **Checking:** with A and B directly linked, walk B away until A's Network screen shows B as Checking. Expect B amber on Home, not orange.
5. **Direct and blocked unchanged:** a direct peer stays green; a blocked peer stays red.

## Failure indicators

FAIL if Home shows orange for a peer that Network lists as Nearby, Checking or Offline; if a relayed peer stays orange after Network shows it offline; or if Home and Network disagree on a peer's state for more than a few seconds.

## Markers

Search `TOPOLOGY_UPDATED` and `BLE_MESH` retire lines in the reported window to time when B dropped C and A lost the route.

## Report

Build identity, devices, Android versions, per-step pass or fail, rough time from C going off to grey on A, and screenshots if a color is wrong.
