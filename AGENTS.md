# ResQMesh Codex guide

This file routes work; it should stay short. The user's current request has priority over repository guidance. Source code and relevant physical-device evidence outrank documentation when they disagree. Correct stale documentation in the same change.

## Project boundary

ResQMesh is an Android capstone and production-oriented prototype for offline text, private messaging, SOS, and relay communication. The active transport is native BLE advertising/scanning with GATT client/server roles and an optional L2CAP payload path. Wi-Fi Direct and Google Nearby Connections are not active transports.

The working tree may contain important uncommitted Android changes. Inspect `git status` and the relevant diff before editing; never discard or overwrite unrelated work.

## Read only what the task needs

| Task | Read |
| --- | --- |
| Current priority or new task | `docs/status.md` |
| BLE, GATT, L2CAP, routing, repository, security | `docs/architecture.md` plus `docs/status.md` |
| Build, tests, phones, Logcat, completion evidence | `docs/validation.md` |
| New feature, architectural choice, or scope question | `docs/decisions.md` plus relevant architecture |
| Capstone claims, metrics, or experiments | `docs/research.md` plus `docs/validation.md` |
| Compose, Radar, chat, or status presentation | `docs/ui.md` plus the actual screen/ViewModel |

Do not walk all documentation. Do not read `archive/` unless the user explicitly asks for historical context or an active document points to one exact archived source for investigation.

## Workflows

- Diagnose: reproduce or bound the symptom, inspect current source, distinguish observations from hypotheses, and propose focused validation. Do not implement unless requested.
- Implement: make the smallest coherent change, run checks proportional to risk, update affected canonical docs, and give the user a physical test card when device behavior is involved.
- Explore: consider the smallest viable fix, a structural alternative, and a non-code or operational alternative when relevant. Compare reliability, Android compatibility, security, complexity, and test cost. Do not implement speculative options without user selection.
- Plan: small work stays in the task; medium work updates `docs/status.md`; only large multi-session work may create one `docs/plans/<slug>.md` capped at 1,200 words and linked from status. Archive or remove the plan after extracting lasting decisions.

Continue the same Codex task for the same unresolved outcome, including follow-up captures and failed validation. Start a new task for an unrelated outcome, a clean second opinion, parallel work, or implementation after a long approved planning phase. New tasks are not created automatically.

## Model and pull-request policy

- Use Luna with low reasoning for routine inspection, concise test summaries, documentation checks, and simple mechanical work.
- Use Terra with medium reasoning by default for implementation, debugging, planning, and code review.
- Use Sol with low reasoning only for difficult BLE lifecycle, concurrency, routing, cryptography, or stubborn failure analysis.
- Never use GPT-6 Astra for this repository. Ask the user before changing the active model.
- Run deterministic checks before deeper AI review. Give reviewers only relevant diffs, affected tests, and focused failure excerpts.
- Never create a pull request or merge automatically. After local checks pass, report readiness and wait for the user's explicit instruction to create the PR.
- A failed local or CI check remains blocking; an AI explanation cannot override it.

## Physical-device boundary

Codex may edit source, run local builds/tests, generate an APK, inspect ADB availability, prepare test steps, and analyze existing captures. The user installs, launches, stops, clears, and operates apps on physical phones unless they explicitly request otherwise for the current task.

Every device-facing change ends with a focused test card: build identity, devices, setup, exact steps, expected results, failure indicators, important log markers, and what the user should report.

## Log analysis

Search focused application markers and the reported time window first. Reconstruct only the relevant link, queue, heartbeat, key, route, or delivery timeline. Expand to `AndroidRuntime`, process lifecycle, permissions, or system Bluetooth/GATT logs only when application markers are insufficient. Never load or summarize an entire Logcat capture, and never reproduce private plaintext, ciphertext previews, credentials, or key material.

## Documentation maintenance

- `docs/status.md` owns current work; keep only active blockers and the next three to five actions.
- `docs/architecture.md` owns as-built behavior and source navigation.
- `docs/validation.md` owns commands, test procedures, and concise milestone results.
- `docs/decisions.md` owns accepted or materially important rejected decisions.
- `docs/research.md` owns capstone questions, methodology, measurements, and claim limits.
- `docs/ui.md` owns stable UI rules.

Update status after relevant work. Add a validation result only for a meaningful device run. Update architecture or decisions only when behavior or policy actually changes. Do not append session transcripts. Keep canonical pages concise and run `powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1` after documentation changes.

## Reliability rules

- Treat radio `CONNECTED`, payload `READY`, indirect reachability, recent visibility, and offline state as different facts.
- A passing build is not BLE proof. Close a runtime issue only with appropriate source review, focused tests, and user-run physical validation.
- Use stable node identity separately from BLE endpoint addresses.
- Private messages fail closed when a trusted usable recipient key is unavailable.
- Do not claim authenticated E2EE, forward secrecy, fixed range, fixed capacity, guaranteed self-healing, or production reliability without evidence.
- Keep text/SOS reliability ahead of speculative transports and large features.
