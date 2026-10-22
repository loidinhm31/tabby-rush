# Code Review Report: Collaborative Task Assignees (Cycle 3)

## Code Review Summary

### Scope
- **Files reviewed:** 150+ modified and newly created files across `doggo` (`TabbyRush`) and `glean-hub` (`Glean Hub`), focusing on Cycle 2 issue resolutions, transactional session integrity, fail-closed authorization, editor state wiring, and full test suite verification.
  - **doggo (Android/Desktop/Shared):**
    - Contracts & Projections: `canonical-record-ref.kt`, `assignment-contracts.kt`, `task-access-contracts.kt`, `collaboration-store-contracts.kt`, `persisted-projection-inputs.kt`, `persisted-schedule-projector.kt`, `task-hierarchy-projector.kt`, `task-surface-projector.kt`, `todo-models.kt`, `todo-ui-state.kt`.
    - Persistence & Room: `room-account-store.kt`, `tabbyrush-dao.kt`, `tabbyrush-database.kt`, `database-migrations.kt`, `task-access-entity.kt`, `access-loss-evidence-entity.kt`, `access-check-staging-entity.kt`, `scan-staging-entity.kt`, `editor-draft-entity.kt`, `outbox-entity.kt`.
    - Native Sync & Isolation: `hub-sync-client.kt`, `account-sync-coordinator.kt`, `staged-full-scan.kt`, `outbox-reconciliation.kt`, `task-json-merge.kt`, `reminder-scheduling-coordinator.kt`.
    - Shared UI & State: `todo-editor.kt`, `todo-list.kt`, `tabbyrush-app.kt`, `task-assignee-picker.kt`, `conflict-review.kt`.
    - Android Runtime & Glance: `account-runtime-factory.kt`, `android-notification-adapter.kt`, `main-activity.kt`, `session-view-model.kt`, `tabbyrush-application.kt`, `task-surface-access.kt`, `task-surface-action-receiver.kt`, `task-widget.kt`.
    - Desktop: `main.kt`, `desktop-task-surfaces.kt`, `sticky-task-content.kt`, `desktop-reminder-scheduler.kt`, `windows-notification-adapter.kt`.
    - Scripts: `hub-schema-provision.mjs`, `hub-schema-provision.test.mjs`.
  - **glean-hub (Backend, Protocol, Admin):**
    - Protocol & Engine: `glean-hub-sync-engine/src/sharing.rs`, `protocol.rs`, `checkpoint.rs`, `conflict.rs`, `document.rs`.
    - Client & Types: `glean-hub-sync-client/src/client.rs`, `export_types.rs`, `reqwest_http.rs`.
    - Plugin: `glean-hub-sync/src/services/sync_service.rs`, `schema_validator.rs`, `app_service.rs`, `assignment_authorization.rs`, `assignment_reconciliation.rs`, `models/assignment_fence.rs`, `error.rs`, `tests/task_assignment_integration.rs`.
    - Admin UI: `glean-hub-app/src/services/sync-admin-service.ts`, `sync-admin-service.test.ts`, `app-registration.tsx`, `edit-app.tsx`.
- **Lines of code analyzed:** ~14,500 lines across Kotlin, Rust, and TypeScript.
- **Review focus:** Verification of 4 Cycle 2 issues, MongoDB transaction semantics & session reads, fail-closed authorization on foreign/unavailable records, assignment CAS slot versioning in UI, and multiplatform test execution.
- **Updated plans:** `plans/261021-1552-tabbyrush-task-assignees/plan.md`.

---

## Overall Assessment
**Overall Score: 7.5/10** (Previous Cycle 2 Score: 7/10, Cycle 1 Score: 5/10)

Key Cycle 2 resolutions successfully verified:
1. **Intra-Transaction MongoDB Session Reads:** `find_canonical_record_session`, `find_live_parent_task_session`, and `determine_effective_task_access_session` implemented with `&mut ClientSession` and attached to 10 out of 11 call sites in `push_sync_collaborative`.
2. **Assignment Clear Fallback Bug Fixed:** `RoomAccountStore.assignTaskLocally` (`room-account-store.kt:1003–1007`) now checks `if (latestSlotOp != null)` before evaluating `slotBase`, ensuring re-assigning after a pending clear creates an outbox update rather than returning `Unchanged`.
3. **Outbox Disambiguation Filtered:** `account-sync-coordinator.kt:280` now filters `it.tableName == "todos"` alongside `ownerUserId`, preventing collision with assignment slots sharing identical row IDs.
4. **All 625 Tests Green:** Full multiplatform test suites executed live with 0 failures across JVM (324), Desktop (145), Android (60), Cargo (34), Pnpm (50), and Node (12).

However, code approval is gated by 3 Critical issues and 3 Warnings:
- **Critical 1 (Fail-Open Authorization):** Foreign tasks without cached access default to `TaskCapabilities.OWNER` in UI (`todo-list.kt:310`), and `room-account-store.kt` checks raw capabilities without verifying `isAccessible`.
- **Critical 2 (Editor CAS Failure & Blank Assignee):** `todo-editor.kt:217` hardcodes `expectedSlotVersion = 0L`, causing `VersionConflict` on edits to existing assigned tasks, while `openEdit` in both Android and Desktop fails to populate `assigneeUserId` in `EditorState`.
- **Critical 3 (MongoDB Transaction Abort on Collision):** Duplicate key collisions on `insert_one` in `sync_service.rs:1126, 1344` abort MongoDB transactions, making session reloads invalid within the same aborted transaction; line 1346 still executes out-of-session `find_canonical_record`.

---

## Critical Issues ([3]): MUST FIX

### 1. Fail-Open Permissions on Foreign and Unavailable Tasks
- **Locations:**
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt:310`
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt:640, 816, 948, 982`
- **Impact:**
  - In `todo-list.kt:310`:
    ```kotlin
    val capabilities = todo.accessState?.capabilities ?: TaskCapabilities.OWNER
    ```
    If `todo.accessState` is null on a foreign task (`todo.taskRef.ownerUserId != currentUserId`), it defaults to full `OWNER` permissions, rendering edit/delete/subtask controls to unauthorized users.
  - In `room-account-store.kt`:
    `setCompletedLocally` (line 948) checks:
    ```kotlin
    val canEdit = taskRef.ownerUserId == userId || access?.capabilities?.editContent == true
    ```
    If `access.status == TaskAccessStatus.Unavailable`, `access.capabilities.editContent` may remain true on the entity before purge. The mutation is admitted instead of failing closed. Identical issues exist at lines 640 (`saveDraft`), 816 (`deleteTaskLocally`), and 982 (`assignTaskLocally`).
- **Remediation:**
  - In `todo-list.kt:310`, check ownership before defaulting to `OWNER`:
    ```kotlin
    val isOwner = todo.taskRef.ownerUserId == currentUserId
    val capabilities = todo.accessState?.capabilities ?: if (isOwner) TaskCapabilities.OWNER else TaskCapabilities.NONE
    ```
  - In `room-account-store.kt`, use `TaskAccessState` computed properties:
    - Line 640: `access?.canEditContent == true`
    - Line 816: `access?.canDeleteTask == true`
    - Line 948: `access?.canEditContent == true`
    - Line 982: `access?.canManageAssignment == true`

### 2. Hardcoded `expectedSlotVersion = 0L` and Unpopulated Assignee in Editor
- **Locations:**
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt:217`
  - `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt:23–25`
  - `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:298, 321`
  - `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:776`
- **Impact:**
  - In `todo-editor.kt:217`:
    ```kotlin
    TaskAssigneePicker(
        assigneeUserId = editor.assigneeUserId,
        inheritedAssigneeUserId = null,
        canManageAssignment = editor.canManageAssignment,
        isInitialAssignmentForNewTask = isCreating,
        expectedSlotVersion = 0L,
        taskRef = editor.taskRef,
    ```
    When modifying an existing assigned task whose slot version is `1L` (or higher), the dispatched `AssignmentIntent` carries `expectedSlotVersion = 0L`. In `RoomAccountStore.assignTaskLocally:996`, `intent.expectedSlotVersion != currentSlotVersion` triggers `AssignmentSlotResult.VersionConflict`. The user can NEVER reassign or clear an assignee on an existing task.
  - In `SessionViewModel.openEdit` and `DesktopController.openEdit`, `EditorState` is constructed without passing `assigneeUserId` or `expectedSlotVersion`. The editor opens with a blank assignee even when a direct assignee exists.
- **Remediation:**
  - Add `expectedSlotVersion: Long = 0L` and `inheritedAssigneeUserId: String? = null` to `EditorState`.
  - In `openEdit`, query the store for `latestSlotOp` or `slotBase` to populate `assigneeUserId` and `expectedSlotVersion`.
  - Pass `editor.expectedSlotVersion` and `editor.inheritedAssigneeUserId` into `TaskAssigneePicker`.

### 3. MongoDB Transaction Abort on Insert Collision & Omitted Session Read
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:1126, 1344, 1346`
- **Impact:**
  - In `sync_service.rs`:
    ```rust
    match collection.insert_one(&task_doc).session(&mut session).await {
        Ok(_) => { ... }
        Err(_) => {
            let reloaded = crate::services::AssignmentAuthorization::find_canonical_record(...)
    ```
    1. Line 1346 calls `find_canonical_record` instead of `find_canonical_record_session(&self.db, &collection_name, app_id, actor_user_id, &sharing.parent_table, &record.row_id, &mut session)`, breaking transactional snapshot isolation.
    2. Under MongoDB multi-document transactions, write errors (such as duplicate key code 11000 on unique indexes) abort the underlying transaction. Subsequent operations on `session` fail with `TransactionAborted` / `NoSuchTransaction`. Catching `Err` and continuing inside the same session causes subsequent operations or `commit_transaction` to fail.
    3. The entire push request batch runs in a single transaction (`lines 934-1604`). A collision on one record aborts the entire multi-record batch instead of permitting partial acceptance.
- **Remediation:**
  - Replace line 1346 with `find_canonical_record_session`.
  - Use per-record transaction boundaries or catch abort-inducing write collisions, restart transaction, and reload the conflicting version outside the aborted session.

---

## High Priority Findings ([3]): SHOULD FIX

### 1. N+1 App Membership Queries in Batch Access Checks
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs:178–200`
- **Impact:** In `execute_access_checks`, each canonical ref loops into `determine_effective_task_access`, which invokes `verify_user_app_membership(db, actor_user_id, app_id)`. For 100 access check keys, 100 identical queries are dispatched to the `users` collection.
- **Remediation:** Call `verify_user_app_membership` once at the entry of `execute_access_checks`. If false, return `unavailable` for all checked keys immediately.

### 2. Missing In-Transaction Guard Reread for Schema and Policy
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:901, 938`
- **Impact:** `actor_user_id` app membership check runs before `session.start_transaction()`, and `app_schema`, `sharing`, and `policy` are passed as preloaded arguments. If admin policy or app membership changes concurrently, mutations proceed under stale in-memory policy despite acquiring the guard.
- **Remediation:** Reread stored policy and verify membership inside the session after `acquire_guard_session`.

### 3. Unused Variable Compiler Warning
- **Location:** `glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs:88:13`
- **Impact:** Unused variable `client` emits a compiler warning during cargo test builds. Prefix with `_client`.

---

## Medium Priority Improvements ([2])

### 1. Duplicate `ASSIGNMENT_TABLE_NAME` Constant
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt:8` (`ASSIGNMENT_TABLE_NAME = "taskAssignees"`) duplicates `TaskDataContract.ASSIGNMENT_TABLE_NAME`. Consolidate to single source of truth.

### 2. Missing Assignee Indicator on Desktop Sticky Surface
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt` displays title, completion toggle, and deadline, but does not display assignee badges or assigned-to indicators.

---

## Low Priority Suggestions ([1])

### 1. Redundant Condition Warnings in TodoList
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt:486, 522`: Kotlinc warns `Condition is always 'true'`. Clean up redundant boolean comparisons.

---

## Positive Observations
- **All 625 Tests Green:**
  - `:shared:jvmTest`: 324/324 PASSED
  - `:desktopApp:test -PdesktopTarget=windows`: 145/145 PASSED
  - `:androidApp:testDebugUnitTest`: 60/60 PASSED
  - `cargo test -p glean-hub-sync --lib`: 28/28 PASSED
  - `cargo test -p glean-hub-sync-engine`: 6/6 PASSED
  - `pnpm --dir glean-hub-app test:unit`: 50/50 PASSED
  - `node --test scripts/hub-schema-provision.test.mjs`: 12/12 PASSED
- **Cycle 2 Critical Fix 2 Verified:** `currentRecipient` logic cleanly distinguishes between pending clear operations (`latestSlotOp != null`) and unmutated server base (`slotBase`).
- **Cycle 2 Warning 1 Verified:** `reviewHead` query accurately scopes `tableName == "todos"` to eliminate collisions with assignment slots sharing identical row IDs.
- **Session API Completeness:** `find_canonical_record_session`, `find_live_parent_task_session`, and `determine_effective_task_access_session` provide complete transaction session coverage.

---

## Recommended Actions (Prioritized)

1. **Fix fail-open permissions in `todo-list.kt` and `room-account-store.kt`:**
   - In `todo-list.kt:310`, default foreign tasks lacking access state to `TaskCapabilities.NONE`.
   - In `room-account-store.kt` (lines 640, 816, 948, 982), check `access?.canEditContent == true`, `access?.canDeleteTask == true`, and `access?.canManageAssignment == true`.
2. **Wire actual slot version and assignee into `EditorState` and `TaskAssigneePicker`:**
   - Add `expectedSlotVersion` and `inheritedAssigneeUserId` to `EditorState`.
   - In `SessionViewModel.openEdit` and `desktop/main.kt:openEdit`, load and populate current slot assignee and version.
   - Pass `editor.expectedSlotVersion` and `editor.inheritedAssigneeUserId` in `todo-editor.kt:217`.
3. **Repair MongoDB transaction collision handling:**
   - Change `find_canonical_record` to `find_canonical_record_session` in `sync_service.rs:1346`.
   - Handle transaction boundary abort on duplicate key collisions in `sync_service.rs`.
4. **Batch app membership query in `assignment_reconciliation.rs`:**
   - Check `verify_user_app_membership` once before the loop.

---

## Metrics
- **Type Coverage:** 100% (TypeScript tsc: 0 errors; Kotlin multiplatform: compile complete).
- **Test Coverage:** 625 tests executed and passing across 7 test suites.
- **Critical Issues Identified:** 3 (Fail-open permissions, Editor hardcoded slot version / missing assignee, Mongo transaction abort on collision).
- **Warnings Identified:** 3 (N+1 membership queries, Stale policy read outside guard, Compiler warning in reqwest_http).
- **Suggestions Identified:** 3 (Duplicate constant, Sticky assignee indicator, Redundant boolean warnings).

---

## Unresolved Questions
1. When a transaction encounters duplicate key collision (11000) inside MongoDB, should `sync_service` abort and retry the entire push batch with refreshed server versions, or transition to individual record transactions?
2. Should child tasks inherit assignment visually in the editor title header as well as in the assignee picker badge?
