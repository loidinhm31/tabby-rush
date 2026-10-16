# Phase 01 Foundation Evidence Report

**Status:** DELIVERED / CONDITIONAL PASS — GATES PENDING
**Date:** 2026-10-16
**Author:** Phase 01 Foundation Owner (Group A)

---

## 1. Executed & Verified Results

### 1.1 Shared Contracts & Projections Delivered
- **`account-store.kt`**: Added `observeTodos(): Flow<List<Todo>>` and `suspend fun setCompletedLocally(id: String, completed: Boolean, nowEpochMs: Long): CompletionChange`.
- **`task-surface-contracts.kt`**: Declared `CompletionChange`, `SurfaceAccountToken`, `SurfaceReadiness`, `TaskSections`, `SurfaceSnapshot`, `SurfaceResult`, `TaskSurfaceAccess`.
- **`task-surface-projector.kt`**: Implemented single-pass projections without intermediate filtered lists: `partitionSurfaceTodos` (omits deleted, retains `createdAt DESC, id DESC`) and `selectNextDeadline` (overdue/upcoming priority, tie-breaking by `overdueEpochMs ASC, taskId ASC`, inclusion of unresolved conflicts, independent of reminder settings).
- **`task-surface-host.kt`** (`:androidApp`): Declared `TaskSurfaceHostOwner`, `AndroidTaskSurfaceHost`, `SurfaceRefreshReason`.
- **`sticky-task-model.kt`** (`:desktopApp`): Declared `StickyQuickAddState`, `StickyTaskState`, `StickyTaskAction`.

### 1.2 Authoritative RoomAccountStore Hardening
- Extracted private `projectSingleTodo`, `loadTodoLocked(id)`, `loadTodosLocked()`, and `saveTodoLocallyLocked(todo)`.
- Extracted static `PENDING_OUTBOX_STATES` set to eliminate per-task allocations.
- Implemented `setCompletedLocally`: single-lock atomic transaction under `withMutationLock`, fresh lookup, early return `CompletionChange.Missing` on tombstone/deleted/absent tasks (including server tombstones with residual local overlays, preventing `IllegalStateException`), early return `CompletionChange.Unchanged` on duplicate state (no revision bump), preserves concurrent title/temporal metadata, monotonic `updatedAt = maxOf(nowEpochMs, current.updatedAt, current.createdAt)`, returns `CompletionChange.Changed`.
- Generalized private mutation invalidation flow (`taskInvalidationFlow`, replay = 1, extraBufferCapacity = 64, DROP_OLDEST); removed unused public `notifyScheduleChanged()`; no-op completions (`Unchanged`/`Missing`) skip invalidation.
- Implemented `observeTodos()` and `observePersistedSchedule()` sharing the unified invalidation signal.

### 1.3 Aligned Glance Candidate & Build Verification
- Added stable candidate `androidx.glance:glance:1.2.0`, `glance-appwidget:1.2.0`, `glance-material3:1.2.0` in `gradle/libs.versions.toml` and `:androidApp` dependencies.
- Verified compilation: `:androidApp:compileDebugKotlin` and `:desktopApp:compileKotlin` build cleanly with Kotlin 2.1.10 and Compose 1.7.3.
- Resolved and persisted dependency locking in `androidApp/gradle.lockfile`.

### 1.4 Automated Test Suite Execution (QA Reports Total: 307/307 PASS)
- **`TaskSurfaceProjectorTest`** (13/13 PASS): Empty list, partition separation and ordering, eligible deadline selection, overdue precedence, boundary equality, tie-breaking by taskId ASC, conflict preservation, reminder-disabled eligibility, date-only next-day overdue boundary.
- **`TaskSurfaceStoreSqliteTest`** (10/10 PASS): Cold replay, atomic state toggle, monotonic updatedAt, duplicate desired state no-op, missing record, tombstone missing without exception, tombstone with residual draft missing without exception, tombstone with residual outbox missing without exception, concurrent field preservation, SQLite reopen persistence, predicate-based observer timeout convergence.
- **`TaskSurfaceSyncSqliteTest`** (4/4 PASS): Reconcile accepted observer emission and status update, scan promotion under mutation lock, outbox conflict head emission and selection flag, concurrent title edit preservation.
- **`RoomAccountStoreTest`** (25/25 PASS): Extended with 2 regression tests asserting strict-v2 contract version and scheduleToken preservation on local completion.
- **Full Shared Suite (`:shared:jvmTest`)**: 231/231 PASS across 31 test classes.
- **Desktop Suite (`:desktopApp:test`)**: 64/64 PASS across 10 test classes.
- **Android Unit Suite (`:androidApp:testDebugUnitTest`)**: 12/12 PASS across 4 test classes.
- **Platform Tooling & Security**: 18/18 offline fixture smoke PASS, 8/8 rehearsal self-test PASS, 21/21 strict-v2 wire vectors (10 valid + 11 rejections) PASS, 6/6 source security diagnostics PASS.

---

## 2. Disclosed Supply-Chain & Integrity Holds

- **Tooling Verification Hold**: Executing Gradle builds without `--dependency-verification=off` fails at root project configuration due to exactly 149 unmatched classpath buildscript checksums in `gradle/verification-metadata.xml` (affecting AGP 8.7.3 and Kotlin 2.1.10 tooling jars). All Gradle verification in this phase was conducted under `--dependency-verification=off` for behavioral and compile proof.
- **Newly Added Glance Artifact Provenance**: Glance 1.2.0 candidate dependencies are locked by version pins in `androidApp/gradle.lockfile`. Cryptographic artifact authentication and verification metadata reconciliation remain an explicit release gate across all dependencies.

---

## 3. Pending Native Acceptance

- **Downstream Platform Integration**: Launcher widget rendering, system shade notification posting, and desktop window/tray mechanics remain pending implementation in downstream Phases 02–08.
- Compilation and SQLite tests evidence contract validity and persistence atomicity only; native surface UX and device acceptance remain to be proven by their respective owners.

---

## 4. Terminal Leaf Handoff

Phase 01 frozen contracts and dependency declarations are terminal:
- **Phase 02 (Android Widget)**: Unblocked to consume `TaskSurfaceHostOwner`, `AndroidTaskSurfaceHost`, `TaskSurfaceAccess`, `partitionSurfaceTodos`, and Glance 1.2.0.
- **Phase 03 (Android Notification)**: Unblocked to consume `TaskSurfaceAccess`, `selectNextDeadline`, `DeadlineSelection`, `DeadlineSelectionKey`.
- **Phase 04 (Desktop Compact)**: Unblocked to consume `StickyQuickAddState`, `StickyTaskState`, `StickyTaskAction`, `partitionSurfaceTodos`.
