> Historical version preserved 2026-10-04. Original deadlines, unchecked procedures, hashes and instructions below retain their prior scope; they do not describe current completion or authorize work. See the [archive index](../README.md) for the active replacement.

# Capstone demo and deployment preparation

Prepared: 2026-10-01. Target: final demo in less than two weeks, followed by a supervised pilot and possible public beta installation.

This is a preparation checklist, not an instruction to change application code. Keep existing features and approved scope. Record failures before deciding on fixes.

## Position the project clearly

ResQMesh has sufficient feature scope for a software engineering capstone: offline communication, routing, persistence, access control, and emergency incident coordination. More features are not the current priority. Preparation follows the approved project objectives and recorded acceptance criteria.

- [ ] Map the approved project objectives to acceptance criteria.
- [ ] Keep the approved title; explain the system as offline emergency communication and incident coordination.
- [ ] Present the incident business workflow: report → offer → select → confirm → withdraw or complete → resolve/cancel.
- [ ] Distinguish local atomic Room transactions from eventual synchronization between disconnected phones. Do not claim distributed global ACID transactions.
- [ ] Map each requirement to a screen, authorization rule, test, and recorded outcome.
- [ ] Add scope only if a specific assessment requirement remains unmet; avoid speculative features.

Software engineering includes requirements, design, construction, testing, and quality, beyond financial transactions. Reference: [IEEE SWEBOK topics](https://www.computer.org/education/bodies-of-knowledge/software-engineering/topics).

## Prepare the evidence package

Use [current status](../../../docs/status.md), [architecture](../../../docs/architecture.md), [validation](../../../docs/validation.md), and [research claim limits](../../../docs/research.md) as canonical references. This checklist does not replace their technical procedures.

- [ ] Approved objectives, stakeholders, functional requirements, and acceptance criteria.
- [ ] Architecture and incident state-transition diagrams; explain BLE, relay, Room persistence, and signed workflow events.
- [ ] Requirements-to-test table with passed, failed, and untested outcomes.
- [ ] Local check results: unit tests, debug build, Android-test compilation, Lint, and documentation checks.
- [ ] Separate executed instrumentation results from compilation-only results.
- [ ] Physical evidence with build identity, device matrix, timestamps, repetitions, and measured delays.
- [ ] Known limitations: transport availability, trust assumptions, background behavior, capacity, and remaining failures.
- [ ] User guide, installation instructions, privacy explanation, and issue-reporting contact.

Existing documentation reports local checks but leaves physical incident convergence and other runtime checks open. Do not mark these complete based on a build or test count.

## Prioritize the remaining time

| Window | Preparation and exit evidence |
| --- | --- |
| Days 1–2 | Clarify rubric; freeze major features; select phones; record baseline checks and installed APK identity. |
| Days 3–7 | Run critical phone scenarios; document failures; arrange focused fixes separately if needed; repeat affected tests. |
| Days 8–10 | Conduct supervised trial; collect usability feedback; rehearse the complete demo. |
| Remaining days | Freeze one candidate APK; prepare slides, evidence, recorded backup, and connection-failure recovery steps. |

Prioritize text/SOS delivery and honest status, then incident correctness, identity permissions, restart/migration/background behavior, and task-blocking UI issues. Five- and ten-phone capacity claims require their own measured matrix; a three-phone demo does not establish them.

## Physical phone test card

**Build:** record version name/code, APK filename and SHA-256, source revision plus uncommitted-change state, and installation date. Recalculate identity for each candidate; do not reuse an old hash.

**Devices:** A = reporter, B = helper, C = alternative helper. Record model, Android version, permissions, battery restrictions, and observed GATT/L2CAP use.

**Setup:** same APK on all phones; Bluetooth enabled; internet unavailable; avoid real emergency recipients. Verify actual READY links. A relay test needs a verified indirect path, not merely physical separation. Agree on a maximum convergence time before testing and record it.

Repeat critical scenarios five times. Record an unsupported or unavailable condition as untested.

| Test | Exact action | Expected result |
| --- | --- | --- |
| Pending withdrawal | A reports; B/C offer; A selects B; B withdraws. | B selection disappears everywhere; incident returns to Looking for help; A can select C. |
| Confirmed withdrawal | Repeat, with B confirming before withdrawal. | Same cleanup; no stale confirmed-helper card; withdrawal remains in history. |
| Reconnect | Disconnect a phone during selection/withdrawal; reconnect. | Replicas converge within the agreed limit; later reporter decisions remain possible. |
| Reporter rename | Rename A before selection, while pending, and after confirmation; navigate and restart. | Reporter actions and My involvement remain; original report-time name persists. |
| Edit policy | Attempt editing B's selected offer; remove selection and try again. | Selected edit blocked without revision change; active unselected edit allowed. |
| Offline revision | B misses selection and edits its offer offline; reconnect. | Obsolete selection invalidates; fresh reporter selection is required. |
| Identity isolation | Give C the same display name as A; attempt reporter actions. | C gains no reporter authorization. |
| Lifecycle | Withdraw, re-offer, reselect; then resolve/cancel. | Fresh offers work; terminal incidents retain history and reject active changes. |

Also test direct public/private text, verified relay delivery, SOS cancellation ownership, duplicate delivery, app restart, and relevant background/upgrade cases from validation documentation.

**Failure indicators:** stale helper display, missing reporter buttons, rejected valid selection, unauthorized action, stuck pending message, false delivery/relay status, lost history, or manual reset required.

**Logs:** capture focused windows around `IDENTITY`, `INCIDENT_IDENTITY`, `INCIDENT_HELP`, `INCIDENT_REPO`, `READY`, routing, queue rejection, and retirement markers as applicable. Do not include private message contents or key material.

**Report template:** scenario; repetition; APK identity; devices; start/end timestamps; expected/actual result; convergence time; screenshot or focused capture reference; recovery needed; pass/fail/untested.

## Final demo and installation gates

- [ ] Rehearse one story: offline report → two offers → selection → confirmation → withdrawal → replacement → resolution/history.
- [ ] Show reporter rename continuity and helper edit locking as correctness safeguards.
- [ ] Explain one reconnect scenario with measured results and limitations.
- [ ] Prepare charged phones, cables, the frozen APK, installation guide, slides, and clearly labeled recorded backup.
- [ ] Conduct a supervised pilot only after critical scenarios pass; gather task-completion feedback.
- [ ] Before wider beta installation: prepare a signed release, test upgrades, document supported devices/Android versions, permissions, privacy, support, and known limitations; resolve blocking reliability/security issues.

Public installation does not imply readiness for operational emergency dependence. Describe the release as a prototype/beta; do not claim guaranteed delivery, range, capacity, self-healing, authenticated E2EE, or production reliability without corresponding evidence.

Archive this preparation plan after the demo/pilot, extracting lasting results and decisions into canonical documentation.
