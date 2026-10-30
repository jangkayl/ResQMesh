# Unknown duplicate row card

Prepared: 2026-10-31. Branch: `fix/unknown-duplicate-row`, based on `5036cc5`. Status: JVM tests pass; not yet run on phones. A clean build is not BLE proof.

The fix keeps one peer-list row per peer. It removes the Unknown row left by a second socket, and moves the row to the surviving socket when one socket drops. Radio behavior is unchanged, so a second socket can still open; it appears as a second path on the Links card.

## Build identity

Use a debug build of the branch, so markers and the developer console are available. Record the commit and APK SHA-256 (`Get-FileHash <apk>`). The user installs and operates the phones.

## Devices and setup

Use phones A and B, plus C for step 6. Bluetooth on. Keep Radar open on one phone and the developer console Links tab open on the other, then swap between runs.

## Steps and expected results

| Step | Action | Expected | Failure indicator |
| --- | --- | --- | --- |
| 1 | Connect A and B | Each phone shows the other once as ONLINE (Direct) | A second row for the same device |
| 2 | Turn Bluetooth off and on on B, then wait for it to return | B returns as one row on A; any `Linking (Handshaking)` row clears once the name arrives | `Node XXXXX / Linking (Handshaking)` stays beside online B for more than about 15 s |
| 3 | Repeat step 2 five times, on each phone | Same result every time | A lasting extra row in any run |
| 4 | When Links shows two paths to one peer, compare with Radar | Radar shows one row; Links shows one card with two paths | Radar and Links list different peers |
| 5 | With two paths showing, wait for one to drop (Links shows one path), then send a message each way | The peer stays listed and both messages arrive | The peer disappears or shows OFFLINE while a path is still READY |
| 6 | Bring in phone C | C appears normally and resolves to its name | C stays unnamed, or joins another peer's row |

## Markers

Search these in the reported window only: `Auto-rename`, `Keeping READY endpoint`, `Duplicate link to`, `Merging stale endpoint`.

## Report fields

Commit, APK SHA-256, phone models, step results, how many runs showed two paths, and the time of any failure with a focused capture.
