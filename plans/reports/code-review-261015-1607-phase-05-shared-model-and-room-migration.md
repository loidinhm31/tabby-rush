# Code Review Report: Phase 05 Shared Task Model & Room Migration

**Date**: 2026-10-15  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-05-shared-model-and-room-migration.md`  
**Overall Score**: 8.8 / 10

---

## Code Review Summary

### Scope
- **Files reviewed**:
  1. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt`
  2. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/server-base-entity.kt`
  3. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/editor-draft-entity.kt`
  4. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/scan-staging-entity.kt`
  5. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/outbox-entity.kt`
  6. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/account-local-settings-entity.kt`
  7. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-delivery-ledger-entity.kt`
  8. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt`
  9. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-database.kt`
  10. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/database-migrations.kt`
  11. `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt`
  12. `shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/2.json`
  13. `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-reminder-models-test.kt`
  14. `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v1-to-v2-test.kt`
  15. `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt`
- **Lines of code analyzed**: ~3,288 lines
- **Review focus**: Phase 05 shared temporal model, Room schema version 2 migration, outbox provenance/intent masks, account-local settings/ledger, BundledSQLite restart stability.
- **Updated plans**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-05-shared-model-and-room-migration.md`

---

## Overall Assessment

Phase 05 establishes a solid foundation for strict-v2 task scheduling and Room persistence. Key achievements:
- **Strict Invariants**: `TaskDeadline` and `ReminderRule` enforce checked 64-bit integer arithmetic with overflow guards. `Todo` models enforce schedule token presence, occurrence limits (1..10), and anchor boundary checks (`last <= anchor`).
- **Explicit Migration**: `DatabaseMigrations.MIGRATION_1_2` successfully transitions Room schemas 1 to 2 without destructive fallback. Provenance tracking (`MIGRATED_FROM_ROOM_V1`), intent derivation (`TITLE|COMPLETED`, `DELETE`, `REQUIRES_REVIEW`), and dispatch history tagging are executed cleanly in SQLite.
- **Account Isolation & Privacy**: `AccountLocalSettingsEntity` and `ReminderDeliveryLedgerEntity` are isolated to account partitions, wiped during logout, and never transmitted over the wire.
- **Tombstone Protection & Safe Coalescing**: Tombstone non-mutation is strictly enforced with `IllegalStateException`. Unsent create-delete cancellation and outbox coalescing explicitly require `transitionCheckpoint == null`, preventing corruption of historical/migrated entries.

JVM BundledSQLite test execution passes cleanly. Connected Android device testing and cross-version replay reconciliation remain explicitly gated in Phase 06 and Phase 08.

---

## Critical Issues

*None blocking Phase 05 storage layer.*

---

## High Priority Findings

1. **Cross-Version Base Reconciliation Risk (Phase 06 Dependency)**:
   - **Location**: `RoomAccountStore.kt:512-558` (`reconcileAccepted`)
   - **Impact**: When acknowledging a legacy strict-v1 outbox operation (`op.contractVersion == CONTRACT_VERSION_V1`), `updatedBase` copies `op.deadlineDate` (null). If the local base already contained strict-v2 deadline data from remote scan or prior edit, this uncodified write erases the v2 fields.
   - **Remediation**: Phase 06 intent rebase must ensure v1 mutation reconciliation does not overwrite remote v2 deadline fields on equal/higher revisions:
     ```kotlin
     val preserveV2Deadline = op.contractVersion == OutboxEntity.CONTRACT_VERSION_V1 && currentBase?.deadlineDate != null
     val deadlineDate = if (preserveV2Deadline) currentBase?.deadlineDate else op.deadlineDate
     ```
   - **Action**: Gated to Phase 06 client version transition.

2. **Non-Atomic Reconcile Cleanup on Regressive Server Revision**:
   - **Location**: `RoomAccountStore.kt:505-510`
   - **Impact**: When `serverRevision < currentBase.serverRevision`, the store deletes the draft (`deleteDraft`) and outbox (`deleteOutbox`) in separate non-transactional DAO calls. A crash or process termination between these calls leaves a partially reconciled state.
   - **Remediation**: Wrap both operations in a single `@Transaction` method in `TabbyRushDao` (e.g., `discardRegressiveReconcileAtomic(opId, rowId, clearDraft)`).

---

## Medium Priority Improvements

1. **Storage Documentation Out-of-Sync**:
   - **Location**: `docs/local-storage-and-sync.md:251-277`
   - **Details**: Section 7.1 still lists Room migration as "[Planned Phase 05]" and references draft names (`protocolVersion`, `dispatchedOnce`, `changedFieldsMask`) instead of finalized entity column names (`contractVersion`, `dispatchHistory`, `intentMask`, `transitionCheckpoint`).
   - **Action**: Update `docs/local-storage-and-sync.md` in Phase 06/docs sync to reflect Room v2 as active schema.

2. **Test Assertions for Schedule Token Generation**:
   - **Location**: `room-account-store-test.kt:345-376` (`testV2SaveGeneratesTokenAndPreservesOnTitleEdit`)
   - **Details**: The test initializes `initialTodo` with an explicit `scheduleToken = "sched-tok-initial"`. It confirms the token survives title edits, but does not exercise dynamic token generation when caller supplies `scheduleToken = null` on initial creation.
   - **Remediation**: Add a test verifying `saveTodoLocally` generates a non-empty UUID token when `todo.scheduleToken` is generated via UI helper.

---

## Low Priority Suggestions

1. **Redundant Field Equality in `hasScheduleSemanticChange`**:
   - **Location**: `todo-models.kt:167-188`
   - **Details**: `hasScheduleSemanticChange` compares all 11 fields individually. Since `TaskDeadline` and `ReminderRule` are immutable data classes with value-based `equals()`, `oldDeadline != newDeadline || oldRule != newRule` achieves identical semantics with simpler maintainability.

2. **Hardcoded Test Epochs vs Civil Time**:
   - **Location**: `task-deadline-reminder-models-test.kt`
   - **Details**: Test fixtures use synthetic epoch numbers (`1792051200000L`). While mathematically sufficient for storage validation, they do not verify civil time zone DST boundary calculations (handled separately in `StrictTimeZoneResolverTest`).

---

## Positive Observations

- **Checked 64-bit Arithmetic**: `safeMultiply`, `safeSubtract`, and `safeAdd` prevent overflow wrapping vulnerabilities on large repeat intervals or negative offsets.
- **Fail-Closed Migration Defaults**: Legacy v1 rows default to canonical nulls for deadlines and disabled reminder rules, eliminating phantom notification triggers on upgrade.
- **Optimal SQLite Indexing**: Composite primary keys and targeted indices (`dueEpochMs`, `[accountKey, generation, taskId]`) ensure low-latency ledger queries and task cancellations.
- **Wipe Marker Integration**: `wipeAllTablesAtomic()` incorporates both new tables (`account_local_settings` and `reminder_delivery_ledger`), preserving fail-closed privacy guarantees upon account logout.

---

## Validation Results

| Test / Check | Scope | Results |
|---|---|---|
| `./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | 19 test classes, 128 tests | 128 PASS, 0 FAIL (6.0s) |
| `node scripts/strict-v2-vector-runner.mjs` | Fingerprints (v1/v2), 10 valid + 11 reject vectors | 23 PASS, 0 FAIL |
| `node scripts/hub-live-smoke.mjs --fixture-test` | 5 suites, strict-v1 protocol fixtures | 18 PASS, 0 FAIL |
| `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, rules, origin HTTPS, secrets | 6 PASS, 0 FAIL |

---

## Recommended Actions

1. In Phase 06 (`phase-06-client-version-transition-and-replay`), implement non-destructive intent-masked rebasing in `RoomAccountStore.reconcileAccepted` to safeguard remote v2 deadline fields from legacy v1 acknowledgements.
2. Introduce `discardRegressiveReconcileAtomic` DAO transaction to eliminate the non-atomic cleanup race in `reconcileAccepted`.
3. Update `docs/local-storage-and-sync.md` to document Room v2 entities and explicit migration guarantees.
4. Schedule Android instrumented test suite (`:shared:connectedAndroidTest`) on an emulator/device during Phase 08.

---

## Metrics
- **Type Coverage**: 100% (Strict KMP types, zero untyped `Any` in storage paths)
- **Test Coverage**: 128 JVM unit/integration tests + 23 contract vector tests
- **Linting Issues**: 0 errors, 0 warnings

---

## Unresolved Questions
1. When will connected Android device/emulator environments be available to validate instrumented Keystore and Android-specific SQLite Room migration paths?
2. Does Phase 06 require local outbox migration for un-dispatched v1 drafts, or will un-dispatched v1 drafts re-derive intent masks during the first v2 sync pass?
