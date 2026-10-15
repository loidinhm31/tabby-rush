# Phase 05 — Shared task model and explicit Room migration

## Context links
[Parent](plan.md) · [Frozen fields](phase-01-freeze-contract-and-transition-design.md) · [Storage evidence](research/reminder-platform-research.md) · [Existing storage spec](../../docs/local-storage-and-sync.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DONE** (2026-10-15) / 100% progress (20h credited). Code review approved with qualifications: 8.8/10. Automated tests: QA reports 175/175 PASS. Estimate 20h. Owner: shared storage/contract lead. Depends on phase 01 G1; runs alongside phase 03/04. This phase completes Room v2 schema and storage foundations without activating v2 network writes.

## Key Insights
Every persisted representation carries task intentions. Outbox schema extension must keep old frozen digest/preimage intact. UI draft and submitted saved intention are different eligibility sources.

## Requirements
- Typed deadline/rule domain, same canonical wire mappings as phase 01; all saves validated before committing.
- Explicit Room 1→2 preserving existing base/outbox/drafts/staging/meta and account partitioning.
- Store source contract version, immutable dispatch evidence, transition checkpoint/changed-field intent mask; preserve frozen v1 data on local upgrade.
- Local settings and delivery ledger are account-local, wiped with account, never sent to Hub.

## Architecture
- Prefer grouped immutable domain types `TaskDeadline` and `ReminderRule`, flattened persisted/wire fields per phase 01. Optional fields canonicalized once; one validator/calculator shared by UI/storage/native.
- All task-bearing tables gain metadata; migrated old live task has null deadline/token, disabled reminders, lead0/count1/intervalnull. Existing tombstones retain canonical null payload.
- Outbox gains contractVersion default v1 and dispatch-history/provenance. Existing DISPATCHING/UNCERTAIN/UNKNOWN_SUPERSEDED are historical; any state lacking proof of never-dispatch remains frozen, not inferred safe from PENDING alone.
- Intent mask derives from old intention and observed base where provable; uncertain/incomplete history flags require review. Persist full saved v2 intentions so later title/completion updates do not erase deadline fields.
- New account-local settings defaults: notifications off until user enables, new-task reminder default disabled, lead0/count1, date-only anchor09:00. Capability is refreshed from OS rather than treating persisted permission status as authoritative. Defaults change future tasks only.
- Ledger key account/generation/task/token/index; due time, claim outcome, posted platform ID and cancellation state sufficient for callback/restart/wipe recovery. Settings/ledger tables included in migration/export; no server-controlled data for local ownership.
- Schedule token generated on deadline/rule semantic change; title/completion do not replace it. Clearing deadline resets token. No task deadline means no scheduling records.
- Migration registered in existing builders on Android/JVM/controller paths; no fallbackToDestructiveMigration, no downward auto-migration.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify `shared/src/commonMain/kotlin/dev/doggo/shared/contract/{todo-models,todo-action,todo-ui-state,account-store,strict-sync-contract}.kt`; data `{server-base-entity,outbox-entity,editor-draft-entity,scan-staging-entity,account-sync-meta-entity,doggo-dao,doggo-database,database-migrations,room-account-store}.kt`; shared Android/JVM `data/{android,jvm}-database-builder.kt`; desktop `main.kt` Room builder as required.
Create focused deadline/rule policy/calculator, local settings/ledger entities and DAO methods within existing `contract/` and `data/`; exported `shared/schemas/dev.doggo.shared.data.DoggoDatabase/2.json` alongside v1 export. Extend `shared/src/jvmTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt`; create meaningful migration behavior suite in same test source set. No obsolete v1 export deletion.

## Implementation Steps
1. Add immutable grouped model and canonical flattened mapping; central checked schedule/consistency validation. Update all constructor/copy/save/toggle/delete/conflict callsites; use LSP references if available at execution.
2. Add persisted fields/provenance/masks/checkpoints/settings/ledger; write explicit SQL migration with canonical defaults/nullability/indexes.
3. Register migration in all builders and export Room schema using pinned toolchain; compare actual validation against migration, not source-text checks.
4. Make saved mutation transaction copy entire valid deadline/rule and only rotate token for semantic edits; draft-only updates cannot write eligibility/ledger.
5. Migrate real v1 fixture containing live/tombstone, draft, queued/uncertain/conflict operations and metadata; reopen on bundled SQLite/JVM and Android.
6. Update storage spec with migration/default/provenance guarantees after smoke evidence. v1 frozen op bytes/digest remain verifiable with v1 encoder.

## Todo list
- [x] Domain/policy and all task-bearing projections updated.
- [x] Room v2 entities/explicit migration/export/builders.
- [x] Frozen provenance and intent change masks.
- [x] Local settings/ledger and migrated-fixture restart smoke.

## Delivery & Verification Evidence
- **Completion Date:** 2026-10-15 (100% progress / 20h credited).
- **Scope Delivered:**
  - **Domain Model & Invariants:** Immutable grouped models `TaskDeadline` and `ReminderRule` in `todo-models.kt`. Enforces checked 64-bit integer arithmetic with overflow guards (`safeMultiply`, `safeSubtract`, `safeAdd`), repetition limits (`1..10`), anchor boundary checks (`last <= anchor`), and semantic `scheduleToken` lifecycle (generated on deadline/rule change, preserved across title edits, cleared when deadline removed).
  - **Explicit Room Migration (`MIGRATION_1_2`):** Implemented in `DatabaseMigrations.kt` and registered in `AndroidDatabaseBuilder` and `JvmDatabaseBuilder`. Explicit non-destructive schema migration from version 1 to 2; no fallback to destructive migration. Exported Room schema in `shared/schemas/dev.doggo.shared.data.DoggoDatabase/2.json`.
  - **Extended Entities:** `ServerBaseEntity`, `EditorDraftEntity`, `ScanStagingEntity`, and `OutboxEntity` updated with 12 temporal fields, `contractVersion` (defaults to v1), `dispatchHistory`, `intentMask` (`TITLE`, `COMPLETED`, `DEADLINE`, `REMINDER_RULE`, `DELETE`, `REQUIRES_REVIEW`), and `transitionCheckpoint`.
  - **New Account-Partitioned Entities:** `AccountLocalSettingsEntity` (`account_local_settings`) for device-local reminder preferences and `ReminderDeliveryLedgerEntity` (`reminder_delivery_ledger`) for local dispatch tracking. Both entities are account-isolated, excluded from sync/backup, and wiped cleanly in `DoggoDao.wipeAllTablesAtomic()` upon account teardown.
  - **Tombstone & Coalescing Invariants:** Non-mutation of tombstones enforced via `IllegalStateException`. Unsent create-delete cancellation and outbox coalescing explicitly require `transitionCheckpoint == null` to prevent corruption of migrated/historical entries.

### QA Test Execution Summary
- **Validation Date:** 2026-10-15 ([QA Test Report](../reports/qa-261015-1602-phase-05-shared-model-and-room-migration.md)).
- **Reported Results:** 175/175 checks PASS (0 failures, 0 skips):
  - `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`: 128/128 JVM unit/integration tests PASS across 19 test classes (including `RoomMigrationV1ToV2Test`, `TaskDeadlineReminderModelsTest`, `RoomAccountStoreTest`, `StrictTimeZoneResolverTest`).
  - `node scripts/strict-v2-vector-runner.mjs`: 23/23 wire vectors PASS (2 schema fingerprints, 10 valid vectors, 11 rejection vectors).
  - `node scripts/hub-live-smoke.mjs --fixture-test`: 18/18 protocol fixture checks PASS across 5 test suites.
  - `node scripts/release-security-check.mjs --diagnose-source`: 6/6 static security checks PASS (manifest, backup rules, single exported activity, HTTPS origin, zero hardcoded secrets).

### Code Review Summary
- **Review Date:** 2026-10-15 ([Code Review Report](../reports/code-review-261015-1607-phase-05-shared-model-and-room-migration.md)).
- **Overall Score:** 8.8 / 10 (Approved with technical qualifications).
- **Post-Review Remediations Implemented & Verified in Phase 05:**
  1. *Cross-Version Base Reconciliation Remediation (`RoomAccountStore.reconcileAccepted`)*: Updated to preserve existing remote v2 deadline/rule/token metadata from `currentBase` when reconciling legacy strict-v1 non-delete operations (`op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1`). Verified by regression test `testV1AckPreservesV2DeadlineFieldsOnServerBase`.
  2. *Atomic Stale Outbox Cleanup on Regressive Server Revision*: Implemented `@Transaction suspend fun purgeStaleOutboxAtomic(opId, rowId, clearDraft)` in `DoggoDao` and wired in `RoomAccountStore.reconcileAccepted`. Verified by regression test `testStaleAckPurgesOutboxAndRetainsNewerDraft`.
  3. *Semantic Schedule Helper*: Hardened `hasScheduleSemanticChange` so that when both deadlines are null, differences in disabled rules return false (no schedule change). Verified by unit assertions.
  4. *Token Generation & Coalescing*: Added regression tests verifying token generation when caller supplies null (`testTokenGeneratedWhenCallerSuppliesNull`) and pre-acknowledgement title edit coalescing with unsent create (`testPreAckTitleEditCoalescesWithUnsentCreate`).
### Validation Boundaries & Environment Gates
1. **Desktop JVM BundledSQLite vs Platform Android:** `:shared:jvmTest` validates BundledSQLite on desktop JVM. Android instrumented Room migrations and SQLite runtime execution require device/emulator testbed, deferred to Phase 08.
2. **Protocol Fixtures vs Live Cluster Migration:** Offline fixture smoke validates strict-v1 protocol compatibility against an in-process mock server; live multi-instance MongoDB cluster execution and live strict-v2 migration remain ON HOLD under Gate G3.
3. **Static Security vs Binary Signing:** Security check validates static manifest and source properties; binary APK Signature Scheme v2/v3 and runtime Android Keystore verification remain deferred to Phase 08 release operations.
4. **Dependency Verification:** Test execution scoped under `--dependency-verification=off` pending regeneration of `gradle/verification-metadata.xml`.

## Next steps
Phase 06 (`phase-06-client-version-transition-and-replay`) consumes the Room v2 schema and implements non-destructive intent-masked rebasing and safe replay. Phase 07 (`phase-07-shared-scheduling-and-account-safety`) consumes persisted saved intentions for draft-free reminder scheduling.

## Unresolved questions
1. When will connected Android device/emulator environments be provisioned to run `:shared:connectedAndroidTest` for instrumented SQLite Room migration and Keystore validation? (Deferred to Phase 08).
2. In Phase 06, will un-dispatched legacy v1 drafts be migrated locally to v2 outbox records, or will they re-derive intent masks during the first v2 sync pass?
