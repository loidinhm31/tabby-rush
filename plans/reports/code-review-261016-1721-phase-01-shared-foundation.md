# Code Review Report: Phase 01 Shared Foundation

**Date:** 2026-10-16  
**Reviewer:** EvcCodeReviewer (Senior Software Engineer)  
**Target:** Phase 01 Sticky Task Surfaces — Shared Contracts, Authoritative Store, and Build Foundation  
**Score:** 9.0 / 10 (Conditional Pass)  

---

## 1. Code Review Summary

### 1.1 Scope
- **Files reviewed (13):**
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/account-store.kt`
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-contracts.kt`
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-projector.kt`
  - `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt`
  - `androidApp/src/main/kotlin/dev/doggo/android/task-surface-host.kt`
  - `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-task-model.kt`
  - `gradle/libs.versions.toml`
  - `androidApp/build.gradle.kts`
  - `androidApp/gradle.lockfile`
  - `shared/src/commonTest/kotlin/dev/doggo/shared/contract/task-surface-projector-test.kt`
  - `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-store-sqlite-test.kt`
  - `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt`
  - `shared/src/jvmTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt`
- **Lines of code analyzed:** ~1,550 LOC
- **Review focus:** Concurrency & non-reentrant mutex hygiene, outbox invariants, single-point-of-truth projections, test boundary validity, and dependency provenance.
- **Updated plans:** `plans/261016-1409-sticky-task-surfaces/phase-01-shared-foundation.md`, `plans/261016-1409-sticky-task-surfaces/plan.md`.

### 1.2 Overall Assessment
The Phase 01 implementation delivers a clean, well-architected shared foundation for sticky task surfaces. The extraction of private locked helpers (`loadTodoLocked`, `loadTodosLocked`, `saveTodoLocallyLocked`, `projectSingleTodo`) solves the non-reentrant mutex constraint cleanly, preventing potential self-deadlocks. Persistence atomicity, schedule-token preservation, monotonic timestamps, and outbox coalescing adhere strictly to architectural specifications.

However, terminal handoff is **conditional**: dependency verification was run with `--dependency-verification=off` due to the pre-existing 149-checksum baseline hold, so strict dependency provenance remains unproven; several concurrency and observer tests assert sequential states or exact event counts rather than true overlapped execution and snapshot convergence.

---

## 2. Requirement to Test Matrix & Verification Coverage

| Requirement | Implementation Component | Test File & Test Name | Verification Status |
|---|---|---|---|
| Atomic desired completion (`setCompletedLocally`) | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceStoreSqliteTest.testSetCompletedLocallyChangesStateAndUpdatesMonotonicTime` | **VERIFIED** |
| Missing record returns `Missing` | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceStoreSqliteTest.testSetCompletedLocallyMissingRecordReturnsMissing` | **VERIFIED** |
| Tombstone returns `Missing` without exception | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceStoreSqliteTest.testSetCompletedLocallyTombstoneReturnsMissingWithoutException` | **VERIFIED** |
| Duplicate desired state no-op (`Unchanged`) | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceStoreSqliteTest.testSetCompletedLocallyDuplicateIsUnchangedNoOp` | **VERIFIED** |
| Concurrent field preservation (title/deadline/token) | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceStoreSqliteTest.testSetCompletedLocallyPreservesConcurrentlyChangedFields` | **VERIFIED** |
| Persistence across store reopen | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceStoreSqliteTest.testSetCompletedLocallyPersistsAcrossReopen` | **VERIFIED** |
| Cold observer replay on initial collect | `RoomAccountStore.observeTodos` | `TaskSurfaceStoreSqliteTest.testObserveTodosColdReplay` | **VERIFIED** |
| Invalidation on mutation | `RoomAccountStore.observeTodos` | `TaskSurfaceStoreSqliteTest.testObserveTodosEmitsOnCompletionMutation` | **VERIFIED** |
| Sync reconcile accepted emission | `RoomAccountStore.reconcileAccepted` | `TaskSurfaceSyncSqliteTest.testReconcileAcceptedEmitsUpdatedStateInObservers` | **VERIFIED** |
| Mutation lock promotion emission | `RoomAccountStore.withMutationLock` | `TaskSurfaceSyncSqliteTest.testPromotionUnderMutationLockEmitsToObservers` | **PARTIAL** (direct DAO write inside lock wrapper, not full sync engine) |
| Conflict head selection flag | `RoomAccountStore.withMutationLock` | `TaskSurfaceSyncSqliteTest.testOutboxConflictHeadEmitsHasUnresolvedConflict` | **VERIFIED** |
| Concurrent title edit preservation | `RoomAccountStore.setCompletedLocally` | `TaskSurfaceSyncSqliteTest.testConcurrentTitleEditPreservedBySetCompletedLocally` | **PARTIAL** (sequential execution, not concurrent threads) |
| Strict-v2 contract version & schedule token preservation | `RoomAccountStore.saveTodoLocallyLocked` | `RoomAccountStoreTest.testSetCompletedLocallyPreservesScheduleTokenAndV2Contract` | **VERIFIED** |
| Stable partition (`createdAt DESC, id DESC`, exclude deleted) | `partitionSurfaceTodos` | `TaskSurfaceProjectorTest.testPartitionSurfaceTodos*` (3 tests) | **VERIFIED** |
| Urgency tie-breaking & conflict inclusion | `selectNextDeadline` | `TaskSurfaceProjectorTest.testSelectNextDeadline*` (10 tests) | **VERIFIED** |
| Glance 1.2.0 build verification | `libs.versions.toml`, `build.gradle.kts` | `:androidApp:compileDebugKotlin`, lockfile check | **VERIFIED** (Compile only; strict provenance pending) |

---

## 3. Critical Issues
*None identified (0 Critical).*
- No data loss risks or unhandled exceptions on missing/tombstoned entities.
- No mutex deadlock or lock inversion found (`withMutationLock` -> `gate` -> `mutex`, readers only take `mutex`).
- No mutation bypass (all writes go through `withMutationLock` and atomic Room transactions).
- No sensitive credentials exposed in surface contracts.

---

## 4. Warnings & Observations

### Warning 1: Deprecated `Iterable.minWith` used alongside `minWithOrNull`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-projector.kt:40,43`
- **Requirement:** Idiomatic, warning-free Kotlin 2.1 code standards.
- **Concrete Evidence:**
  ```kotlin
  40: overdue.minWith(compareBy({ it.deadline!!.overdueEpochMs }, { it.taskId }))
  ...
  43: upcoming.minWithOrNull(compareBy({ it.deadline!!.overdueEpochMs }, { it.taskId }))
  ```
- **Impact:** `Iterable.minWith` was deprecated in Kotlin 1.4 in favor of `minWithOrNull`. Using both in the same method introduces inconsistency and potential future deprecation compilation errors.
- **Recommendation:** Replace `minWith` on line 40 with `minWithOrNull`.

### Warning 2: Redundant filtering in `selectNextDeadline`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-projector.kt:42`
- **Requirement:** Token & runtime efficiency; KISS.
- **Concrete Evidence:**
  ```kotlin
  38: val overdue = eligible.filter { nowEpochMs >= it.deadline!!.overdueEpochMs }
  ...
  42: val upcoming = eligible.filter { nowEpochMs < it.deadline!!.overdueEpochMs }
  ```
- **Impact:** If `overdue.isEmpty()`, every item in `eligible` is guaranteed to satisfy `nowEpochMs < overdueEpochMs`. Re-filtering `eligible` executes an unnecessary second iteration and allocates an unneeded `ArrayList`.
- **Recommendation:** If `overdue.isEmpty()`, use `eligible.minWithOrNull(...)` directly without allocating `upcoming`.

### Warning 3: Spurious internal invalidation on `Unchanged` / `Missing` completion
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:70,78,420,422`
- **Requirement:** Authoritative completion idempotency and coalescing.
- **Concrete Evidence:**
  `withMutationLock` calls `notifyInvalidated()` unconditionally upon block return. When `setCompletedLocally` returns early with `CompletionChange.Unchanged` or `CompletionChange.Missing`, `notifyInvalidated()` is still triggered.
- **Impact:** Although downstream UI subscribers do not receive duplicate emissions (suppressed by `.distinctUntilChanged()`), all active observers will re-execute `loadTodos()` and `getPersistedScheduleProjection()` against the database for a no-op action.
- **Recommendation:** Document this behavior or consider supporting conditional mutation invalidation when no write occurred.

### Warning 4: Observer tests assert fixed event count instead of state convergence
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-store-sqlite-test.kt:275`, `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt:90,122,190`
- **Requirement:** Observer resiliency under burst mutations and conflation.
- **Concrete Evidence:**
  Tests collect Flow emissions and cancel immediately at `emissions.size >= 2`, asserting `assertEquals(2, emissions.size)`.
- **Impact:** The contract specifies that `taskInvalidationFlow` may conflate burst events (`DROP_OLDEST`) and that final state convergence must be guaranteed. Hardcoding exact emission count assertions can cause test flakiness under high-concurrency environments.
- **Recommendation:** In future test additions, assert the final converged snapshot rather than relying on exact intermediate emission counts.

---

## 5. Suggestions & Performance Optimizations

### Suggestion 1: Single-pass partitioning in `partitionSurfaceTodos`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-projector.kt:20-25`
- **Observation:** `todos.filter { !it.isDeleted }` followed by two separate filters for `completed` and `!completed` executes 3 passes and 3 list allocations.
- **Improvement:**
  ```kotlin
  fun partitionSurfaceTodos(todos: List<Todo>): TaskSections {
      val incomplete = ArrayList<Todo>()
      val completed = ArrayList<Todo>()
      for (todo in todos) {
          if (!todo.isDeleted) {
              if (todo.completed) completed.add(todo) else incomplete.add(todo)
          }
      }
      return TaskSections(incomplete = incomplete, completed = completed)
  }
  ```

### Suggestion 2: Zero-allocation `selectNextDeadline`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-projector.kt:34-52`
- **Observation:** Can be evaluated in a single loop comparing candidates on the fly, eliminating all intermediate lists and dynamic `compareBy` comparator allocations.

### Suggestion 3: Static allocation for pending outbox states
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:145`
- **Observation:** `setOf(OutboxEntity.STATE_PENDING, OutboxEntity.STATE_DISPATCHING, OutboxEntity.STATE_UNCERTAIN)` allocates a new `Set` on every projected task. Extract to a top-level or companion `private val PENDING_STATES = setOf(...)`.

---

## 6. Positive Observations
1. **Flawless Non-Reentrant Mutex Management:** Private `*Locked` methods (`loadTodoLocked`, `loadTodosLocked`, `saveTodoLocallyLocked`) guarantee that `setCompletedLocally` executes under a single `withMutationLock` without self-deadlock.
2. **Unified Single-Point Projection:** `projectSingleTodo` cleanly eliminates duplicate code between single-task lookup and full-table load, enforcing strict DRY.
3. **Robust Coalescing:** Correctly merges pending local mutations with new completion states, avoiding unnecessary revision proliferation and redundant network sync.
4. **Clean Domain Separation:** Frozen contracts (`TaskSurfaceAccess`, `SurfaceReadiness`, `StickyTaskState`) provide zero-dependency surfaces ready for independent leaf development (Phases 02, 03, 04).

---

## 7. Recommended Actions
1. **Replace deprecated `minWith`:** Update `task-surface-projector.kt:40` to use `minWithOrNull`.
2. **Remove redundant filter:** Update `task-surface-projector.kt:42` to use `eligible` directly when `overdue.isEmpty()`.
3. **Retain explicit dependency verification gate:** Keep dependency provenance gate marked pending in release-level checks until root checksum baseline is addressed.
4. **Proceed with leaf phases (Phases 02, 03, 04):** Phase 01 shared contracts and persistence APIs are stable and unblock downstream leaf development.

---

## 8. Unresolved Questions
1. Does the pre-existing 149-checksum baseline hold in `gradle/verification-metadata.xml` have an upstream scheduled resolution window, or does it remain an explicit bypass across all feature phases?
