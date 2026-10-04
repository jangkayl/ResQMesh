# Capstone demo and deployment preparation

Last reviewed: 2026-10-04. Status: active preparation plan. Source baseline: `2e27013`. Originally prepared 2026-10-01; demo/pilot dates and completion are unconfirmed.

This checklist prepares evidence and installation; it does not authorize application or network changes.

## Current achievements and pending preparation

[Validation](../validation.md) identifies the current October 4 APK and qualitative user-reported four-phone text/receipt/note/SOS success, recovery/toggles/background, mutual blocking, full incident lifecycle/reconciliation, independent SOS cancellation and offline maps.

Intermittent A-to-D message/note loss remains DELIVERY-01, cause unknown. Models/API, repetitions, timings and transport/captures were not supplied. Functional success does not establish every exact test below, novice usability, deployment readiness or pilot completion.

- [ ] Map the approved project objectives to acceptance criteria and the assessment rubric.
- [ ] Keep the approved title; explain offline communication and incident coordination.
- [ ] Present report → offer → select → confirm → withdraw/replace → resolve/cancel.
- [ ] Distinguish local atomic Room transactions from eventual cross-phone convergence; no distributed global ACID claim.
- [ ] Map requirements to screens, authority rules, tests and bounded outcomes. Add scope only for a confirmed unmet requirement.

## Evidence package and preparation order

Use [architecture](../architecture.md), [status](../status.md), [research](../research.md) and validation as canonical references.

| Order | Exit evidence |
| --- | --- |
| Baseline | Approved objectives/rubric, chosen phones, installed APK identity and recorded local-check limitations |
| Critical scenarios | Repeated phone outcomes and focused failure records, including DELIVERY-01; fixes separately authorized |
| Trial/rehearsal | Supervised usability trial and complete demo story, with actual results rather than planned completion |
| Freeze | One candidate APK, known limitations, instructions, slides and clearly labeled recorded backup |
| Wider installation | Signing/upgrade/privacy/support gates and supported-device evidence |

No calendar deadline or completed trial is inferred.

- [ ] Architecture/state diagrams and requirements-to-test table: PASS/FAIL/UNTESTED.
- [ ] Separate builds/unit checks, executed emulator instrumentation, user reports and analyzed phone captures.
- [ ] Record phone/build matrix, timestamps, counts, route/transport and measured convergence/delays.
- [ ] Document trust, background/OEM, capacity and intermittent loss limits.
- [ ] User guide, installation/privacy explanation and issue-reporting contact.
- [ ] Accessibility/novice review: both themes, small/landscape screens, 200% text, keyboard, TalkBack, restoration and denied/error states.

The archived incident UX proposal's five-participant study (four-of-five uncoached task success, helper intent within five seconds, no destructive/false-delivery misunderstanding) remains unconfirmed. Keep it separate from the functional lifecycle report.

## Reporter/helper phone card

**Build:** use validation's current full APK hash/version/source baseline; record installed identity per phone and actual date. Preserve app data/keys. Users install and operate phones.

**Devices/setup:** A reporter, B helper, C alternate helper. Record models/API, permissions/restrictions, blocks and topology. Use matching APKs without internet and harmless emergencies. Verify indirect routes rather than inferring them from separation. Agree on and record a convergence limit before timed runs.

Repeat critical cases five times; the present qualitative report does not establish those counts.

| Case | Action | Expected result |
| --- | --- | --- |
| Pending withdrawal | B/C offer; A selects B; B withdraws | Selection clears everywhere; A can freshly select C |
| Confirmed withdrawal | B confirms then withdraws | Same cleanup, no stale confirmed card; history retained |
| Reconnect | Disconnect during selection/withdrawal, then restore | Replicas converge; later reporter decisions remain possible |
| Reporter rename | Rename before selection, while pending and after confirmation; restart | Stable ownership/My activity preserved; report-time name remains |
| Selected editing | Attempt selected B edit, then remove selection and retry | Selected edit rejected without revision change; unselected edit allowed |
| Offline revision | B edits before learning selection; reconnect | Obsolete selection invalidates; fresh approval required |
| Identity isolation | C uses A's display name and attempts owner actions | No reporter authority granted |
| Lifecycle | Withdraw/re-offer/reselect; resolve/cancel; replay | Fresh offers work; terminal history rejects active changes |

Basic lifecycle/recovery was reported working. Rename, unauthorized identity, exact editing/replay, timed repetitions and other unlisted cases remain unconfirmed. Use the [physical checklist](../testing/physical-reliability-tests.md) and [SOS card](../testing/sos-conversations-test-card.md) for transport, independent alerts and background/upgrade procedures.

Failures: stale helper, missing owner controls, unauthorized/rejected valid action, stuck pending, false delivery/route state, lost history or manual reset. Report scenario/repetition, APK, models/API/topology, timestamps, expected/actual outcome, convergence and focused capture. Mark INCIDENT_IDENTITY/HELP/REPO/SYNC, READY, route/receipt/queue events without private contents/keys.

## Demo and release gates

- [ ] Rehearse offline report → two offers → select/confirm → withdraw → replace → resolve/history.
- [ ] Show rename continuity/edit locking only after checking those exact cases.
- [ ] Explain measured reconnect results and the intermittent-loss limitation.
- [ ] Prepare charged phones/cables, frozen APK, instructions, slides and recorded backup.
- [ ] Conduct and record a supervised pilot after critical scenarios pass.
- [ ] Before wider beta: signed release, upgrade tests, device/API support, permissions/privacy/support and outstanding reliability/security issues.

Describe installation as prototype/beta, without operational emergency, authenticated E2EE or guaranteed range/capacity/delivery claims. Archive this plan after completed preparation, extracting lasting evidence/decisions.
