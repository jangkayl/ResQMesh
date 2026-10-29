# Incident reconnect sync card

Prepared: 2026-10-29. Status: pending. Branch `fix/incident-reconnect-sync` (stacked on `feat/incident-notifications`). See the incident paragraph in [architecture](../architecture.md).

Help offers and lead decisions made while two phones are apart should appear in incident details on the other phone soon after they reconnect.

**2026-10-29 capture (user-run, debug, V2424 / SM-A236E / CPH2127, build hash not supplied):** a help change sent while all links were down (`targets=0`) never reached the reporter. Later changes waited behind it as `DEPENDENCY_PENDING`. Background repair could not fetch it because the inbound limiter allowed only 3 sync requests (then 6/min) per origin, shared by SOS and incident sync. It logged `RL_DROP`/`RL_COOLDOWN` for `EVENT_SYNC_REQ` on every phone, so repair requests timed out (`RETRY ... timeout`, `STALE`). The final fix (D29) removes the sync rate limit and bounds sync instead. Sync covers open incidents plus closures from the last 24 h, at most 100, using a cached snapshot. Live broadcasts of new incidents and offer changes are paced per signing key, and catch-up sync is never paced. This build uses incident sync protocol 3, so **reinstall every phone together**; an older build only gets the limited legacy sync. Phones that already hold wrongly rejected events from older builds repair themselves on the next sync.

**Known limit (pre-existing, tracked by TRUST-01/SEC-01 later phases):** signing keys are self-minted. A modified app can still create many keys and push signed incidents through catch-up sync, which is not paced, and so fill a phone's storage or crowd the 100-incident window. This build bounds part of that: events dated over 10 minutes in the future are refused, peers can only request the standard sync window, and unknown-incident events never block completion. It does not stop key minting.

## Build identity

Use `scripts\device\manage_devices.ps1`, option 5, choose **Debug** (release builds hide the markers below), and press Enter to rebuild. Record the printed package, commit and SHA-256.

## Devices and setup

Use two phones: reporter A and helper B, both on this build and connected (READY). Start `scripts\device\capture_ble_logcat.ps1 -DurationMinutes 20`. Keep the incident details sheet open on the phone that observes each step. "Disconnect" means turning Bluetooth off on one phone, and "reconnect" means turning it back on.

## Steps and expected results

A reports an incident, and B offers help. Then, for each step: disconnect, make the change, reconnect, and watch the other phone.

1. **Offer edit:** B edits its offer note. Expected: A shows the new note.
2. **Withdraw and re-offer:** B withdraws. Reconnect and check, then disconnect again and have B offer again. Expected: A shows the offer removed, then back.
3. **Select while apart:** A selects B as lead. Expected: B shows the confirm/decline prompt.
4. **Concurrent decline and revoke:** with B selected and awaiting, disconnect. B declines and A removes B as lead. Reconnect. Expected: both phones show the incident open for offers with the same state, and A can select again.
5. **Confirm:** A selects B, then disconnect. B confirms, then reconnect. Expected: A shows B responding.
6. **Quick reconnect:** repeat step 1 with Bluetooth off for under 5 seconds. Expected: same as step 1, without waiting about 30 seconds.
7. **Missed change while apart (the captured failure):** with three phones, turn Bluetooth off on all phones briefly, have a helper edit its offer while it has no links, then reconnect. Expected: within about 35 seconds every phone shows the edit and any later changes. There should be no `RL_DROP`/`RL_COOLDOWN` for `EVENT_SYNC_REQ` and no lasting `DEPENDENCY_PENDING`, and each link should reach `COMPLETE`.
8. **Relay:** A and C not directly connected, B in between. C offers, A selects C, C confirms. Expected: each change reaches the far phone within about 35 seconds.
9. **New phone joins:** before D joins, have an open incident older than 24 h if available, plus a recently cancelled one. Expected: within about 35 s, D shows open incidents and closures from the last 24 h, but not older closed ones.
10. **Report pacing:** report 4 incidents quickly on one phone. Expected: the 4th is blocked with "Next report available in N s", and the button returns when the countdown ends. All reported incidents reach the other phones.
11. **Offer pacing:** edit an offer 6 times quickly. Expected: the 6th shows "Available in N s". Withdraw still works immediately.

Expected timing: each update appears within about 10 seconds of the phones showing as connected again.

## Failure indicators and report

Grep markers for the step window: `INCIDENT_SYNC` (`START`, `MISMATCH`, `STALE`, `PENDING`, `COMPLETE`, `DEPENDENCY_PENDING`, `REJECTED`) and `INCIDENT_HELP`.

Report a FAIL for any of these:
- the details sheet still shows the old state after 30 seconds connected
- the two phones show different lead or offer states
- repeated `MISMATCH`/`PENDING` with no `COMPLETE`
- a `REJECTED` marker for a genuine helper or reporter action
- a crash

Report per step: PASS, FAIL or UNTESTED; the build identity; models and Android versions; approximate update delay; and the capture folder. Do not share incident descriptions or offer notes.
