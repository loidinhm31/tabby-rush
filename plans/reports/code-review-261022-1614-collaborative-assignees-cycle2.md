# Code Review Report: Collaborative Task Assignees (Review Cycle 2)

## Code Review Summary

### Scope
- **Files reviewed:**
  - `doggo` (Desktop / Android / Shared Multiplatform):
    - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt`
    - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`
    - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces.kt`
    - `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt`
    - `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt`
    - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt`
    - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-access-contracts.kt`
    - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt`
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
- **Lines of code analyzed:** ~17,200 lines across Kotlin, Rust, and TypeScript.
- **Review focus:** Verification of Review Cycle 2 remediation items:
  1. `is_duplicate_key_error` matching `ErrorKind::Write(WriteFailure::WriteError/WriteConcernError)` in `sync_service.rs`.
  2. Affirmative access gating (`isOwner || canEditContent`) in desktop `main.kt` and android `session-view-model.kt`.
  3. Policy update `accessRevision` invalidation in `app_service.rs`.
- **Updated plans:** `plans/261021-1552-tabbyrush-task-assignees/plan.md`.

---

### Overall Assessment
**Overall Score: 8.0 / 10**

Cycle 2 exhibits positive remediation on the targeted items:
1. **`is_duplicate_key_error` Error Classification:** Fixed. In `sync_service.rs:167–182`, `ErrorKind::Write` is matched for both `WriteError` and `WriteConcernError` (code 11000). However, end-to-end transaction collision recovery has not yet been exercised by automated integration tests.
2. **Affirmative Access Gating:** Fixed in core editor workflows (`openEdit` and `submitEditor` in both desktop `main.kt:761–764, 901–903` and android `session-view-model.kt:297–300, 500–502`). However, secondary UI action handlers (`toggleCompleted`, `RequestDelete`, `confirmDelete`, and sticky `CompactTaskRow`) still contain inverted/fail-open checks (`== false` or `?: true`).
3. **Policy Update Invalidation:** In `app_service.rs:196–210`, `policy_changed` was added, guaranteeing `accessRevision` increments when an assignment policy is modified or removed. However, schema publication and fence revision advancement remain separate, un-transactional writes, creating a crash window and racing with concurrent pushes.

Release remains blocked pending resolution of critical authorization and consistency invariants.

---

## 1. Overall Score
**8.0 / 10** (Status: Partially Resolved; Release blocked pending Critical Issues).

---

## 2. Critical Issues (MUST FIX)

### 1. Stale Policy Authorization Race in Collaborative Push
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:908–955`
- **Impact:**
  `push_sync_collaborative` receives references to `app_schema`, `sharing`, and `policy` that are loaded in `push_sync` before the transaction begins.
  `acquire_guard_session` is executed subsequently inside the transaction.
  If an administrator modifies or disables an assignment policy concurrently, the push operation proceeds to authorize incoming records using the obsolete in-memory policy and schema.
- **Remediation:**
  Re-verify or reload the authoritative `AppSchema` and `AssignmentFence` inside the active transaction session immediately following `acquire_guard_session`. Abort the transaction if `access_revision` or policy has changed.

### 2. Non-Transactional Schema Publication and Fence Invalidation
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/app_service.rs:165–176, 196–210`
- **Impact:**
  `_schemas.find_one_and_update(...)` and `assignment_fences.update_one(...)` run as independent, detached database operations without a shared session/transaction.
  A server crash, process kill, or network partition between lines 198 and 203 persists updated table schemas or removed assignment policies while failing to increment `accessRevision`. Connected clients retain obsolete cached access states indefinitely.
- **Remediation:**
  Wrap the schema mutation and fence revision increment in a shared MongoDB client session/transaction:
  ```rust
  let mut session = self.db.client().start_session().await?;
  session.start_transaction().await?;
  // update schema within session
  // increment accessRevision within session
  session.commit_transaction().await?;
  ```

### 3. Checkpoint Filter Inconsistency in Collaborative Pull (`rowId > cp.id` vs `ObjectId`)
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:1820–1827`
- **Impact:**
  ```rust
  let id_filter = if let Ok(oid) = ObjectId::parse_str(&cp.id) {
      doc! {
          "$or": [
              { "syncedAt": { "$gt": &cp_time } },
              { "syncedAt": &cp_time, "_id": { "$gt": oid } },
              { "syncedAt": &cp_time, "rowId": { "$gt": &cp.id } }
          ]
      }
  ```
  The database query sort order is strictly `.sort(doc! { "syncedAt": 1, "_id": 1 })`.
  When `cp.id` is an `ObjectId` hex string, the third branch compares client-assigned `rowId` (UUID) against `cp.id` (ObjectId hex). For records having identical `syncedAt` timestamps where `_id <= oid`, any record whose `rowId` is lexicographically higher than the hex string evaluates to true, causing repeated duplicate fetches across pagination pages.
- **Remediation:**
  Remove `{ "syncedAt": &cp_time, "rowId": { "$gt": &cp.id } }` from the `ObjectId` branch:
  ```rust
  let id_filter = if let Ok(oid) = ObjectId::parse_str(&cp.id) {
      doc! {
          "$or": [
              { "syncedAt": { "$gt": &cp_time } },
              { "syncedAt": &cp_time, "_id": { "$gt": oid } }
          ]
      }
  ```

---

## 3. Warnings (SHOULD FIX)

### 1. Inverted and Fail-Open Access Checks in UI Surface Handlers
- **Location:**
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:629` (`RequestDelete`)
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:928` (`toggleCompleted`)
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:944` (`confirmDelete`)
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt:520` (`CompactTaskRow`)
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:198` (`RequestDelete`)
- **Impact:**
  - In `desktopApp/main.kt:928`: `if (current.accessState?.capabilities?.editContent == false)` rejects only explicit `false`. If `accessState` is null on a foreign task, the check does not fire, attempting mutation before being rejected silently by `RoomAccountStore.setCompletedLocally`.
  - In `desktopApp/sticky-task-content.kt:520`: `val canEdit = item.todo.accessState?.capabilities?.editContent ?: true` defaults to `true` when `accessState` is null, rendering clickable checkboxes for foreign unassigned tasks.
  - In delete action handlers, checking `capabilities.deleteTask == false` permits opening delete confirmations for unowned tasks when `accessState` is null.
- **Remediation:**
  Adopt uniform affirmative gating across all UI action handlers:
  ```kotlin
  val isOwner = current.taskRef.ownerUserId == myUserId
  val canEdit = if (isOwner) true else (current.accessState?.canEditContent == true)
  if (!canEdit) { ... return }
  ```

### 2. N+1 Database Queries in Collaborative Pull Access Checks
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs:178–200`
- **Impact:**
  For each record in `access_checks`, `determine_effective_task_access` invokes `verify_user_app_membership(db, actor_user_id, app_id).await?`. A pull request checking 100 tasks generates 100 redundant MongoDB queries to the `users` collection for the same user and app.
- **Remediation:**
  Check app membership once at the start of `execute_access_checks`. If membership verification fails, map all requested references to `"unavailable"` and return immediately.

### 3. Missing Integration Test for Concurrent Insert Collision Recovery
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:1146–1179, 1386–1420`
- **Impact:**
  While `is_duplicate_key_error` now correctly matches `ErrorKind::Write`, there is no integration test validating that concurrent slot/task insertion aborts cleanly and returns `ConflictInfo` without poisoning the session or crashing with an unhandled driver error.
- **Remediation:**
  Add a dedicated unit/integration test simulating duplicate key `WriteError` (code 11000) during collaborative push to verify transaction recovery.

---

## 4. Suggestions (NICE TO HAVE)

### 1. Consolidate Duplicate Constant `ASSIGNMENT_TABLE_NAME`
- **Location:** `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt:8` vs `task-data-contract.kt:5`
- **Impact:** Redundant constant declaration across shared contract files. Reference `TaskDataContract.ASSIGNMENT_TABLE_NAME` directly.

### 2. Unused Variable Compiler Warning in `reqwest_http.rs`
- **Location:** `glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs:88:13`
- **Impact:** `let client = ReqwestHttpClient::new();` emits compiler warning `unused variable: client`. Replace with `let _client = ...`.

### 3. Query Predicate Optimization in Collaborative Pull
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:1777–1804`
- **Impact:** Generates three `$or` clauses per accessible parent. For users with large numbers of assigned tasks, group branches using `$in` to minimize MongoDB query planner complexity.

### 4. Redundant Condition Compiler Warning in UI Workflow Test
- **Location:** `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/task-assignee-workflow-test.kt:85:13`
- **Impact:** Compiler warning: `Condition is always 'false'` due to preceding `directAssignee = null`. Simplify branch condition.

---

## Positive Observations
1. **Accurate Error Classification:** `is_duplicate_key_error` in `sync_service.rs` now covers `ErrorKind::Write` for both `WriteError` and `WriteConcernError` with error code 11000, aligning with MongoDB driver 3.5.0.
2. **Affirmative Editor Gating:** `openEdit` and `submitEditor` in both Desktop (`main.kt`) and Android (`session-view-model.kt`) enforce affirmative `(isOwner || canEditContent)` checks.
3. **Fence Invalidation on Policy Removal:** In `app_service.rs`, `policy_changed` triggers fence update and `accessRevision` increment when assignment policy is modified or disabled.
4. **Strong Store-Level Invariants:** `RoomAccountStore` robustly fails closed on unauthorized writes across `saveTodoLocally`, `deleteTodoLocally`, `setCompletedLocally`, and `assignTaskLocally`.
5. **Automated Suite Stability:** All unit test suites pass cleanly:
   - doggo `:shared:jvmTest`, `:desktopApp:test`, `:androidApp:testDebugUnitTest` (494+ tests passing).
   - glean-hub `glean-hub-sync` (31 tests passing), `glean-hub-sync-engine` (6 tests passing).
   - glean-hub-app unit tests (51 tests passing), TypeScript `tsc --noEmit` (0 errors).

---

## Recommended Actions (Prioritized)
1. **Re-check Policy within Collaborative Push Transaction:** In `sync_service.rs:955`, re-verify stored schema and fence revision within `session` to eliminate stale policy authorization races.
2. **Enforce Transaction on Schema & Fence Updates:** In `app_service.rs:196–210`, wrap `_schemas` update and `assignment_fences` revision increment in a shared MongoDB transaction.
3. **Correct Checkpoint Filter Cursor Comparison:** In `sync_service.rs:1820–1827`, eliminate `{ "syncedAt": &cp_time, "rowId": { "$gt": &cp.id } }` from the `ObjectId` filter branch.
4. **Align Secondary UI Handlers with Affirmative Permissions:** Update `toggleCompleted`, `RequestDelete`, `confirmDelete`, and sticky `CompactTaskRow` to use affirmative status-aware capability checks.
5. **Batch App Membership Query:** Check membership once at entry of `execute_access_checks` in `assignment_reconciliation.rs:178`.

---

## Metrics
- **Type Coverage:** 100% (TypeScript: 0 errors; Kotlin multiplatform: clean).
- **Test Results:** 494+ tests passing across all active automated test suites.
- **Critical Issues Identified:** 3 (stale policy authorization race, non-transactional schema/fence update, checkpoint cursor filter mismatch).
- **Warnings Identified:** 3 (inverted/fail-open secondary UI checks, N+1 app membership queries, unverified transaction collision recovery).
- **Suggestions Identified:** 4 (duplicate constant, unused variable, pull query optimization, test condition warning).

---

## Unresolved Questions
1. Should `pull_sync_collaborative` migrate to pure cursor pagination using solely `(syncedAt, _id)` without secondary `rowId` branching?
2. Does the MongoDB cluster configuration mandate replica sets in all deployment environments to support multi-document transactions during schema administration?
