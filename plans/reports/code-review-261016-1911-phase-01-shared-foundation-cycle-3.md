# Code Review Report: Phase 01 Shared Foundation (Cycle 3)

**Date:** 2026-10-16  
**Reviewer:** EvcCodeReviewerCycle3 (Senior Software Engineer)  
**Target:** Phase 01 Sticky Task Surfaces — Shared Contracts, Authoritative Store, and Build Foundation  
**Score:** 9.4 / 10 (Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending)  

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
- **Lines of code analyzed:** ~1,680 LOC
- **Review focus:** Cycle 2 findings remediation (server tombstone with residual draft/outbox overlay, dependency provenance accounting), concurrency semantics, predicate-based observer bounds, zero-allocation projection, and architectural readiness for downstream leaf phases (Phases 02, 03, 04).
- **Updated plans:** `plans/261016-1409-sticky-task-surfaces/phase-01-shared-foundation.md`, `plans/261016-1409-sticky-task-surfaces/reports/phase-01-foundation-evidence.md`.

### 1.2 Overall Assessment
Phase 01 foundation code demonstrates exceptional technical rigor, robust thread-safety, and strict adherence to architectural contracts. The server-tombstone finding from Cycle 2 is cleanly resolved in `RoomAccountStore.setCompletedLocally` by verifying authoritative tombstone state prior to loading overlays or attempting local mutations, returning `CompletionChange.Missing` without throwing exceptions or generating corrupting outbox entries. Two new targeted SQLite regression tests validate both residual-draft and residual-outbox retention.

The test suite now stands at **307/307 PASS** across shared JVM, desktop, and Android unit suites, accompanied by 18/18 fixture smoke, 6/6 source security diagnostics, and 8/8 cutover self-test checks. 

The review score is upgraded to **9.4 / 10 (Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending)**. The code foundation is solid and terminal conditional handoff to Phases 02, 03, and 04 remains fully authorized.

---

## 2. Remediations Verification Matrix

| Cycle 2 Finding | Remediation Applied | Verification Detail | Status |
|---|---|---|---|
| **Warning 1: Server tombstone with residual draft/outbox overlay triggers `IllegalStateException`** | Added early guard in `setCompletedLocally` checking `dao.getServerBase(id)?.deleted == true` and returning `CompletionChange.Missing` before evaluating overlays or calling `saveTodoLocallyLocked`. | `room-account-store.kt:426-429`. Verified by SQLite tests `testSetCompletedLocallyTombstoneWithResidualDraftReturnsMissingWithoutException` and `testSetCompletedLocallyTombstoneWithResidualOutboxReturnsMissingWithoutException`. | **RESOLVED** |
| **Warning 2: Strict dependency provenance remains an external gate** | Glance 1.2.0 version pins locked in `androidApp/gradle.lockfile` (324 hashes). External gate explicitly documented and tracked in `phase-01-shared-foundation.md` and `phase-01-foundation-evidence.md`. | Documented external gate pending root `verification-metadata.xml` tooling baseline regeneration. | **ACCOUNTED FOR (HOLD)** |
| **Medium 1: Observer collection cancellation can be bounded by state predicate** | Converted arbitrary emission count checks (`emissions.size >= 2`) to `CompletableDeferred` awaiting target state predicates with `withTimeout(3000)`. | `task-surface-store-sqlite-test.kt:348-363`, `task-surface-sync-sqlite-test.kt:83-106,118-144`. | **RESOLVED** |
| **Suggestion 2: Align status terminology in evidence documentation** | Aligned status in `phase-01-foundation-evidence.md` to "DELIVERED / CONDITIONAL PASS — GATES PENDING" matching parent plan. | `phase-01-foundation-evidence.md:3`. | **RESOLVED** |

---

## 3. Critical Issues
*None identified (0 Critical).*
- No data corruption or silent loss risks.
- Mutex serialization ensures atomic state transitions without deadlock or recursive re-entrancy.
- Room transactions execute atomically via `DoggoDao`.
- No sensitive user data or tokens exposed in shared contracts or logs.

---

## 4. Warnings List (High Priority Findings)

### Warning 1: Unbounded initial await in observer coroutine tests
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt:100,140,214` and `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-store-sqlite-test.kt:358`
- **Impact:** While target condition convergence is properly bounded by `withTimeout(3000)`, the preceding `initialEmitted.await()` runs directly under `runBlocking` without a timeout. If an unexpected regression breaks initial collection emission, the test runner would block indefinitely rather than failing fast.
- **Recommendation:** Enclose `initialEmitted.await()` within `withTimeout(3000)` and call `collectJob.cancelAndJoin()` in the `finally` block before cleaning up database resources:
  ```kotlin
  try {
      withTimeout(3000) { initialEmitted.await() }
      store.setCompletedLocally("t1", completed = true, nowEpochMs = 15_000L)
      withTimeout(3000) { targetReached.await() }
  } finally {
      collectJob.cancelAndJoin()
  }
  ```

### Warning 2: Strict dependency provenance remains an external gate
- **Location:** `gradle/verification-metadata.xml:1`, `androidApp/gradle.lockfile:1`
- **Impact:** `androidApp/gradle.lockfile` pins dependency versions, but does not provide cryptographic artifact authentication. Glance 1.2.0 artifacts lack checksum entries in `gradle/verification-metadata.xml`. Running Gradle without `--dependency-verification=off` fails due to the pre-existing 149-checksum baseline hold on buildscript tooling.
- **Recommendation:** Retain `--dependency-verification=off` as an explicit disclosed operational constraint until the root repository checksum baseline is regenerated. Do not classify supply-chain provenance as 100% verified prior to that update.

---

## 5. Medium Priority Improvements

### Medium 1: Redundant primary-key lookup in `setCompletedLocally`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:426,430`
- **Observation:** `setCompletedLocally` queries `dao.getServerBase(id)` to guard against server tombstones, and then immediately invokes `loadTodoLocked(id)` which executes `dao.getServerBase(id)` a second time.
- **Recommendation:** Pass the pre-fetched `existingBase` into `loadTodoLocked(id, existingBase)` to avoid the duplicate SQLite query:
  ```kotlin
  val existingBase = dao.getServerBase(id)
  if (existingBase != null && existingBase.deleted) {
      return@withMutationLock CompletionChange.Missing
  }
  val current = loadTodoLocked(id, existingBase) ?: return@withMutationLock CompletionChange.Missing
  ```

### Medium 2: Test name overstates concurrent race guarantee
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-store-sqlite-test.kt:258`
- **Observation:** `testSetCompletedLocallyPreservesConcurrentlyChangedFields` sets up a task on `server_base` and asserts that `setCompletedLocally` preserves existing non-conflicting fields (deadline, reminder, token). The test executes sequentially without competing threads.
- **Recommendation:** Rename to `testSetCompletedLocallyPreservesExistingTemporalFields` to avoid conflating sequential field preservation with concurrent race resolution.

### Medium 3: Direct DAO manipulation under `withMutationLock` tests invalidation dispatch rather than full sync engine
- **Location:** `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt:144-148,218-222`
- **Observation:** Promotion and conflict injection tests simulate sync by calling `dao.upsertServerBase` directly inside `withMutationLock`. This validates that `withMutationLock` triggers `taskInvalidationFlow`, but does not test the sync engine or conflict resolver directly.

---

## 6. Low Priority Suggestions

### Suggestion 1: Eliminate duplicated lock acquisition block in `withMutationLock`
- **Location:** `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:73-80,83-90`
- **Observation:** The inner lock execution and invalidation emission logic (`mutex.withLock { check(!isFrozen); val result = block(); ... }`) is duplicated in both branches of `if (gate != null)`. Extracting the inner invocation into a private inline helper reduces code duplication.

---

## 7. Positive Observations
1. **Targeted Tombstone Guard:** The `existingBase.deleted` early return cleanly avoids `IllegalStateException` and prevents outbox pollution while preserving uncommitted local drafts.
2. **Zero-Allocation Projection:** `selectNextDeadline` maintains $O(N)$ runtime and $O(1)$ allocations via the static `DEADLINE_COMPARATOR`.
3. **Single-Pass Partitioning:** `partitionSurfaceTodos` partitions tasks into incomplete/completed in a single pass while preserving encounter order and filtering deleted items.
4. **Invalidation Hygiene:** `withMutationLock` suppresses invalidations for `Unchanged` and `Missing` completions, avoiding spurious database re-queries.
5. **Contract Freezing:** Platform-agnostic contracts (`TaskSurfaceAccess`, `SurfaceReadiness`, `TaskSections`) provide clean, stable boundaries unblocking downstream phases.

---

## 8. Recommended Actions
1. **Bound Initial Observer Await:** Enclose `initialEmitted.await()` in `withTimeout(3000)` and use `cancelAndJoin()` across observer SQLite tests.
2. **Pass Pre-fetched Base to `loadTodoLocked`:** Eliminate duplicate `getServerBase(id)` query in `setCompletedLocally`.
3. **Rename Test:** Rename `testSetCompletedLocallyPreservesConcurrentlyChangedFields` to reflect sequential field preservation.
4. **Maintain Dependency Provenance Gate:** Keep `--dependency-verification=off` explicitly tracked until the project-wide checksum baseline is updated.
5. **Proceed with Leaf Phases:** Phase 01 shared foundation is robust and ready for downstream integration (Phase 02 Android Widget, Phase 03 Android Notification, Phase 04 Desktop Compact UI).

---

## 9. Metrics
- **Review Score:** 9.4 / 10 (Conditional Pass — Terminal Handoff Authorized)
- **Files Reviewed:** 13
- **Lines of Code Analyzed:** ~1,680 LOC
- **Critical Issues:** 0
- **Warnings (High Priority):** 2
- **Medium Improvements:** 3
- **Low Suggestions:** 1
- **Gradle Test Suite:** 307/307 PASS (231 shared JVM across 31 classes, 64 desktop across 10 classes, 12 Android unit across 4 classes)
- **Tooling & Security Scripts:** 18/18 fixture smoke, 6/6 source security diagnostics, 8/8 strict-v2 cutover self-test

---

## 10. Unresolved Questions
1. Is there an scheduled milestone to regenerate `gradle/verification-metadata.xml` for AGP 8.7.3 and Kotlin 2.1.10 buildscript tooling, enabling strict dependency verification repository-wide?
