# Claude Code notes for ResQMesh

@AGENTS.md

`AGENTS.md` (imported above) is the shared router for all tools. Do not duplicate it here.

Claude-specific notes:

- Model tiers: Haiku for routine inspection, Sonnet for standard work, Opus for difficult BLE/concurrency/crypto. Use the global Claude agents (explorer, planner, implementer, reviewer, auditor, researcher) with the same intent: smallest sufficient route, no push/PR without explicit request.
- The parent-folder `Downloads/AGENTS.md` is Codex-only and does not apply here.
- Never read `captures/` or `archive/` unless asked; for logs, grep markers in a bounded window.
