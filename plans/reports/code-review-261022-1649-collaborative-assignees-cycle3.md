# Code Review Report: Collaborative Task Assignees (Review Cycle 3)

## Code Review Summary

### Scope
- **Files reviewed:**
  - `doggo` (Desktop / Android / Shared Multiplatform):
    - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`
    - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`
    - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt`
    - `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt`
    - `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt`
    - `androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-actions.kt`
    - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt`
    - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt`
    - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-data-contract.kt`
    - `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/task-assignee-workflow-test.kt`
  - `glean-hub` (Sync Plugin, Engine, Server, Admin UI):
    - `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs`
    - `glean-hub-plugin/glean-hub-sync/src/services/app_service.rs`
    - `glean-hub-plugin/glean-hub-sync/src/services/assignment_authorization.rs`
    - `glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs`
    - `glean-hub-plugin/glean-hub-sync/src/models/app.rs`
    - `glean-hub-plugin/glean-hub-sync/src/models/assignment_fence.rs`
    - `glean-hub-plugin/glean-hub-sync/tests/task_assignment_integration.rs`
    - `glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs`
    - `glean-hub-app/src/services/sync-admin-service.ts`
- **Lines of code analyzed:** ~17,500 lines across Kotlin, Rust, and TypeScript.
- **Review focus:** Verification of Review Cycle 2 critical remediations and secondary UI handler affirmative access gating:
  1. Stale policy authorization race in `push_sync_collaborative` (`sync_service.rs`).
  2. Non-transactional schema publication & fence invalidation (`app_service.rs`).
  3. Checkpoint filter cursor comparison mismatch on `ObjectId` (`sync_service.rs`).
  4. Affirmative access gating in secondary UI handlers (`main.kt`, `session-view-model.kt`, `task-surface-access.kt`, `sticky-task-content.kt`).
- **Updated plans:** `plans/261021-1552-tabbyrush-task-assignees/plan.md`.

---

### Overall Assessment
**Overall Score: 9.5 / 10**
**Critical Issues: 0**
**Status: Targeted remediation review complete; release remains blocked pending runtime integration acceptance (G2–G6).**

Cycle 3 successfully resolves the 3 critical architectural and consistency issues identified in Cycle 2:
1. **In-Transaction Policy Re-Verification:** Inside `sync_service.rs:957–975`, `push_sync_collaborative` queries `_schemas` within the active MongoDB transaction session (`.session(&mut session)`) immediately following serialized guard acquisition on `assignment_fences`. Incoming pushes operate against the authoritative committed schema and policy within that guarded transaction.
2. **Transactional Schema & Fence Publication:** In `app_service.rs:201–224`, `update_schema` executes both the `_schemas` update and the `assignment_fences` `accessRevision` increment within a shared MongoDB client session and transaction (`session.start_transaction()` / `session.commit_transaction()`), guaranteeing that schema changes and fence invalidations commit or abort together atomically.
3. **Strict Checkpoint Cursor Pagination:** In `sync_service.rs:1840–1846`, the `$or` filter branch for `ObjectId` cursors strictly evaluates `{ "syncedAt": { "$gt": &cp_time } }` or `{ "syncedAt": &cp_time, "_id": { "$gt": oid } }`. Removing the extraneous `rowId > cp.id` clause aligns the predicate directly with the collection sort order `{"syncedAt": 1, "_id": 1}`.
4. **Affirmative Access Gating in Core Handlers:** Desktop `main.kt` and Android `session-view-model.kt` uniformly enforce affirmative `isOwner || (accessState?.can... == true)` checks across `openEdit`, `submitEditor`, `RequestDelete`, `toggleCompleted`, and `confirmDelete`. Android `task-surface-access.kt` affirmatively enforces completion permissions.

---

## 1. Overall Score
**9.5 / 10** (Status: All Cycle 2 Critical Issues Resolved; Runtime Acceptance G2–G6 Pending).

---

## 2. Critical Issues (MUST FIX)
**Count: 0**
None. All 3 critical issues from Cycle 2 are verified resolved in source.

---

## 3. High Priority Findings / Warnings (SHOULD FIX)
**Count: 3**

### 1. Fail-Open Access Fallback in Desktop Sticky Note Row
- **Location:** `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt:520`
- **Impact:**
  `val canEdit = item.todo.accessState?.canEditContent ?: true` falls back to `true` when `accessState` is null. While `RoomAccountStore` populates `creatorAccessState` for owner tasks and rejects unauthorized mutations at the storage boundary (`store.setCompletedLocally`), unowned foreign tasks lacking access metadata could still present interactive checkboxes in the sticky note surface. Store-level rejection prevents database corruption, but the UI affordance should fail closed.
- **Remediation:**
  Supply authenticated `currentUserId` to `StickyTaskContent` to apply explicit affirmative gating (`val isOwner = item.todo.taskRef.ownerUserId == currentUserId; val canEdit = if (isOwner) true else (item.todo.accessState?.canEditContent == true)`), or default null accessState to `false`.

### 2. N+1 App Membership Queries During Collaborative Pull Access Checks
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs:178–200`
- **Impact:**
  Inside `execute_access_checks`, each iteration invokes `determine_effective_task_access`, which calls `verify_user_app_membership(db, actor_user_id, app_id).await?`. For requests checking the maximum of 100 tasks, this issues 100 identical queries against the `users` collection for the requesting user.
- **Remediation:**
  Verify user app membership once at the entry of `execute_access_checks`. If membership check fails, map all requested references to `"unavailable"` immediately and return.

### 3. Missing Integration Test for Concurrent Insert Collision Recovery
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:1166–1172, 1406–1412`
- **Impact:**
  `is_duplicate_key_error` accurately handles `ErrorKind::Write` for write errors and concern errors (code 11000) and aborts the transaction cleanly. However, an end-to-end integration test validating concurrent slot/task insertion collision recovery against a replica set remains absent.
- **Remediation:**
  Add a targeted test in `task_assignment_integration.rs` simulating duplicate key `WriteError` (code 11000) during collaborative push to verify transaction abort and `ConflictInfo` generation without session corruption.

---

## 4. Medium & Low Priority Suggestions (NICE TO HAVE)
**Count: 4**

### 1. Consolidate Duplicate Constant `ASSIGNMENT_TABLE_NAME`
- **Location:** `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt:8` vs `task-data-contract.kt:5`
- **Impact:** Redundant constant declarations across shared contracts. Reference `TaskDataContract.ASSIGNMENT_TABLE_NAME` directly.

### 2. Unused Variable Compiler Warning in `reqwest_http.rs` Tests
- **Location:** `glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs:88:13`
- **Impact:** `let client = ReqwestHttpClient::new();` emits unused variable compiler warning. Replace with `let _client = ...`.

### 3. Query Predicate Optimization in Collaborative Pull
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:1810–1824`
- **Impact:** Generates separate `$or` branches for each parent and assignment slot. Group parent IDs using `$in` to minimize MongoDB query planner complexity for users with many assignments.

### 4. Redundant Condition Warning in Task Assignee Workflow Test
- **Location:** `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/task-assignee-workflow-test.kt:85:13`
- **Impact:** Compiler warning: `Condition is always 'false'` because `directAssignee` is set to `null` immediately prior to the `when` expression.

---

## Positive Observations
1. **Authoritative In-Session Policy Reload:** `push_sync_collaborative` eliminates the stale authorization race by querying `_schemas` within the active transaction session following guard acquisition.
2. **Transactional Administrative Consistency:** Schema mutations and fence revisions now commit atomically in a single multi-document transaction, preventing partition or crash inconsistencies.
3. **Strict Pagination Cursor Semantics:** Removal of the invalid `rowId` comparison aligns the collaborative pull cursor strictly with `(syncedAt, _id)` query ordering.
4. **Robust Affirmative UI Access Gating:** Handlers in Desktop `main.kt` (`openEdit`, `submitEditor`, `RequestDelete`, `toggleCompleted`, `confirmDelete`) and Android `session-view-model.kt` strictly gate modifications on `isOwner || capability == true`.
5. **Cross-Platform Automated Suites Passing:** All executed automated unit test suites pass cleanly with zero compiler or typecheck errors.

---

## Recommended Actions
1. **Pass `currentUserId` to Desktop Sticky Content:** Align `sticky-task-content.kt:520` with `main.kt` affirmative permission model.
2. **Hoist Membership Verification:** Check `verify_user_app_membership` once at the entry of `execute_access_checks` in `assignment_reconciliation.rs:178`.
3. **Add Collision Integration Test:** Implement a concurrent slot insert test in `task_assignment_integration.rs` to exercise `is_duplicate_key_error` transaction recovery against a live replica set.
4. **Proceed to Main Integration Verification:** Advance to G2–G6 integration acceptance gates (real replica-set Hub API smoke, Room v5 coordinator runtime smoke, connected Android device acceptance, desktop interactive smoke, and release security audit) per release protocol.

---

## Verification Evidence & Metrics

### Executed Verification Suites
- **doggo Shared JVM Suite (`:shared:jvmTest --rerun-tasks`):**
  179 / 179 tests PASSED (43.65s). Covers canonical access, Room v5 migration, outbox reconciliation, scheduled reminders, and workflow tests.
- **doggo Desktop Unit Suite (`:desktopApp:test`):**
  95 / 95 tests PASSED (20.68s). Covers sticky task surfaces, leave protocol gates, reminder scheduler, and Windows DPAPI vault.
- **doggo Android Unit Suite (`:androidApp:testDebugUnitTest`):**
  40 / 40 tests PASSED (20.68s). Covers task widget models, surface access, notification adapters, and intent factories.
- **glean-hub-sync Library Suite (`cargo test --lib`):**
  31 / 31 unit tests PASSED (30.02s). Note: excludes `tests/task_assignment_integration.rs` which requires disposable MongoDB instance (`MONGO_TEST_ALLOW_DISPOSABLE=1`).
- **glean-hub-sync-engine Suite (`cargo test`):**
  6 / 6 tests PASSED (0.13s). Covers checkpoint ordering, protocol serialization, and sharing policy tests.
- **glean-hub-app Unit Suite (`npm run test:unit`):**
  51 / 51 tests PASSED (0.21s). Covers app registry, announcement state, and sync admin service.
- **glean-hub-app TypeScript Typecheck (`tsc --noEmit`):**
  PASSED with 0 type errors (23.93s).

### Metric Counts
- **Critical Issues Identified:** 0 (all 3 Cycle 2 critical issues resolved).
- **Warnings Identified:** 3 (sticky UI fallback permissiveness, N+1 pull membership queries, missing collision test).
- **Suggestions Identified:** 4 (duplicate constant, unused test variable, pull query `$in` optimization, test warning).

---

## Scope & Operational Context
Targeted remediation review complete for Cycle 3. The 3 critical code-level issues identified in Cycle 2 have been verified resolved. Outstanding warnings and suggestions are documented above. Live multi-document replica-set integration smoke (G2), native Android runtime/Glance widget acceptance (G4), and desktop interactive surface acceptance (G5) remain pending under Main coordination. Release remains blocked; production migration is unauthorized.
