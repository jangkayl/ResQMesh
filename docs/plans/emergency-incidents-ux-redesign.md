# Emergency Incidents UX redesign and Antigravity handover

Approved: 2026-10-01. Status: implemented and locally checked; usability/device validation pending. Baseline observations below describe the pre-redesign checkout.

## Objective and boundaries

Help nontechnical users understand the request, recognize helpers, and find their next action. Approved choices: saved title, existing helper offers rather than general comments, and one detail page.

Cover incident listing, reporting, details, offers, selection, confirmation, closure, history, and errors. Preserve other app areas, BLE/routing, authorization, signing, and workflow-v1 compatibility. No comments, automatic reassignment, multiple confirmed assignments, or verified-responder claims.

The pre-redesign source used type/area headings and Room 8. The implementation now persists titles with Room 9 and includes presentation/tests/schema files. Physical validation remains open; consult canonical status for current evidence.

## Screen specification

### List

Header: “Emergency incidents.” Subtitle: “See requests and offer help.”

Destinations: Active (default, unresolved); My activity (active reports, active offers, or selections involving me); History (resolved/cancelled).

Search title, description, landmark, reporter/helper names; retain identifier search internally. Replace long filter strips with a sheet: type, urgency, assistance (Any / Looking for help / No offers yet). Apply commits draft filters; dismissal preserves previous filters; Clear resets. Show applied count. History clears active-only assistance filters.

Cards show urgency/status, prominent title, landmark, two-line description, helper summary, reporter/time, and “View incident.” Summaries: “No offers yet,” “2 people offered help,” or “Miguel confirmed.” Include “You offered help.” Cards only open details. Keep “Report incident” visible.

Active/My activity: urgency descending, latest update, stable ID. History: latest update, stable ID. Distinguish empty destinations from filtered results; offer Clear filters. Say “on this phone,” avoiding claims about emergencies elsewhere.

### Report

Full-screen form; Back; docked “Report incident.” Order: Title, Description, Place or landmark, Emergency type, How urgent is it?, Attach my location.

Title: trimmed, required, 1–80 characters. Description/landmark optional. Type: labelled dropdown, existing six categories, no default. Urgency: Moderate—help needed; Serious—help urgently needed; Critical—immediate danger. Require explicit selection; these are descriptions, not medical triage instructions.

Example title: “Help moving an injured person.” Description prompt: “Describe what happened and the help needed.”

Acquire location only on explicit attachment. Permission/fix failures never block reporting. Show attached/remove/retry controls; coordinates, accuracy/time under Location details. Location remains a snapshot.

Submit reveals inline errors and focuses the first invalid field. Disable duplicate submissions; show “Saving…”. Preserve drafts on failure/restoration. Success opens details: “Saved on this phone. Sharing depends on available mesh connections.” Unsaved Back: Keep editing / Discard draft.

### Details and activity

One page: Back/More actions; title/urgency/status; next-step sentence; full description/place/map link; selected-helper summary; People offering help; reporter/update; View activity; docked primary action.

Helpers expanded by default: first three inline, Show all offers expands the rest. Review helpers scrolls/focuses the heading. No empty optional panels. Map/activity return restores incident/scroll.

Activity is read-only, chronological, applied events only: “Ana reported this incident,” “Miguel offered help,” “Ana selected Miguel,” “Miguel confirmed,” “Ana marked the incident resolved.” Hide IDs, revisions, signatures, and rejected diagnostics.

### Helpers and offers

Cards: name (“You” locally), intent/state, full note, relative time, permitted action. Sort selected first, mine second, latest update/offer ID thereafter. Duplicate names remain distinct; selection dialog includes offer note.

Labels: Wants to help; Selected · Waiting for confirmation; Confirmed helper; Selection needs review; Previously selected/confirmed helper. Remove node IDs, revision counters, signed badges, capability parsing, and decorative connection dots. Section note: “Helpers describe their own skills and availability.”

Offer help opens a sheet with labelled Your help offer, example, required trimmed 1–240-character note, Save help offer. Explain reporter selection still requires helper confirmation. Success closes, shows local offer/feedback, changes action to Edit my offer. Failure retains draft/error. Editing preserves existing offer identity/revisions. Additional offers remain allowed during another selection without implying additional assignments.

### Actions and uncertainty

| Viewer/state | Primary | Secondary |
| --- | --- | --- |
| Visitor | Offer help | — |
| Offer owner | Edit my offer | Withdraw |
| Reporter, offers available | Review helpers | Cancel |
| Reporter, no offers | Waiting explanation | Cancel |
| Selected helper, awaiting | Confirm I can help | I can’t help; Withdraw |
| Reporter, awaiting | Review selected helper | Remove selection; Cancel |
| Confirmed helper | Confirmed explanation | Withdraw |
| Reporter, valid confirmed helper | Mark resolved | Remove selection; Cancel |
| Terminal | None | Read-only |

Confirm choosing, revoking, withdrawing, resolving, cancelling. “Choose Miguel?” explains confirmation remains necessary. Recheck authority/latest state during execution. Retain legacy acknowledge/volunteer/start/release/closure according to existing policy.

Separate selected-helper connection from commitment: direct, through another phone, checking, unavailable, this phone. Use existing readiness/routes, never generic “Mesh.” Disconnection never reassigns or removes confirmation.

## Compose and data contracts

Reuse Material 3/theme tokens: orange actions, red critical/destructive, neutral surfaces, existing shapes/spacing/motion. Incident-local typography: titles 24sp, cards 18sp, body/forms 16sp, secondary 14sp. Minimum targets 48dp; primary buttons 56dp minimum, expandable. No global theme rewrite, emojis, pulsing panels, or celebrations. Content maximum 680dp; support small phones/landscape/200% text. Respect IME/system insets, TalkBack headings/selection/focus, both themes.

Centralize presentation/actions/involvement/warnings; keep business authority in repositories. Route collects state; stateless components receive models/callbacks. Clear stale events/offers on incident switches. Tie drafts/results to incident IDs; explicit operation results control form closure. Save small drafts/navigation/filter/scroll values, not entity lists.

Append defaulted title to entity; Room 8→9 adds title TEXT NOT NULL DEFAULT ''. Register migration; inspect existing schema-9 before regenerating. Extend creation form/ViewModel/repository; legacy internal callers may omit title. Put title in creation JSON before signing; preserve raw payload for verification/relay. Receive missing/null/blank as legacy; reject nontext/over-80 supplied titles. Verify original signatures before projection. Never rewrite historical events. Display fallback: saved title, type/landmark, type emergency. Include title search; no title editing.

## Rationale, implementation, acceptance

Use-case hypotheses: familiar fields reduce reporting confusion; title/landmark aid scanning; named offers expose intent; confirmation CTA separates selection/commitment; separate connection avoids false certainty; secondary history reduces clutter. These are not completed case studies. References: [progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [state saving](https://developer.android.com/develop/ui/compose/state-saving).

Antigravity: read AGENTS/status/UI/architecture/decisions/validation as relevant; preserve unrelated changes. Reconcile title/schema first; presentation second; screens third; restoration/accessibility fourth; validation/docs last. Use ui-ux-pro-max with jetpack-compose guidance; no dependency upgrade. Update canonical docs only as behavior becomes implemented. No commit/push/PR without instruction.

Run debug assembly/unit tests/Android-test compilation, lintDebug, scripts/check_docs.ps1, git diff --check. Cover migration, signed title replay/tampering/legacy, involvement, stale selections, unauthorized/terminal/legacy actions, duplicate taps, failures/restoration/navigation. Capture both themes at 360/412dp, landscape, 200% text, keyboard, TalkBack, disabled animations.

Five novice participants: report, offer, identify helper intent, distinguish selected/confirmed, cancel. Target four-of-five uncoached; identify intent within five seconds; zero accidental destruction/false delivery interpretation. Record outcomes; no validated-usability claim beforehand.

User phone card: record APK version/hash/build, A reporter/B–C helpers/models/API/topology. Report; both offer; select B; disconnect before confirmation; reconnect/confirm/disconnect; revoke/select C; reject stale B confirmation; C confirms/A resolves; reconnect and compare titles/offers/activity. Include relay, cancellation, denied location, failed drafts, large text. Expect preserved decisions/drafts without false delivery. Report timestamp/step/screenshots and focused INCIDENT_REPO/INCIDENT_CREATED/sync/READY/route evidence, excluding private contents/keys. Local checks do not close physical convergence.
