# Maintainability size and ownership ledger

Last reviewed: 2026-10-06. Baseline: `db2260e` plus the preserved uncommitted initial refactor; 478-file snapshot in `app/build/maintainability-phases/2026-10-05-200635/baseline.json`.

Implementation inventory: all 21 original oversized files have a disposition; final F1–F14 phone comparison is user-reported PASS (October 6). Four baseline automated navigation checks remain unresolved. Source paths below are relative to `app/src/main/java/com/example/testresqmesh/`. Aim for 200–400 lines per substantial owner; every file above 500 needs an explicit disposition. NativeBleManager is the single reviewed exception described below; no new collaborator exceeds 500 lines.

## Original oversized files

| Source | Baseline lines | Current lines | Disposition |
| --- | ---: | ---: | --- |
| `feature/incident/ui/components/IncidentDetailSheet.kt` | 1477 | 426 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `core/network/NativeBleManager.kt` | 1384 | 743 | Reviewed compatibility/construction exception; focused owners extracted |
| `feature/sos/ui/SosMapScreen.kt` | 1296 | 106 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `data/repository/MeshRepository.kt` | 1173 | 368 | Phase 5 shared-flow facade; peer/delivery/inbound/block/media owners extracted |
| `data/repository/IncidentRepository.kt` | 1000 | 288 | Phase 6 commands/ingestion/projection/sync/codec extracted; five Room cases pass |
| `feature/sos/ui/SOSBroadcastScreen.kt` | 792 | 406 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/incident/ui/IncidentListScreen.kt` | 777 | 477 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/profile/ui/OfflineMapSettingsScreen.kt` | 763 | 146 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/incident/ui/components/CreateIncidentSheet.kt` | 741 | 460 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `core/network/bluetooth/BlePeerAdmissionController.kt` | 725 | 158 | Phase 4 advertisement/bootstrap/diagnostics and one session extracted |
| `feature/comms/ui/components/ChatInput.kt` | 671 | 486 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/sos/ui/SosHubScreen.kt` | 619 | 471 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/profile/ui/ProfileScreen.kt` | 613 | 445 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/comms/ui/ActiveChatScreen.kt` | 612 | 288 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/comms/ui/WalkieTalkieScreen.kt` | 580 | 401 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/sos/ui/ActiveSOSMonitoringScreen.kt` | 576 | 283 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `core/network/bluetooth/gatt/GattClientManager.kt` | 568 | 133 | Phase 3a client entry point; attempt/callback/setup extracted |
| `feature/incident/ui/components/IncidentCoordinationHub.kt` | 558 | 427 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `feature/incident/ui/IncidentDialogs.kt` | 546 | 42 | Phase 7 sections extracted; original state/effect lifetimes retained |
| `core/network/bluetooth/gatt/GattServerManager.kt` | 519 | 44 | Phase 3b registration/callback/link/attribute/L2CAP owners extracted |
| `app/navigation/MainContainerScreen.kt` | 515 | 430 | Phase 7 sections extracted; original state/effect lifetimes retained |

## Phase 1 owners

| Owner | Lines | State/resource ownership |
| --- | ---: | --- |
| NativeInboundPipeline | 125 | Original ingress checks and dispatch; live generation/identity/listeners, shared store and lazy journal provider; original posts only |
| NativeOutboundDispatcher | 225 | Outbound selection, legacy-frame map and retained fallback; original shared GATT coordinator, L2CAP send capability and main handler |
| OutboundFrameReporter | 47 | Original progress decode/event ordering; current listener, exact captured relay-frame removal and lazy journal provider |

Two now-unused private facade delegates are removed; their method bodies remain private inside the outbound owner. Existing public facade APIs remain. Later owners are implemented as recorded below; phase APKs and evidence remain in the local ledger.

## Historical baseline reconciliation

The original presentation baseline is preserved, not recaptured or replaced. Its fifteen distinct differing baseline entries (sixteen category entries because the fixture appears twice) are explained:

- Six production networking files differ because of the already-authorized initial refactor. Before Phase 1, the historical networking checker passed: twelve handler bodies, 91 orchestration bodies, GATT callbacks and 23 repository bindings preserved; 447 unrelated files unchanged.

- The reconnect fixture differs in two baseline categories but is one path. Its separately authorized repair was already in the fresh baseline; the original 29 reconnect cases and all 411 baseline test results remain. This phase does not alter that fixture or its JVM support.

- Seven old test/plan paths moved into `archive/docs-superseded-2026-10-04/{testing,plans}/`: admission, private reconnect, latency, stability, transactional implementation, private reliability and local blocking. Their indexed dispositions, constraints and unique checks remain preserved.

- The Figma preview README was changed by the documentation synchronization to identify a dated static reference.

- Historical Activity/ViewModel body hashes still match. Committed UI repairs belong to `db2260e`; the earlier networking default check, fresh snapshot and preservation scopes establish the source provenance.

Historical guard modes still reject later authorized changes against their older immutable baselines. The current phase is validated with its explicit scope/move mapping; this is not a claim that historical snapshots describe the current candidate. No unexplained discrepancy was found in this audit.

## Historical Phase 1 gate and evidence

Phase 1 local gates pass: 423 tests, APK/instrumentation compilation, Lint and preservation/architecture checks. Its exact APK identity belongs to [validation](../validation.md). Import inspection retains one existing cycle involving MainActivity, MainContainerScreen and NotificationHelper, with no new cycle; this is a navigation check, not compiler analysis.

[Phone comparison](../testing/network-maintainability-test-card.md) is UNTESTED. The approved [phase plan](../plans/network-maintainability-refactor.md) now defers user-operated comparison until the final build. No other oversized source is changed at this gate; DELIVERY-01 remains open.

## Final ownership and exception review

NativeBleManager remains 743 lines (originally 1,384). Reviewed responsibility: existing public Bluetooth/GATT host surface, construction/dependency wiring, live callback entry points and small policy adapters. Remaining adapters include profile/election/identity queries, connect/lock entry points, and public receipt payload construction. It is not a second queue/session/retry owner. Moving the initialization graph solely to reduce its count would obscure captured/live resource order and require broader compatibility plumbing. This is the explicit >500-line exception, supported by declaration maps, generation/queue tests, passing build/JVM/preservation checks and source review; four baseline navigation failures block final local acceptance. Final phone F1–F14 is user-reported PASS; detailed measurements remain unspecified.

Screen coordinators near 400–500 lines intentionally retain saveable state, effects, launchers, gestures or lifecycle ownership. Extracted rendering does not introduce layout wrappers or move those resources. MapLibreMapCanvas retains one MapView, observer, location subscription and cleanup; the composer retains its launcher/animations and root callbacks. Compose state restoration is checked separately from physical process-death/OEM behavior.

| Resource/state family | Single existing owner after extraction |
| --- | --- |
| Platform handles, facade callbacks and construction order | NativeBleManager / BleTransportResources; live providers and original handles |
| Receiver, radio recovery retries and ordered shutdown | BleTransportSessionController; existing lifecycle/supervisor/radio collaborators |
| Peer lookup, captured retirement, heartbeat tracking | BlePeerDirectory / BleLinkRetirementController / BleHeartbeatDriver; shared BleStateStore |
| Inbound gates, outbound relay/fallback and frame events | NativeInboundPipeline / NativeOutboundDispatcher / OutboundFrameReporter; existing journals, directed receipts and transfer engine |
| Captured client attempt and server registration | GattClientAttempt / GattServerRegistration; callback/setup/link/attribute helpers; original L2CAP acceptor resources |
| Candidates, advertisements, generation/drain/cooldown | One BleAdmissionSession; advertisement/bootstrap/diagnostic helpers read the same live capabilities |
| Public flows, router and 23 init bindings | MeshRepository; unchanged flow allocation/defaults/buffers/sharing and startup order |
| Public/private outbox, worker, mutex, conflated wakes and receipt tracking | One MeshOutboundDelivery; MeshInboundMessages receives the exact same mutex |
| Peer/name/topology, block retries and channel/media policy | MeshPeerCoordinator / MeshBlockCoordinator / MeshConversationMedia; original state/jobs and nullable SOS reply capability |
| Incident projection lock and replay/rebuild flag | IncidentEventIngestor; original help replay/Room lock hierarchy and modern sync coordinator |
| Incident commands, legacy branches/sync and JSON codec | IncidentCommands / LegacyIncidentProjection / LegacyIncidentSync / IncidentEventCodec; no second lock or sync worker |
| Presentation state/scopes/flows | Original screen/ViewModel owners; pure IncidentSelectors and compatible model types are separate |
| Navigation and overlays | MainContainerScreen retains effects/state; destination/overlay children receive explicit values/actions |

## Extracted source inventory

Normal source-line counts include imports, comments and spacing. Public compatibility types/entry points retain original packages; new collaborators/rendering helpers are internal. Existing unrelated 401–500-line files were preserved. Paths are relative to `app/src/main/java/com/example/testresqmesh/`.

| Source | Lines |
| --- | ---: |
| `app/navigation/MainDestinationContent.kt` | 74 |
| `app/navigation/overlays/DeveloperBadge.kt` | 50 |
| `app/navigation/overlays/MainApplicationOverlays.kt` | 54 |
| `core/network/bluetooth/BleAdmissionScheduler.kt` | 13 |
| `core/network/bluetooth/admission/BleAdmissionCapabilities.kt` | 35 |
| `core/network/bluetooth/admission/BleAdmissionConstants.kt` | 21 |
| `core/network/bluetooth/admission/BleAdmissionContext.kt` | 57 |
| `core/network/bluetooth/admission/BleAdmissionDiagnostics.kt` | 102 |
| `core/network/bluetooth/admission/BleAdmissionSession.kt` | 44 |
| `core/network/bluetooth/admission/BleAdvertisementAdmission.kt` | 269 |
| `core/network/bluetooth/admission/BleBootstrapQueue.kt` | 257 |
| `core/network/bluetooth/gatt/client/GattClientAttempt.kt` | 79 |
| `core/network/bluetooth/gatt/client/GattClientAttemptContext.kt` | 23 |
| `core/network/bluetooth/gatt/client/GattClientCallback.kt` | 233 |
| `core/network/bluetooth/gatt/client/GattClientConnectionEvents.kt` | 142 |
| `core/network/bluetooth/gatt/client/GattClientPayloadSetup.kt` | 64 |
| `core/network/bluetooth/gatt/client/GattClientScanState.kt` | 18 |
| `core/network/bluetooth/gatt/server/GattL2capAcceptor.kt` | 64 |
| `core/network/bluetooth/gatt/server/GattServerAttributeRequests.kt` | 196 |
| `core/network/bluetooth/gatt/server/GattServerCallback.kt` | 49 |
| `core/network/bluetooth/gatt/server/GattServerLinkEvents.kt` | 227 |
| `core/network/bluetooth/gatt/server/GattServerRegistration.kt` | 51 |
| `core/network/bluetooth/gatt/server/GattServerRegistrationContext.kt` | 13 |
| `core/network/bluetooth/peers/BleLinkRetirementController.kt` | 221 |
| `core/network/bluetooth/peers/BlePeerDirectory.kt` | 98 |
| `core/network/bluetooth/session/BleHeartbeatDriver.kt` | 86 |
| `core/network/bluetooth/session/BleTransportResources.kt` | 11 |
| `core/network/bluetooth/session/BleTransportSessionController.kt` | 372 |
| `data/repository/incident/IncidentCommands.kt` | 328 |
| `data/repository/incident/IncidentEventCodec.kt` | 27 |
| `data/repository/incident/IncidentEventIngestor.kt` | 168 |
| `data/repository/incident/LegacyIncidentProjection.kt` | 240 |
| `data/repository/incident/LegacyIncidentSync.kt` | 186 |
| `data/repository/incident/LegacyIncidentSyncConstants.kt` | 9 |
| `data/repository/mesh/MeshBlockCoordinator.kt` | 241 |
| `data/repository/mesh/MeshConversationMedia.kt` | 70 |
| `data/repository/mesh/MeshDeliveryConstants.kt` | 14 |
| `data/repository/mesh/MeshInboundMessages.kt` | 183 |
| `data/repository/mesh/MeshOutboundDelivery.kt` | 388 |
| `data/repository/mesh/MeshPeerCoordinator.kt` | 308 |
| `data/repository/mesh/SosReplyCapability.kt` | 6 |
| `feature/comms/ui/ActiveChatModels.kt` | 9 |
| `feature/comms/ui/components/composer/ComposerActionPopup.kt` | 123 |
| `feature/comms/ui/components/composer/ComposerAttachmentPreview.kt` | 72 |
| `feature/comms/ui/components/composer/LocationAcquisitionBanner.kt` | 88 |
| `feature/comms/ui/privatechat/DeleteConversationDialog.kt` | 28 |
| `feature/comms/ui/privatechat/PrivateChatHeader.kt` | 109 |
| `feature/comms/ui/privatechat/PrivateMessageContent.kt` | 257 |
| `feature/comms/ui/radio/ChannelHistoryControl.kt` | 101 |
| `feature/comms/ui/radio/RadioMonitorControls.kt` | 77 |
| `feature/comms/ui/radio/RadioPlaybackStatus.kt` | 82 |
| `feature/incident/model/IncidentSelectors.kt` | 102 |
| `feature/incident/ui/components/LegacyCreateIncidentDialog.kt` | 210 |
| `feature/incident/ui/components/LegacyIncidentCard.kt` | 138 |
| `feature/incident/ui/components/LegacyIncidentDetailDialog.kt` | 233 |
| `feature/incident/ui/coordination/CoordinationStatusBanners.kt` | 166 |
| `feature/incident/ui/detail/IncidentActionConfirmation.kt` | 101 |
| `feature/incident/ui/detail/IncidentActivity.kt` | 117 |
| `feature/incident/ui/detail/IncidentConfirmationDialogs.kt` | 140 |
| `feature/incident/ui/detail/IncidentHelperChatTarget.kt` | 12 |
| `feature/incident/ui/detail/IncidentIntroduction.kt` | 223 |
| `feature/incident/ui/detail/IncidentLocationGuidance.kt` | 200 |
| `feature/incident/ui/detail/IncidentPrimaryActions.kt` | 296 |
| `feature/incident/ui/detail/SelectedIncidentHelper.kt` | 195 |
| `feature/incident/ui/form/IncidentTextFields.kt` | 149 |
| `feature/incident/ui/form/IncidentTypeFields.kt` | 221 |
| `feature/incident/ui/list/IncidentAppliedFilters.kt` | 109 |
| `feature/incident/ui/list/IncidentDestinations.kt` | 91 |
| `feature/incident/ui/list/IncidentQuickFilters.kt` | 75 |
| `feature/incident/ui/list/IncidentSearchField.kt` | 108 |
| `feature/incident/ui/list/QuickFilterChip.kt` | 66 |
| `feature/incident/viewmodel/IncidentScreenModels.kt` | 55 |
| `feature/profile/ui/components/ProfileIdentityCard.kt` | 74 |
| `feature/profile/ui/components/ProfileSettingsComponents.kt` | 142 |
| `feature/profile/ui/offlinemap/MapInstallationActions.kt` | 220 |
| `feature/profile/ui/offlinemap/MapInstallationProgress.kt` | 206 |
| `feature/profile/ui/offlinemap/MapPackageDetails.kt` | 241 |
| `feature/profile/ui/offlinemap/MapPackageFormatting.kt` | 18 |
| `feature/sos/ui/EmergencyProfile.kt` | 12 |
| `feature/sos/ui/SosMapPresentation.kt` | 138 |
| `feature/sos/ui/broadcast/BroadcastIllumination.kt` | 133 |
| `feature/sos/ui/broadcast/BroadcastSlider.kt` | 212 |
| `feature/sos/ui/broadcast/EmergencySummaryCard.kt` | 108 |
| `feature/sos/ui/hub/SosDispatchHeader.kt` | 187 |
| `feature/sos/ui/map/MapCollapsedPreview.kt` | 89 |
| `feature/sos/ui/map/MapDetailRow.kt` | 41 |
| `feature/sos/ui/map/MapExpandedDetails.kt` | 220 |
| `feature/sos/ui/map/MapFallbackContent.kt` | 190 |
| `feature/sos/ui/map/MapHeader.kt` | 85 |
| `feature/sos/ui/map/MapLibreMapCanvas.kt` | 474 |
| `feature/sos/ui/map/MapMarkerBitmaps.kt` | 97 |
| `feature/sos/ui/map/NoLocationContent.kt` | 67 |
| `feature/sos/ui/monitoring/RadarScanHud.kt` | 243 |
| `feature/sos/ui/monitoring/ResolveSlider.kt` | 122 |
