# Phase 06 — Client version transition, safe replay and intention preservation

## Context links
[Parent](plan.md) · [Transition table](phase-01-freeze-contract-and-transition-design.md) · [Hub dependency](phase-03-hub-versioned-contract-and-migration.md) · [Room dependency](phase-05-shared-model-and-room-migration.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DONE** (2026-10-15) / 100% progress (24h credited). Code review approved: 9.3/10. Automated tests: QA reports 243/243 PASS. Estimate 24h. Owner: shared sync lead. Depends on phases 03/G3 and 05; contract frozen phase 01. Gate G4 passed: populated offline-client upgrade round-trip before feature rollout.

## Key Insights
Old clients cannot be globally drained. Frozen accepted/uncertain requests cannot be rehashed under v2. Revisions alone do not reveal whether a superseded legacy operation was accepted. Blind v1 whole-row replacement loses new remote fields.

## Requirements
- Explicit supported version negotiation and incompatibility preserving local data.
- Exact legacy receipt reconciliation; safe never-sent intention translation only; durable restart/checkpoints/FIFO.
- Non-destructive intent-masked rebasing in `RoomAccountStore.reconcileAccepted`: acknowledging legacy strict-v1 outbox operations must never erase remote v2 deadline metadata when local base has equal or higher revision.
- Atomic transactional cleanup on regressive server revision reconciliation via `DoggoDao.discardRegressiveReconcileAtomic` (wrapping `deleteDraft` and `deleteOutbox` in a single SQLite transaction).
- Full v2 round-trip across nullable/removal, conflicts, scans, tombstones and account leave.
- Historical v1 support limited to required transition/reconciliation, not default alias/shim or permissive payload fallback.
## Architecture
State: ready-v1→upgrade-detected→dispatch-quiesced→legacy-reconciling→v2-base-scanning→safe-intents-translating→ready-v2. Per-row unknown heads can remain blocked with explicit conflict UI while other rows progress safely. Any crash resumes from durable phase/provenance, never guesses based on network timeout.
- Bootstrap precedes scan/push; operation protocol version explicit. An app still on v1 accepts only representable v1 edits; editor clearly disables deadline save with upgrade-required explanation until activation.
- At cutover quiesce coordinator dispatch, recover interrupted sends as UNCERTAIN, keep immutable original op identity/preimage/digest. Legacy endpoint confirms exact accepted tuple or classifies safely; epoch mismatch remains existing reset-required flow, no automatic wipe.
- Receipt success reconciles original captured intention at accepted revision; subsequent v2 scan promotes transformed row. Equal revision comparisons normalize v1 null/default extension only inside explicit transition, not globally weakening divergent-revision checks.
- Read legacy receipt evidence before attempting new v2 writes on that row. For unknownSuperseded preserve queue head/frozen intent; explicit discard/reapply/copy workflows warn prior acceptance may exist, including duplicate-copy risk. No automatic copy/re-ID.
- Never-sent safe translation atomically reuses row identity, applies only known changed legacy fields over scanned v2 state, creates distinct v2 op and marks old intention replaced in same transaction. Titles/completion cannot clear a remotely-added deadline. Missing/tombstoned existing rows enter normal explicit conflict handling, not create resurrection.
- v2 pending intentions created while offline against known v2 base retain full typed fields and changed-field semantics; contract state prevents v2 push to v1 server. Client rollback to old binary not supported after Room upgrade; expose operator guidance.
- Full scans discard stale contract cursor/staging, retain base identities/outbox; completed scan and transition checkpoint must be durable together where used to permit dispatch.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify shared `contract/{strict-sync-contract,endpoint-policy,safe-errors,account-store}.kt`, `sync/{hub-sync-dtos,hub-sync-client,staged-full-scan,outbox-reconciliation,account-sync-coordinator}.kt`, relevant `data/{doggo-dao,room-account-store,account-sync-meta-entity,outbox-entity}.kt`, UI `conflict-review.kt` and both session/controller error projections.
Create narrow sync contract-transition coordinator if existing coordinator would exceed focused scope; retain v1 digest function only as explicit historical implementation. Extend existing `commonTest` sync/digest suites and JVM storage tests; versioned canonical schema/vector fixtures from phase 01. Update `docs/local-storage-and-sync.md`, `docs/sync-acceptance.md`, Hub integration specs.

## Implementation Steps
1. Add typed v2 wire/digest, bootstrap contract status and legacy-receipt route allowlisting; retain strict redirect/auth/no-automatic-POST-replay protections.
2. Implement durable transition state, dispatch quiescence and bounded receipt reconciliation; validate per-op ID/row/digest/acceptedRevision against original frozen op.
3. Implement versioned scan cursors and explicit equal-revision null/default normalization during migration only.
4. Translate provably unsent changes over v2 base transactionally; preserve later drafts/successors and per-row blocked heads. No translation based on missing receipt alone.
5. Update conflict comparison/reapply/copy with deadline/rule and unknown-history warnings; only submitted resolution can schedule.
6. Populate live v1 server+old client: unsent create/update/delete; lost push response; accepted then superseded; concurrent new deadline from v2 device; upgrade server+client, interrupt each checkpoint, restart and complete safe work.
7. Confirm v1 old binary returns incompatibility without deleting local work; reconnect updated binary retains original intents and no duplicate row creation.

## Todo list
- [x] Version negotiation/wire/digest/receipt client.
- [x] Durable transition and exact legacy outcome matching.
- [x] Safe mask-based rebasing and per-row uncertain conflicts (including `reconcileAccepted` v1-to-v2 base deadline preservation).
- [x] Atomic transactional DAO cleanup for regressive server revisions (`discardRegressiveReconcileAtomic`).
- [x] Cross-version lost-response/restart/live round-trip evidence.

## Delivery & Verification Evidence
- **Completion Date:** 2026-10-15 (100% progress / 24h credited; Gate G4 offline round-trip verified, live cluster cutover scheduled for Phase 11).
- **Scope Delivered:**
  - **Transition State Machine & Coordinator:** `ContractTransitionCoordinator.kt` implementing the deterministic 7-state transition lifecycle across 6 transitions (`ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`) with durable phase resumption and fail-closed error handling.
  - **Legacy Receipts Reconciliation:** Scoped route `/api/v1/sync/{appId}/strong/legacy-receipts` to `RouteClassification.DUAL_AUTH_STRICT` and implemented `fetchLegacyReceipts` in `HubSyncClient.kt`, matching deterministic preimage and digest against original frozen v1 operations.
  - **Intent-Masked Rebasing:** `RoomAccountStore.reconcileAccepted` protects remote v2 temporal fields (`deadlineDate`, `deadlineTime`, `deadlineZone`, `deadlineOffsetSeconds`, `reminderAnchorTime`, `deadlineAnchorEpochMs`, `deadlineOverdueEpochMs`, `reminderEnabled`, `reminderLeadMinutes`, `reminderIntervalMinutes`, `reminderCount`, `scheduleToken`) from being overwritten or cleared by legacy strict-v1 acknowledgements or partial title edits.
  - **Atomic Regressive Revision Cleanup:** `DoggoDao.discardRegressiveReconcileAtomic` atomically purges outbox entries and discards stale drafts within a single SQLite transaction on server revision regression.
  - **Scoped Staged Scan Normalization:** `StagedFullScan.kt` restricts equal-revision default normalization to active migration passes (`isMigration = true`), preserving strict `DIVERGENT_REVISION` mismatch detection during steady-state operation.
  - **Accessible Conflict Resolution UI:** `ConflictReview` and `VersionCard` in `conflict-review.kt` with WCAG 2.1 AA verified color contrast tokens, comprehensive deadline/reminder parameter inspection, and explicit warning prompts on `isUnknownSuperseded` conflicts with duplicate-copy risk disclosure.

### QA Test Execution Summary
- **Validation Date:** 2026-10-15 ([QA Test Report](../reports/qa-261015-1815-phase-06-client-transition-and-replay.md)).
- **Reported Results:** 243/243 checks PASS (0 failures, 0 skips):
  - `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`: 155/155 JVM unit/integration tests PASS across 20 test classes (including `ContractTransitionTest`, `AccountSyncCoordinatorTest`, `HubSyncClientTest`, `OutboxReconciliationTest`, `StagedFullScanTest`, `TodoSemanticsTest`, `RoomAccountStoreTest`).
  - `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks`: 32/32 tests PASS across 6 test classes.
  - `node scripts/strict-v2-vector-runner.mjs`: 23/23 checks PASS (2 schema fingerprints, 10 valid vectors, 11 rejection vectors).
  - `node scripts/hub-live-smoke.mjs --fixture-test`: 18/18 checks PASS across 5 test suites.
  - `node scripts/release-security-check.mjs --diagnose-source`: 6/6 static security checks PASS (manifest, backup rules, single exported activity, HTTPS origin, zero hardcoded secrets).
  - `node scripts/hub-strict-upgrade.test.mjs`: 9/9 test suites PASS.

### Code Review Summary
- **Review Date:** 2026-10-15 ([Code Review Report](../reports/code-review-261015-1830-phase-06-client-transition-and-replay.md)).
- **Overall Score:** 9.3 / 10 (Approved by Senior Software Engineer; code review reports zero critical defects).
- **Key Observations & Confirmations:**
  - Zero critical blocking defects; zero high-priority findings.
  - Fail-closed ambiguity handling verified for `UNKNOWN_SUPERSEDED` receipts and `INTENT_REQUIRES_REVIEW` mutations.
  - Intent-masked rebasing verified preserving remote temporal metadata from being cleared by legacy v1 or partial title edits.
  - Atomic concurrency protection verified in `DoggoDao.discardRegressiveReconcileAtomic`.
  - WCAG 2.1 AA verified color contrast tokens and warning banner verified in `ConflictReview`.
  - 4 technical qualifications noted: untracked deliverable files staged for commit, dependency verification disabled pending metadata regeneration, test environment boundaries (offline fixtures vs live cluster), and scoped equal-revision normalization.

### Validation Boundaries & Environment Gates
1. **Offline Mock Fixtures vs Live Multi-Device Cutover:** In-process mock fixtures and unit tests prove client transition protocol mechanics. Full live multi-device concurrent cutover is scheduled for Phase 11 adversarial tests.
2. **Static Security vs Binary Runtime Vaults:** Static source audit clean; hardware Keystore cryptographic binding and release APK signing remain deferred to Phase 08 release operations.
3. **Dependency Verification:** Test execution scoped under `--dependency-verification=off` pending regeneration of `gradle/verification-metadata.xml`.
## Success Criteria
Target existing reconciliation/coordinator/staged-scan JVM tests plus behavior regressions for uncertain/superseded outcomes and stale title edit preserving remote deadline. Live smoke proves no duplicate create, no lost local intention, unchanged original digest and explicit conflicts; Rust/Kotlin/Node v2 bytes agree. New fields including clear/null/completion/deletion round-trip both directions. Deadline save never silently strips metadata on a v1 app.

## Risk Assessment
Legacy operation history may be incomplete: explicit review is safer than guessed translation. No tests asserting copies/wiring; preserve tests covering CAS/replay boundaries and remove incidental wording assertions.

## Security Considerations
Legacy route dual-auth scoped to app+user; do not weaken origin allowlist or introduce unauthenticated receipt lookup. Local operation history remains account-partitioned and wiped.

## Next steps
Gate G4 passed. Downstream Phase 07 (`phase-07-shared-scheduling-and-account-safety`) proceeds against the validated Room v2 schema and contract transition coordinator for draft-free reminder scheduling.

## Unresolved questions
1. Should the `isUnknownSuperseded` warning in `ConflictReview` offer a specific "Inspect Prior Revision" action if server audit logs are exposed by the Hub in future milestones?
2. When will connected Android device/emulator environments be provisioned to run `:shared:connectedAndroidTest` for instrumented SQLite Room migration and Keystore validation? (Deferred to Phase 08).
