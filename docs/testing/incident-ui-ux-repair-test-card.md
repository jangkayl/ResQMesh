# Incident UI/UX repair test card

Prepared: 2026-10-04. Updated: 2026-10-05. Status: physical cases UNTESTED; bounded emulator fixture outcomes are recorded in [validation](../validation.md). Scope: [Antigravity handover](../handoffs/antigravity-incident-ui-ux-repair.md).

## Build identity and setup

Candidate: UI commit `db2260e` on `fix/incident-ui-ux`, rebuilt during the separate TEST-01 fixture repair. Production source/configuration is unchanged. Version `1.0.1`/code `2`, debug signing, existing L2CAP enabled. APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `B2061C4B213E538675D9F800D39F7EFECD510E31E15DE3E76345255D9563AE3B`. Install one matching candidate on every participant; preserve app data and keys. Users install, launch and operate physical phones.

Earlier audit and installed-build identities remain in [validation](../validation.md). Their outcomes do not carry forward to this candidate.

Use A reporter, B helper and C alternate helper. Record models/API, route, permissions, blocks and appearance. Use harmless test reports. For deterministic legacy/stale-state/key-unavailable cases, use Compose instrumentation fixtures; do not alter phone databases or identities to manufacture cases.

Recorded emulator evidence belongs to the earlier `2EF834...` APK: 18/18 incident UI tests passed on Medium_Phone/API 37, followed by 4/4 focused cases at 200% text (Night/Daylight details, footer and actual filter/search/reset). Screenshots confirm the full selected-helper mesh status and replacement label. The rebuilt artifact has not been instrumented again. Physical steps, TalkBack, landscape, rotation and actual chat/history navigation remain UNTESTED. See [validation](../validation.md) for full identities, environment limits and remaining gates.

## Exact cases

| ID | Steps | Expected outcome |
| --- | --- | --- |
| UI-01 | B offers; A selects B; B confirms. Have B become unreachable; inspect A's details. Also exercise Checking/key-unavailable UI fixtures. | Commitment remains separate from route state; no unsupported radio/private-ready badges. |
| UI-02 | Inspect B/C offer cards visually and with TalkBack. | No hardware-verification claim; names, ownership and actual offer state remain available. |
| UI-03 | Create Critical, Serious and Moderate reports. Tap Critical only, clear, then Medical and advanced filters. Search known title/landmark/reporter/helper values; clear. Switch Active/My activity/History. Inspect quick chips with TalkBack. | Filtering matches labels; clear restores results; existing ordering/search/destination rules survive. Chips announce selection and provide at least 48dp targets; search has an accessible label. |
| UI-04 | A selects B; B confirms then withdraws; A freshly selects C. A resolves a separate confirmed incident or cancels another report. Use a fixture with withdrawn offer and stale RESPONDING projection. | Confirmation never implies travel; obsolete selection disappears; terminal details remain read-only with no active progress step. |
| UI-05 | In legacy fixtures, exercise non-reporter OPEN acknowledgement and assigned responder resolution before START. Exercise v2 role actions and busy/identity/terminal states. | Existing permitted actions remain reachable; unauthorized or repeated busy actions stay unavailable. |
| UI-06 | Use long reporter/helper names, long statuses and multiple offers. Inspect small portrait, landscape and 200% text in Night/Daylight. | Footer status/helper information remains readable; controls and chips wrap or remain accessible; no overlap or clipping. |
| UI-07 | Open create/details/offer views. Use system and toolbar Back, rotate with details open, dismiss keyboard, reopen and retry a failed save. Rename reporter and inspect old reports. | Predictable return; draft handling/confirmation works; actions remain reachable above keyboard/system bars; historical reporter name survives. |
| UI-08 | Complete report → two offers → select → confirm → withdraw → replacement → resolve/history on matching phones. | Existing workflow and local-versus-mesh feedback remain intact; no duplicate or unauthorized actions. Record synchronization separately. |
| UI-09 | Give B/C the same display name, collect both offers on A, then tap each helper and the selected-helper spotlight. Rename B and reopen its existing conversation. Use a fixture with a missing helper node ID; inspect reporter/self names. | Each shortcut opens the matching stable-ID chat/history, never another same-name peer. Missing-ID, reporter-name and self shortcuts are unavailable. A usable shortcut does not promise a route, trusted key or delivery. |

Check visible and announced selected states, 48dp accessible targets and contrast in both themes. Screenshots support layout review; they do not prove TalkBack, Back navigation or successful action execution.

## Failure evidence and reporting

Failures include false trust/readiness/movement claims, wrong-peer chat, hidden incident status, missing allowed actions, lost search/filter access, stale helper, unauthorized controls, lost draft, inaccessible controls or keyboard overlap.

For each case record PASS/FAIL/UNTESTED, candidate identity, roles/models/API, theme/font/orientation, exact steps, timestamp, expected/actual outcome and screenshot/capture path. Distinguish emulator fixture results, physical observations and unexecuted checks.

When workflow evidence is needed, inspect only the relevant window and existing `INCIDENT_IDENTITY`, `INCIDENT_HELP`, `INCIDENT_REPO`, `INCIDENT_SYNC`, READY and route markers. Do not add transport instrumentation or expose message contents, keys or sensitive location. Local build success does not close DELIVERY-01 or establish BLE reliability.
