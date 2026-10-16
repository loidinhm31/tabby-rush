# Code Review Report: Phase 01 Shared Foundation (Cycle 2)

**Date:** 2026-10-16  
**Reviewer:** EvcCodeReviewerCycle2 (Senior Software Engineer)  
**Target:** Phase 01 Sticky Task Surfaces — Shared Contracts, Authoritative Store, and Build Foundation  
**Score:** 9.2 / 10 (Conditional Pass — Gate Pending)  

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
- **Lines of code analyzed:** ~1,560 LOC
- **Review focus:** Verification of Cycle 1 remediations, zero-allocation projections, mutation invalidation hygiene, observer snapshot convergence, and dependency provenance gates.
- **Updated plans:** `plans/261016-1409-sticky-task-surfaces/phase-01-shared-foundation.md`.

### 1.2 Overall Assessment
Cycle 1 remediations have been implemented cleanly and effectively. The task surface projector now operates with zero heap allocations during deadline selection and partitions todos in a single pass. Invalidation emissions in `RoomAccountStore.withMutationLock` are properly suppressed for no-op completions (`Unchanged` and `Missing`). Observer tests in SQLite suites now assert snapshot convergence rather than rigid emission counts.

The review score is upgraded from **9.0** to **9.2 / 10 (Conditional Pass — Gate Pending)**. Terminal handoff to downstream leaf phases (Phases 02, 03, 04) remains authorized under conditional status. Two key items warrant attention: handling edge-case server tombstones that retain non-deleted local draft/outbox overlays, and keeping dependency verification explicitly gated due to the pre-existing 149-checksum baseline hold in Gradle metadata.

---

## 2. Remediations Verification Matrix

| Cycle 1 Finding | Remediation Applied | Verification Detail | Status |
|---|---|---|---|
| **Warning 1: Deprecated `Iterable.minWith`** | Removed `minWith`/`minWithOrNull`; replaced with zero-allocation single-pass loop using static `DEADLINE_COMPARATOR`. | `task-surface-projector.kt:34-63` verifies zero allocations and warning-free compilation. | **RESOLVED** |
| **Warning 2: Redundant filtering in `selectNextDeadline`** | Replaced multi-pass filter and list allocation with single-pass scan maintaining `bestOverdue` and `bestUpcoming`. | `selectNextDeadline` iterates `schedules` once; 13 projector tests pass. | **RESOLVED** |
| **Warning 3: Spurious invalidation on `Unchanged`/`Missing`** | `withMutationLock` checks `result != CompletionChange.Unchanged && result != CompletionChange.Missing` before invoking `notifyInvalidated()`. | `room-account-store.kt:76,86` guards emission; `testSetCompletedLocallyDuplicateIsUnchangedNoOp` verifies no outbox/meta bump. | **RESOLVED** |
| **Warning 4: Observer tests assert fixed count** | Changed hardcoded `assertEquals(2, emissions.size)` to `assertTrue(emissions.size >= 2)` and asserted target snapshot state on `emissions.last()`. | `task-surface-store-sqlite-test.kt:291` and `task-surface-sync-sqlite-test.kt:106,151,225` assert state convergence. | **RESOLVED** |
| **Suggestion 1: Single-pass `partitionSurfaceTodos`** | Single loop partitioning into `incomplete` and `completed` lists, skipping deleted todos. | `task-surface-projector.kt:20-32` executes 1 pass with 2 list allocations total. | **RESOLVED** |
| **Suggestion 2: Static `DEADLINE_COMPARATOR`** | Extracted `compareBy` comparator to top-level `DEADLINE_COMPARATOR`. | `task-surface-projector.kt:34` eliminates per-call comparator instantiation. | **RESOLVED** |
| **Suggestion 3: Static `PENDING_OUTBOX_STATES`** | Extracted `setOf(...)` from `projectSingleTodo` to top-level `PENDING_OUTBOX_STATES`. | `room-account-store.kt:26-30` eliminates per-task set allocation during list load. | **RESOLVED** |

---

## 3. Critical Issues
*None identified (0 Critical).*
- No data corruption or silent loss risks.
- No mutex deadlock or re-entrancy issues identified in locked helper sequence.
- All Room transactions execute atomically via `DoggoDao`.
- No sensitive user data or tokens exposed in shared contracts.

---

## 4. Warnings & Observations

### Warning 1: Server tombstone with residual local overlay can trigger `IllegalStateException`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:122-131,224-226,426`
- **Requirement:** Phase 01 Requirements §3: "Missing or tombstoned records return early with Missing without throwing an exception or enqueuing operations."
- **Observation:** In `projectSingleTodo`, `base.deleted == true` marks `isDeletedLocally = true` only when `draft == null && latestOp == null`. If an offline client created a draft or queued an update operation before the server tombstone arrived, `loadTodoLocked(id)` projects the row as existing (`isDeletedLocally = false`). When `setCompletedLocally` proceeds to `saveTodoLocallyLocked`, line 225 throws `IllegalStateException("Cannot mutate permanently deleted tombstone with rowId: ${todo.id}")` instead of returning `CompletionChange.Missing`.
- **Impact:** Rare edge-case crash during local completion if a server tombstone reconciles while a stale local draft exists.
- **Recommendation:** In `setCompletedLocally`, verify `dao.getServerBase(id)?.deleted != true` or treat confirmed server tombstones as authoritative tombstones in `projectSingleTodo` when no newer client create is pending.

### Warning 2: Strict dependency provenance remains an external gate
- **Location:** `gradle/verification-metadata.xml`, `androidApp/gradle.lockfile`
- **Requirement:** Build and deployment validation.
- **Observation:** Glance 1.2.0 adds new library dependencies and lockfile hashes, but no entries were added to `gradle/verification-metadata.xml`. Running Gradle without `--dependency-verification=off` fails due to the pre-existing 149-checksum baseline hold on buildscript tooling.
- **Impact:** Dependency authenticity of newly added Glance artifacts cannot be cryptographically verified by Gradle root configuration until the repository-wide checksum baseline is regenerated.
- **Recommendation:** Maintain the dependency verification gate as explicitly pending in release milestones; do not classify Phase 01 as 100% verified until repository checksum verification passes.

---

## 5. Medium Priority Improvements

### Medium 1: Observer collection cancellation can be bounded by state predicate
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-store-sqlite-test.kt:275`, `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt:90,124,193`
- **Observation:** The tests collect emissions and cancel when `emissions.size >= 2`. While this permits conflation to drop intermediate events, awaiting a target condition (e.g., `todos.any { it.completed }`) with a bounded timeout (`withTimeout(3000)`) is more resilient against delayed or burst scheduler interleavings than an arbitrary size check.

### Medium 2: Direct DAO injection under `withMutationLock` tests invalidation plumbing, not full sync workflow
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt:136-148,205-222`
- **Observation:** Tests simulate scan promotion and conflict head injection by calling `dao.upsertServerBase` and `dao.upsertOutbox` directly inside `withMutationLock`. This validates that `withMutationLock` triggers `taskInvalidationFlow`, but does not exercise the full sync engine or conflict resolver paths.

### Medium 3: Contention test in SQLite suite is sequential
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt:237-262`
- **Observation:** `testConcurrentTitleEditPreservedBySetCompletedLocally` executes `saveTodoLocally` followed sequentially by `setCompletedLocally`. It validates data preservation across successive mutations, but does not simulate true thread contention or overlapping coroutines competing for `withMutationLock`.

---

## 6. Low Priority Suggestions

### Suggestion 1: Eliminate duplicated lock acquisition block in `withMutationLock`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:68-92`
- **Observation:** The inner logic (`mutex.withLock { check(!isFrozen); val result = block(); ... }`) is duplicated in both the `gate != null` and `else` branches. Extracting the inner invocation into a private inline helper reduces repetition.

### Suggestion 2: Align status terminology in evidence documentation
- **Location:** `plans/261016-1409-sticky-task-surfaces/reports/phase-01-foundation-evidence.md:3`
- **Observation:** Header claims "DELIVERED / 100% COMPLETE" whereas the parent plan records "delivered / gate pending" due to the dependency verification hold. Aligning the wording prevents conflicting release status claims.

---

## 7. Positive Observations
1. **Zero-Allocation Projection:** `selectNextDeadline` and `DEADLINE_COMPARATOR` achieve optimal efficiency without any heap allocations per check.
2. **Single-Pass Partitioning:** `partitionSurfaceTodos` cleanly partitions tasks while preserving encounter order in $O(N)$ time with minimal allocations.
3. **Invalidation Hygiene:** Gating `notifyInvalidated()` on actual state mutation prevents spurious DB re-queries across all active subscribers.
4. **Strong Contract Integrity:** `RoomAccountStore` strictly preserves strict-v2 wire format, local revision monotonicity, and scheduleToken rotation invariants.
5. **Frozen Leaf Interfaces:** Platform-agnostic contracts (`TaskSurfaceAccess`, `SurfaceReadiness`, `TaskSections`) provide clean, stable boundaries unblocking Phases 02, 03, and 04.

---

## 8. Recommended Actions
1. **Tombstone Overlay Guard:** In `setCompletedLocally`, ensure server tombstones (`base.deleted == true`) return `CompletionChange.Missing` even if an uncommitted local draft exists, avoiding `IllegalStateException`.
2. **Track Dependency Verification Gate:** Retain `--dependency-verification=off` as an explicit documented prerequisite until root `verification-metadata.xml` is upgraded across the project.
3. **Proceed with Leaf Phases:** Phase 01 shared foundation is robust and ready to unblock Phase 02 (Android Widget), Phase 03 (Android Notification), and Phase 04 (Desktop Compact UI).

---

## 9. Metrics
- **Review Score:** 9.2 / 10 (Conditional Pass)
- **Files Reviewed:** 13
- **Lines of Code Analyzed:** ~1,560 LOC
- **Critical Issues:** 0
- **Warnings:** 2
- **Medium Improvements:** 3
- **Low Suggestions:** 2
- **Gradle Test Suite (Regression Run):** 305 tests passed across `:shared:jvmTest`, `:desktopApp:test`, `:androidApp:testDebugUnitTest` (0 failures)
- **Fixture / Security / Vector Checks:** 18/18 smoke, 6/6 security clean, 21/21 strict-v2 wire vectors passed

---

## 10. Unresolved Questions
1. Should `setCompletedLocally` explicitly discard any orphaned local draft when encountering a server tombstone, or is returning `CompletionChange.Missing` sufficient?
2. Is there a designated team or timeline to update `verification-metadata.xml` for the 149 AGP 8.7.3 / Kotlin 2.1.10 tooling jars, enabling strict verification enforcement?
