# Antigravity handover: incident UI/UX repairs

Prepared: 2026-10-04. Updated: 2026-10-05. Status: original six repairs and three follow-up UI fixes implemented; final readiness remains subject to the checks in [validation](../validation.md).
Inspected checkout at audit: `docs/sync-project-context`, HEAD `2c2b9fc`, plus uncommitted UI changes. The reviewed repair change set is now recorded on `fix/incident-ui-ux` at the user's subsequent request. The instructions below preserve the original audit context. Reinspect status/diffs before further editing; archived redesign proposals do not expand scope.

## Outcome and boundaries

Repair the audited presentation regressions while preserving Antigravity's cards, category icons, urgency accents, full-page forms/details and help-offer sheet. Restore existing functionality without redesigning the app again.

Read [AGENTS](../../AGENTS.md), [status](../status.md), [UI guidance](../ui.md), [validation](../validation.md), and affected composables. Inspect the incident ViewModel, presentation mapper and policies **read-only** to understand existing state and callbacks.

Production edits belong in `feature/incident/ui/`: `IncidentListScreen.kt` and components `CreateIncidentSheet.kt`, `IncidentDetailSheet.kt`, `TacticalHelperOfferCard.kt`, `TacticalIncidentCard.kt`, `TacticalHelpOfferSheet.kt`, and the existing `IncidentFilterSheet.kt` if needed. Preserve the Home spacing change unless focused layout checks show a problem. A reproduced Back-order defect may justify a minimal presentation-only edit in `app/navigation/MainContainerScreen.kt`.

Preserve `core/network`, `data/repository`, services, domain/use cases, shared models, crypto/signing, Room entities/schema/migrations, DI, build configuration, transport settings and ViewModel/policy behavior. No BLE, routing, delivery, key-trust, helper-authority or synchronization changes. Do not add attestation, live PTT, movement tracking or new capabilities to justify UI wording. DELIVERY-01 remains deferred.

Preserve unrelated tracked/untracked files, including existing network helpers/tests, backups, device scripts and Figma tools. The untracked `TacticalHelpOfferSheet.kt` is part of this UI change and must be retained. Preserve the existing README edit for eventual commit review.

## Repair order and acceptance

### 1. P1: unsupported readiness claims

[IncidentDetailSheet](../../app/src/main/java/com/example/testresqmesh/feature/incident/ui/components/IncidentDetailSheet.kt), current lines 706/724, always renders “Walkie-Talkie Ready” and “Private Chat Ready” for a selected helper.

Preferred fix: remove both badges and retain the existing route description. Helper commitment, usable route and trusted private key are separate states; route reachability alone cannot establish private-send readiness. Do not introduce new backend plumbing for these badges.

**Accept:** an unavailable/checking helper has no ready claim; selecting or confirming a helper never independently implies communications readiness. Existing route labels remain accurate.

### 2. P2: unsupported hardware verification

[TacticalHelperOfferCard](../../app/src/main/java/com/example/testresqmesh/feature/incident/ui/components/TacticalHelperOfferCard.kt), line 116, unconditionally announces “Verified Hardware Identity”. Existing signing establishes signature/key continuity, not remote hardware attestation or a verified person.

Remove the verification ornament and its accessibility claim. Retain avatar, name, ownership and offer status. Avoid replacing it with another unconditional trust badge.

**Accept:** visible UI and TalkBack make no unsupported hardware, credentials or trust claims.

### 3. P2: filter wording and lost discovery controls

[IncidentListScreen](../../app/src/main/java/com/example/testresqmesh/feature/incident/ui/IncidentListScreen.kt), lines 255–260, applies an exact Critical urgency filter. Rename “Critical First” to **“Critical only”**; existing Active/My activity sorting already prioritizes urgency. Do not change sorting/filter policy.

Restore search and an advanced-filter entry using the existing `searchQuery`, `setSearchQuery`, filter state/callbacks and [IncidentFilterSheet](../../app/src/main/java/com/example/testresqmesh/feature/incident/ui/components/IncidentFilterSheet.kt). Keep quick chips. Make active query/filter state visible; clearing returns the destination to its unfiltered contents. Preserve existing History assistance-filter rules and search fields.

**Accept:** critical-only filtering visibly excludes other urgencies; clearing restores them. Users can search title/description/landmark/reporter/helper and choose every existing category, urgency and assistance filter. No hidden query produces a misleading empty state.

### 4. P2: progress must follow normalized presentation

`IncidentDetailSheet.kt`, lines 481–578, infers “En Route” from confirmation and reads raw fields that can remain stale during separate Room emissions.

Use existing normalized `presentation` state for displayed status/selection. For workflow v2, say “Helper confirmed”, never infer travel; legacy response wording remains distinct. A withdrawn/superseded selection must not retain an active assigned/confirmed stage. Resolved/cancelled views must not highlight an active next step. If the five-stage visualization cannot honestly represent these states, remove it and retain status plus next-step guidance.

**Accept:** confirmation means commitment only; withdrawn selection, stale projection and terminal fixtures display the same truth as the existing presentation mapper.

### 5. P2: preserve every permitted secondary action

`IncidentDetailSheet.kt`, lines 926–974, replaces the previous menu with incomplete action rendering. Legacy OPEN incidents lose secondary ACKNOWLEDGE; an assigned responder loses secondary RESOLVE while START is primary.

Render every existing `presentation.secondary` action through its existing callback/confirmation path, using a compact accessible menu or wrapping controls. Avoid duplicating primary actions. Respect busy/terminal/identity state and existing authority; do not invent permission rules from raw incident fields.

**Accept:** legacy acknowledgement and assigned-state resolution remain usable; v2 offer/select/confirm/decline/withdraw/revoke/resolve/cancel controls retain their existing authority and safeguards.

### 6. P2: footer layout

[TacticalIncidentCard](../../app/src/main/java/com/example/testresqmesh/feature/incident/ui/components/TacticalIncidentCard.kt), lines 331–401, removes the previous summary-width constraint.

Bound the helper summary, allow meaningful labels to wrap, and stack footer items when space is insufficient. Keep incident status visible alongside long helper names and offer counts. Avoid fixed heights that clip enlarged text.

**Accept:** small portrait/landscape screens and 200% text preserve status and helper information without overlap or inaccessible overflow.

## Validation and delivery

The October 5 follow-up qualifies helper chat callbacks using `helperNodeId` and existing `NodeIdentity.compose`, including the selected-helper spotlight. Self/missing-ID shortcuts and the reporter-name shortcut are unavailable: reports contain no stable reporter mesh ID. Quick chips expose selected semantics with 48dp targets; search has an accessible label. Large-text screenshot review also found clipped helper-replacement text and truncated mesh status: the control now grows from a 48dp minimum and status wraps. Instrumentation uses current labels and exercises real filtering/search/reset, stable-ID and missing-ID behavior, plus replacement-control height and status text overflow. No transport, ViewModel, policy or schema changes were made.

Follow the [test card](../testing/incident-ui-ux-repair-test-card.md). Back/rotation, draft retention, keyboard insets, both themes and TalkBack are validation risks, not previously executed failures. Preserve report-time reporter names alongside ownership labels.

Update the existing `IncidentUiTest.kt` for actual page structure/labels; remove obsolete `isDialog()` screenshot targeting, without deleting behavioral assertions. Add focused coverage for the six regressions, restored discovery controls and legacy actions. Prefer interaction/state assertions over screenshots alone.

Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:lintDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
powershell -ExecutionPolicy Bypass -File .\scripts\check_docs.ps1
git diff --check
```

Run updated UI instrumentation on an available emulator; physical phones remain user-operated. Report compilation and execution separately. The reconnect fixture blocked unit compilation at the audit. The user subsequently authorized a separate TEST-01 fixture repair, recorded in [validation](../validation.md); do not silently exclude tests to claim success.

Record before/after hashes for protected files and unrelated work using a separate scope-aware manifest. The old structure baseline has stale archived-document/tool entries: disclose them, preserve that baseline and verify protected content independently.

Update canonical status/UI/validation only with actual changes/results. Deliver changed-file inventory, checks/failure excerpts, screenshots for both themes/large text, APK version/hash and PASS/FAIL/UNTESTED card. Failed checks remain blocking for readiness. The user later authorized a focused local conventional commit including README; unrelated work is excluded. Push, PR and merge remain separately authorized actions.
