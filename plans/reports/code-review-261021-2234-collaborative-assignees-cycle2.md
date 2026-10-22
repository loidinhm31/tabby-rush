# Code Review Report: Collaborative Task Assignees (Cycle 2)

## Code Review Summary

### Scope
- **Files reviewed:** 150+ modified and newly created files across `doggo` (`TabbyRush`) and `glean-hub` (`Glean Hub`), focusing on post-fix verification and deeper transactional/state analysis.
  - **doggo (Android/Desktop/Shared):**
    - Contracts & Projections: `canonical-record-ref.kt`, `assignment-contracts.kt`, `task-access-contracts.kt`, `collaboration-store-contracts.kt`, `persisted-projection-inputs.kt`, `persisted-schedule-projector.kt`, `task-hierarchy-projector.kt`, `task-surface-projector.kt`, `todo-models.kt`, `safe-errors.kt`.
    - Persistence & Room: `room-account-store.kt`, `tabbyrush-dao.kt`, `tabbyrush-database.kt`, `database-migrations.kt`, `task-access-entity.kt`, `access-loss-evidence-entity.kt`, `access-check-staging-entity.kt`, `scan-staging-entity.kt`, `editor-draft-entity.kt`, `outbox-entity.kt`.
    - Native Sync & Isolation: `hub-sync-client.kt`, `account-sync-coordinator.kt`, `staged-full-scan.kt`, `outbox-reconciliation.kt`, `task-json-merge.kt`, `reminder-scheduling-coordinator.kt`.
    - Shared UI & State: `todo-editor.kt`, `todo-list.kt`, `tabbyrush-app.kt`, `task-assignee-picker.kt`, `conflict-review.kt`, `todo-ui-state.kt`.
    - Android Runtime & Glance: `account-runtime-factory.kt`, `android-notification-adapter.kt`, `main-activity.kt`, `session-view-model.kt`, `tabbyrush-application.kt`, `task-surface-access.kt`, `task-surface-action-receiver.kt`, `task-widget.kt`.
    - Desktop: `main.kt`, `desktop-task-surfaces.kt`, `sticky-task-content.kt`, `desktop-reminder-scheduler.kt`, `windows-notification-adapter.kt`.
    - Scripts: `hub-schema-provision.mjs`, `hub-schema-provision.test.mjs`, `task-assignees-live-smoke.mjs`.
  - **glean-hub (Backend, Protocol, Admin):**
    - Protocol & Engine: `glean-hub-sync-engine/src/sharing.rs`, `protocol.rs`, `checkpoint.rs`, `conflict.rs`, `document.rs`.
    - Client & Types: `glean-hub-sync-client/src/client.rs`, `export_types.rs`, `reqwest_http.rs`.
    - Plugin: `glean-hub-sync/src/services/sync_service.rs`, `schema_validator.rs`, `app_service.rs`, `assignment_authorization.rs`, `assignment_reconciliation.rs`, `models/assignment_fence.rs`, `error.rs`.
    - Admin UI: `glean-hub-app/src/services/sync-admin-service.ts`, `sync-admin-service.test.ts`, `app-registration.tsx`, `edit-app.tsx`.
- **Lines of code analyzed:** ~14,200 lines across Kotlin, Rust, and TypeScript.
- **Review focus:** Verification of 3 Cycle 1 critical fixes, intra-transaction consistency in MongoDB, state-machine edge cases in assignment CAS, cross-table outbox disambiguation, fail-closed authorization checks, and test coverage.
- **Updated plans:** `plans/261021-1552-tabbyrush-task-assignees/plan.md`.

---

## Overall Assessment
**Overall Score: 7/10** (Previous Cycle 1 Score: 5/10)

Significant progress has been made since Cycle 1:
1. **Android Compilation & Tests Restored:** All 11 compilation syntax errors, missing imports, and unclosed blocks across 6 files were resolved; `:androidApp:testDebugUnitTest` passes cleanly with all 60 tests green.
2. **MongoDB Write Transactions Attached:** All write mutations (`insert_one`, `update_one`) and fence operations (`acquire_guard_session`, `increment_access_revision_session`) in `push_sync_collaborative` now participate in the transaction session with atomic commit.
3. **Cross-Owner Outbox Isolation Implemented:** `account-sync-coordinator.kt:280` now partitions fallback queries by `it.ownerUserId == taskRef.ownerUserId`.

However, approval is gated by two critical functional bugs and several high-priority correctness issues discovered during deep review and advisor consultation:
1. **Sessionless Reads Inside Collaborative Transactions:** While writes use `&mut session`, the authorization and relationship reads (`find_canonical_record` and `find_live_parent_task`) bypass the session and query MongoDB directly. Uncommitted records written earlier in a multi-record batch (such as a root task followed by an assignment slot, or a parent followed by a subtask) remain invisible to subsequent items in that batch, triggering false access denials.
2. **Assignment Clear Fallback Bug in Room Account Store:** When a pending slot operation clears an assignee (null recipient), `room-account-store.kt:1003` uses `?:` to fall back to `slotBase`. Re-assigning to the previous recipient falsely evaluates to `Unchanged`, leaving the pending deletion in the outbox.

---

## Critical Issues ([2]): MUST FIX

### 1. Collaborative Push Transaction Reads Bypass Active Session
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:977, 1016, 1070, 1179, 1213, 1267, 1426, 1548` and `assignment_authorization.rs:39, 59`
- **Impact:** While mutations use `.session(&mut session).await`, read helpers `AssignmentAuthorization::find_canonical_record` and `find_live_parent_task` execute collection queries directly on `&self.db` without the session. Under WiredTiger snapshot isolation:
  - If a push request creates a new task (`record[0]`) and assigns it (`record[1]`), `record[1]` calls `find_canonical_record` to verify the parent task. Because `record[0]` is uncommitted, the out-of-session read returns `None`, falsely failing `record[1]` with `ERROR_ACCESS_DENIED`.
  - If a push request creates a parent task and child task together, `find_live_parent_task` fails to find the uncommitted parent, failing the child task with `"Parent task not found or deleted"`.
  - Intra-batch version conflict reloads check committed state rather than the transactional snapshot.
- **Remediation:** Overload or update `find_canonical_record` and `find_live_parent_task` to accept `session: Option<&mut ClientSession>`, and pass `&mut session` throughout `push_sync_collaborative`.

### 2. Pending Assignment Clears Incorrectly Fall Back to Previous Server Base
- **Location:** `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt:1003–1008`
- **Impact:**
  ```kotlin
  val currentRecipient = latestSlotOp?.let { parseRecipientFromPayload(it.payloadJson) }
      ?: slotBase?.rawJson?.let { parseRecipientFromPayload(it) }

  if (currentRecipient == intent.desiredRecipientUserId) {
      return@withMutationLock AssignmentSlotResult.Unchanged
  }
  ```
  `parseRecipientFromPayload` returns `null` for a delete/clear operation. When `latestSlotOp` is a clear operation, `?.let` evaluates to `null`, causing the Elvis operator `?:` to fall back to `slotBase`. If base had recipient `A`, and the user had cleared it (pending outbox delete), calling `assignTaskLocally` to re-assign `A` evaluates `currentRecipient` as `A`. It returns `AssignmentSlotResult.Unchanged` instead of creating an update outbox record! The pending delete remains in the outbox, and subsequent sync wipes out the assignment.
- **Remediation:** Check whether `latestSlotOp` exists first. If present, its recipient (including `null`) is authoritative for pending state:
  ```kotlin
  val currentRecipient = if (latestSlotOp != null) {
      parseRecipientFromPayload(latestSlotOp.payloadJson)
  } else {
      slotBase?.rawJson?.let { parseRecipientFromPayload(it) }
  }
  ```

---

## Warnings ([4]): SHOULD FIX

### 1. Incomplete Canonical Identity in Conflict Review Head Fallback
- **Location:** `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:279–283`
- **Impact:** `store.dao.getOutboxByRowId(taskRef.rowId).filter { it.ownerUserId == taskRef.ownerUserId }` partitions by `ownerUserId`, but does not filter by `tableName == "todos"`. Because assignment slots share the exact same `rowId` as tasks (`rowId == taskId`), if `todos` outbox is empty but `taskAssignees` has a conflict on that task, `reviewHead` returns the assignment outbox entry. Passing an assignment entry to `reapplyConflict` invokes `TaskJsonMerge.merge` with task field masks, resulting in deserialization failure or payload corruption.
- **Remediation:** Require both matching `ownerUserId` and `tableName`:
  ```kotlin
  store.dao.getOutboxByRowId(taskRef.rowId).filter { 
      it.ownerUserId == taskRef.ownerUserId && it.tableName == TaskDataContract.DEFAULT_TABLE_NAME 
  }
  ```

### 2. Disconnected Inherited Assignee UI in Editor
- **Location:** `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt:23–25` and `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt:214`
- **Impact:** `EditorState` lacks an `inheritedAssigneeUserId` field. `todo-editor.kt:214` passes hardcoded `inheritedAssigneeUserId = null` to `TaskAssigneePicker`. Consequently, child tasks inheriting an assignee from a root parent cannot render the inherited assignee badge or distinguish between direct and inherited assignment.
- **Remediation:** Add `val inheritedAssigneeUserId: String? = null` to `EditorState`, populate it from parent task metadata in `SessionViewModel` and `DesktopController`, and forward it in `todo-editor.kt`.

### 3. Fail-Closed Permission Checking for Foreign Tasks
- **Location:** `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt:948, 982` and `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt:310`
- **Impact:**
  - In `room-account-store.kt`, checks use `access?.capabilities?.editContent == true` directly without checking `access.status == TaskAccessStatus.Accessible`. If an access entry is marked `Unavailable`, capabilities may still evaluate to true if the entity was not yet purged. Use `access?.canEditContent == true` and `access?.canManageAssignment == true`.
  - In `todo-list.kt:310`, `val capabilities = todo.accessState?.capabilities ?: TaskCapabilities.OWNER` defaults foreign tasks with missing access state to full `OWNER` permissions in the UI. If `todo.taskRef.ownerUserId != currentUserId`, it must default to empty/zero capabilities.

### 4. N+1 App Membership Query Overhead in Batch Access Checks
- **Location:** `glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs:178–200` and `assignment_authorization.rs:94`
- **Impact:** In `execute_access_checks`, each canonical ref loops into `determine_effective_task_access`, which calls `verify_user_app_membership(db, actor_user_id, app_id).await?`. In a batch of 100 access checks, this issues 100 identical queries to the `users` collection for the same user and app ID.
- **Remediation:** Perform `verify_user_app_membership` once at the entry of `execute_access_checks`.

---

## Suggestions ([3]): NICE TO HAVE

### 1. Eliminate Compiler Warning in `glean-hub-sync-client`
- **Location:** `glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs:88:13`
- Unused variable `client` triggers a rustc compiler warning during test builds. Prefix with `_client` or remove.

### 2. Consolidate Duplicate `ASSIGNMENT_TABLE_NAME` Constants
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt:8` (`ASSIGNMENT_TABLE_NAME = "taskAssignees"`) duplicates `TaskDataContract.ASSIGNMENT_TABLE_NAME`. Use one canonical definition.

### 3. Provide Visual Indicator on Desktop Sticky Surface for Assigned Tasks
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`: The desktop sticky row displays title, checkbox, and deadline, but does not display assignee badges. Adding a subtle assignee indicator improves visibility.

---

## Positive Observations
- **Android Compilation Fixes Verified:** All syntax errors, missing imports, unclosed blocks, and missing sealed branches in `androidApp` were cleanly repaired. `:androidApp:testDebugUnitTest` passes 60/60 tests.
- **Write Transaction Safety:** All mutations (`insert_one`, `update_one`, `acquire_guard_session`, `increment_access_revision_session`) in `push_sync_collaborative` now participate in the transaction session.
- **Deterministic Room v5 Migrations:** Migration 4→5 seamlessly creates new tables, preserves local outbox and base evidence, and establishes compound indices without fallback drops.
- **High Multiplatform Test Volume:** 625 unit and integration tests passing with 0 failures across JVM, Desktop, Android, Rust, Node, and TypeScript suites.

---

## Recommended Actions (Prioritized)
1. **Pass MongoDB session to intra-transaction reads:**
   - In `assignment_authorization.rs`, add session parameter to `find_canonical_record` and `find_live_parent_task`.
   - In `sync_service.rs:push_sync_collaborative`, pass `&mut session` to all read helpers.
2. **Fix assignment clear fallback in `room-account-store.kt:1003`:**
   - Do not fall back to `slotBase` via `?:` when `latestSlotOp` is present.
   - Add unit test for sequence: `Assigned A` → `Clear (pending outbox)` → `Re-assign A`.
3. **Filter table name in `reviewHead` fallback (`account-sync-coordinator.kt:280`):**
   - Add `&& it.tableName == TaskDataContract.DEFAULT_TABLE_NAME`.
4. **Populate `inheritedAssigneeUserId` in `EditorState`:**
   - Add field to `EditorState` and forward parent assignee to `TaskAssigneePicker`.
5. **Enforce fail-closed permission checks for foreign tasks:**
   - Use `access?.canEditContent` in `room-account-store.kt`.
   - Default foreign tasks with missing access state to zero capabilities in `todo-list.kt`.

---

## Metrics
- **Passing Tests:** 625 tests reported passing across 7 suites:
  - `:shared:jvmTest`: 324 passing
  - `:desktopApp:test -PdesktopTarget=windows`: 145 passing
  - `:androidApp:testDebugUnitTest`: 60 passing
  - `node --test scripts/hub-schema-provision.test.mjs`: 12 passing
  - `cargo test -p glean-hub-sync-engine`: 6 passing
  - `cargo test -p glean-hub-sync --lib`: 28 passing
  - `pnpm --dir glean-hub-app test:unit`: 50 passing
- **TypeScript Typecheck:** 0 errors (`pnpm exec tsc --noEmit`).
- **Critical Issues Identified:** 2 (Sessionless transaction reads in Hub, Assignment clear fallback bug in Room store).
- **Warnings Identified:** 4 (Outbox table collision, Disconnected inherited assignee UI, Foreign task fail-closed permissions, N+1 membership queries).

---

## Unresolved Questions
1. Should child tasks inherit assignment status dynamically in queries, or is assignment inheritance strictly visual while access capabilities are derived from the root parent? (Current model: visual + root parent capabilities).
2. Are real-Mongo multi-document transaction integration tests (`--features mongo-integration-tests`) executed in continuous integration pipelines for multi-record batch creation?
