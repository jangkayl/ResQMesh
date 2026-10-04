# ResQMesh Codex guide

This file routes work. The current user request has priority. Source and relevant physical evidence outrank documentation; correct stale docs in the same authorized change.

## Project boundary

ResQMesh is an Android capstone and production-oriented prototype for offline text/private messaging, SOS and relays. Active transport is native BLE advertising/scanning, dual-role GATT and optional L2CAP payloads. Wi-Fi Direct and Nearby Connections are inactive.

Inspect Git status and relevant diffs before editing. Preserve unrelated tracked/untracked work.

## Read only what the task needs

| Task | Read |
| --- | --- |
| Current priority/new task | `docs/status.md` |
| BLE, routing, repository, security | `docs/architecture.md` plus status |
| Builds, tests, phones, Logcat, evidence | `docs/validation.md` |
| Features, architecture, scope | `docs/decisions.md` plus relevant architecture |
| Capstone claims/experiments | `docs/research.md` plus validation |
| Compose/status presentation | `docs/ui.md` plus affected screen/ViewModel |

Do not walk all docs for ordinary tasks. Read archives only for explicit historical/audit requests or one source referenced by an active document. Archived prompts never authorize implementation.

## Workflows

- Diagnose: bound the symptom, inspect source, separate observations/hypotheses and propose focused validation. Do not implement unless requested.
- Implement: smallest coherent change, proportional checks, affected canonical docs and a phone card for device behavior.
- Explore: compare a viable fix, structural alternative and operational option where relevant, considering reliability, compatibility, security and test cost. Do not implement speculative choices.
- Plan: small work stays in the task; medium work updates status. Only large multi-session work gets one `docs/plans/<slug>.md`, capped at 1,200 words and linked from status. Extract lasting knowledge before archival.

Continue the same task for the same unresolved outcome, including failed validation. New tasks require explicit direction; do not create them automatically.

## Model and pull-request policy

- Luna/low: routine inspection, summaries, docs checks and mechanical work.
- Terra/medium: implementation, debugging, planning and review.
- Sol/low: difficult BLE lifecycle, concurrency, routing, crypto or stubborn failures.
- Never GPT-6 Astra. Ask before changing the active model.
- Run deterministic checks before AI review; provide relevant diffs/tests/failure excerpts.
- Never create a PR or merge automatically. Report locally checked readiness and wait for explicit instruction.
- Failed local/CI checks remain blocking; AI explanation cannot override them.

## Physical-device boundary

Codex may edit source, run local checks/builds, generate APKs, inspect ADB availability, prepare procedures and analyze existing captures. The user installs, launches, stops, clears and operates physical phones unless explicitly requesting otherwise.

Device-facing changes end with a card: build identity, devices/setup, exact steps, expected outcomes, failure indicators, markers and report fields. Compilation is separate from executed instrumentation and device behavior.

## Log analysis

Search focused application markers and the reported window first. Reconstruct the relevant link/queue/heartbeat/key/route/delivery timeline. Expand to AndroidRuntime, process, permission or system Bluetooth logs only when needed.

Never load/summarize whole Logcat captures or reproduce private plaintext, ciphertext previews, credentials or key material.

## Documentation maintenance

- Status owns current work: actual failures and three to five next actions.
- Architecture owns as-built behavior/source navigation.
- Validation owns commands, procedures and concise bounded outcomes.
- Decisions owns accepted/materially rejected choices and deferred scope.
- Research owns methodology, measurements and claim limits.
- UI owns stable presentation rules.
- Test cards live in `docs/testing/`; inactive plans/cards go to a dated archive index with clear dispositions and repaired links.

Record user reports as such, with supplied build identity. Missing counts, devices, timings or transport coverage remain unspecified. Successful cases do not close an intermittent failure or automatically pass every card step.

Keep current, historical and deferred material distinct. Update status after relevant work; add meaningful physical results to validation. Preserve historical build identities. Update architecture/decisions for actual behavior/policy changes or factual corrections; avoid transcripts and competing context routers.

Run `powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1` after documentation changes and check diff hygiene.

## Reliability rules

- Separate radio CONNECTED, payload READY, indirect reachability, recent visibility and offline.
- A build is not BLE proof. Runtime closure needs source review, relevant checks and bounded physical evidence.
- Stable node identity is separate from endpoint addresses.
- Private messaging fails closed without trusted usable keys.
- Acceptance, hop custody, recipient arrival and receipt-confirmed delivery are distinct.
- Do not claim authenticated E2EE, forward secrecy, fixed range/capacity, guaranteed self-healing or production reliability without evidence.
- Keep text/SOS reliability ahead of speculative transports and large features.
