# Claude Code notes for ResQMesh

@AGENTS.md

`AGENTS.md` (imported above) is the canonical router: read-first table, workflows, reliability and physical-device rules. Do not duplicate it here.

Claude-specific adjustments:

- Model/effort tables and `fork_turns` rules in parent-folder `Downloads/AGENTS.md` are Codex-only. Use the Claude agents from `~/.claude/CLAUDE.md` (explorer, planner, implementer, reviewer, auditor, researcher) with the same intent: smallest sufficient route, no push/PR without explicit request.
- Start from `docs/README.md` (one-line "read when" index). For ordinary tasks read only the one or two docs it names.
- Never read `captures/` or `archive/` unless asked; for logs, grep markers in a bounded window.
- Baseline, open issues and next actions live only in `docs/status.md`; build hashes and test counts only in `docs/validation.md`. Link to them instead of copying.
