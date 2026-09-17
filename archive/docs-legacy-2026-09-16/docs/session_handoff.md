# Starting a new repair session

Use this file when a repair phase needs a fresh Codex conversation. A new conversation resets chat context, while this repository documentation carries the task decisions. The previous conversation remains available for reference. A fork copies its transcript and is useful when exact conversational detail matters, but it also carries that history into the new context.

## Before starting

- Check that `docs/README.md` and `docs/ble_repair_pipeline.md` are present in the checkout you will use.
- Review `git status --short --untracked-files=all`. The documentation and pre-existing Android edits were uncommitted at the 2026-09-15 handoff; their state may have changed since then.
- For a new **Local** task, select this project's current checkout so it can read its working-tree files.
- Keep the repair sessions **Local** in this checkout. `docs/` is intentionally Git-ignored and stays only on this computer; a new Git worktree will not automatically contain these files.

## Reusable opening prompt

Copy this into the first message of the new task, replacing `[PHASE]` with the phase number:

> This is the ResQMesh BLE repair task. Read `docs/README.md`, `docs/session_handoff.md`, and `docs/ble_repair_pipeline.md`. Inspect the current Git branch, status, and diff before changing files. Preserve and evaluate existing uncommitted Android edits. Begin with Phase `[PHASE]` of the checklist, but verify prerequisites and the current implementation first. Distinguish static-review hypotheses from failures reproduced on devices. Implement only the selected phase and its necessary prerequisites, run appropriate build/tests, and report what changed, what evidence supports it, and what device validation remains. Update the checklist with completed items and evidence before handing off to another session.

For the first implementation conversation, set `[PHASE]` to `0`. Later sessions should include a short note of the last completed phase, commit, test results, and outstanding device observations.

## Phase-end handoff note

At the end of each phase, write a short note in the repair checklist or a dated file under `docs/` with:

- Exact branch/commit and whether the working tree is clean.
- Changes made and checklist items completed.
- Build/test results and device models, Android versions, reproduction steps, and logs when available.
- Remaining failures, uncertainty, and the next phase's first action.

This is a manual handoff workflow, not a scheduled automation. A recurring automation is unnecessary for starting an on-demand repair conversation.
