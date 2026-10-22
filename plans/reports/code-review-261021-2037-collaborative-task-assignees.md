# Code Review Report: Collaborative Task Assignees

## Code Review Summary

### Scope
- **Files reviewed:** 150+ modified and newly created files across `doggo` (`TabbyRush`) and `glean-hub` (`Glean Hub`).
  - **doggo (Android/Desktop/Shared):**
    - Contracts & Projections: `canonical-record-ref.kt`, `assignment-contracts.kt`, `task-access-contracts.kt`, `collaboration-store-contracts.kt`, `persisted-projection-inputs.kt`, `persisted-schedule-projector.kt`, `task-hierarchy-projector.kt`, `task-surface-projector.kt`, `todo-models.kt`, `safe-errors.kt`.
    - Persistence & Room: `room-account-store.kt`, `tabbyrush-dao.kt`, `tabbyrush-database.kt`, `database-migrations.kt`, `task-access-entity.kt`, `access-loss-evidence-entity.kt`, `access-check-staging-entity.kt`, `scan-staging-entity.kt`, `editor-draft-entity.kt`, `outbox-entity.kt`.
    - Native Sync: `hub-sync-client.kt`, `account-sync-coordinator.kt`, `staged-full-scan.kt`, `outbox-reconciliation.kt`, `task-json-merge.kt`, `reminder-scheduling-coordinator.kt`.
    - Shared UI: `todo-editor.kt`, `todo-list.kt`, `tabbyrush-app.kt`, `task-assignee-picker.kt`, `conflict-review.kt`.
    - Android Runtime: `account-runtime-factory.kt`, `android-notification-adapter.kt`, `main-activity.kt`, `session-view-model.kt`, `tabbyrush-application.kt`, `task-surface-access.kt`, `task-surface-action-receiver.kt`, `task-widget.kt`, etc.
    - Desktop: `main.kt`, `desktop-task-surfaces.kt`, `sticky-task-content.kt`, `desktop-reminder-scheduler.kt`, `windows-notification-adapter.kt`.
    - Tooling & Scripts: `hub-schema-provision.mjs`, `hub-schema-provision.test.mjs`, `task-assignees-live-smoke.mjs`.
  - **glean-hub (Backend & Admin):**
    - Protocol & Engine: `glean-hub-sync-engine/src/sharing.rs`, `protocol.rs`, `checkpoint.rs`, `conflict.rs`, `document.rs`.
    - Client: `glean-hub-sync-client/src/client.rs`, `export_types.rs`.
    - Plugin: `glean-hub-sync/src/services/sync_service.rs`, `schema_validator.rs`, `app_service.rs`, `assignment_authorization.rs`, `assignment_reconciliation.rs`, `models/assignment_fence.rs`, `error.rs`.
    - Admin UI: `glean-hub-app/src/services/sync-admin-service.ts`, `sync-admin-service.test.ts`, `app-registration.tsx`, `edit-app.tsx`.
- **Lines of code analyzed:** ~14,200 lines of code across Rust, TypeScript, and Kotlin.
- **Review focus:** Security, correctness, concurrency, error handling, performance, contract consistency, and task completeness for collaborative task assignees.
- **Updated plans:** `plans/261021-1552-tabbyrush-task-assignees/plan.md`.

---

## Overall Assessment
**Overall Score: 5/10**

The foundational multiplatform contracts, deterministic Room v5 SQLite migrations, pure hierarchy projections, and TypeScript admin schema validation demonstrate thoughtful, high-integrity engineering. Desktop sticky surfaces and shared JVM tests pass (565 tests passing across `:shared:jvmTest`, `:desktopApp:test`, `cargo test`, `pnpm test:unit`, and `node --test`).

However, the change cannot be approved in its current state due to two severe blockers:
1. **Broken Android Compilation:** `androidApp` failed Kotlin compilation with 21 affected files due to missing imports, duplicate variable declarations, unclosed `if` and `when` branches, an inexhaustive `when` expression, and references to non-existent sealed variants. Android build verification was omitted by earlier test passes.
2. **Hollow Server Transactions in Hub:** `push_sync_collaborative` initiates and commits a MongoDB session transaction, but none of the internal collection queries or mutations (`insert_one`, `update_one`, `find_one`) pass `&mut session`, leaving the operations completely un-transactional with no rollback guarantees on partial failure.
3. **Cross-Owner Identity Leak in Conflict Head Lookup:** `account-sync-coordinator.kt` falls back from an owner-bound outbox lookup to an un-partitioned row ID query without verifying `ownerUserId`, allowing one user's conflict action to select another user's task mutation if row IDs collide.

---

## Critical Issues ([3]): MUST FIX

### 1. `androidApp` fails Kotlin compilation (`./gradlew.bat :androidApp:compileDebugKotlin`)
Multiple files in `androidApp` fail compilation, preventing Android APK builds and packaging:
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt:44`**: Missing import `dev.tabbyrush.shared.data.AndroidAccountFiles`. Causes property delegate `val accountFiles by lazy { AndroidAccountFiles(this) }` to fail type inference. This cascades into unresolved references for `hasPendingWipe` and `readGeneration` across:
  - `tabbyrush-application.kt:159, 185, 190, 217`
  - `session-view-model.kt:830`
  - `tabbyrush-alarm-receiver.kt:74, 79`
  - `tabbyrush-sync-worker.kt:37, 54`
  - `task-surface-access.kt:79, 130, 191, 228`
  - `android-sync-scheduler.kt:47, 65`
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt:42, 114`**: Conflicting local declaration of `val establishedIdentity` within `create(...)`.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt:49, 177`**: Unresolved reference `RoomAccountStore`; missing `import dev.tabbyrush.shared.data.RoomAccountStore`.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt:38`**: Unresolved reference `Bundle` in `onCreate(savedInstanceState: Bundle?)`; missing `import android.os.Bundle`.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt:226`**: Missing closing brace `}` on the `ACTION_VIEW_TASK_LEGACY, ACTION_OPEN_TASK -> {` branch in `handleIntent()`, causing parser corruption for subsequent action branches and companion object.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt:145, 155`**: Unclosed `if (am != null) {` block in `cancelAllForAccount()` lacks closing brace before line 156 (`if (nm != null)`), causing member functions (`scheduleAlarmHandle`, `cancelAlarmHandle`, `cancelLegacyAlarmHandle`) to be parsed as nested local functions with illegal `private` modifiers.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:127, 162, 218`**: Unresolved reference `CancellationException`; missing `import kotlinx.coroutines.CancellationException` (or `import kotlin.coroutines.cancellation.CancellationException`).
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:224`**: `when (val result = runtime.store.assignTaskLocally(intent))` is not exhaustive; missing branches for `is AssignmentSlotResult.InvalidStructure` and `AssignmentSlotResult.Unchanged`.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:466, 489`**: Unresolved reference `TaskMutationResult.OperationForbidden` in `res is TaskMutationResult.OperationForbidden`. `TaskMutationResult` contains `AccessDenied`, `InvalidStructure`, `VersionConflict`, `Changed`, `Unchanged`, `Missing`.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:473`**: Argument type mismatch: actual `TaskRef`, expected `String` in `SafeError.OperationForbidden("Edit forbidden for this task", existing.taskRef)`. Correct parameter naming: `SafeError.OperationForbidden(code = "OPERATION_FORBIDDEN", message = "Edit forbidden for this task", taskRef = existing.taskRef)`.
- **`androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt:468-469`**: Missing comma `,` after `.clickable(...)` before `verticalAlignment = Alignment.CenterVertically`.

### 2. Hollow / unattached MongoDB transactions in collaborative push sync
- **`glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:926-936, 1594`**:
  `session.start_transaction()` is initiated and `session.commit_transaction()` is executed. However, none of the database operations inside `push_sync_collaborative` pass `&mut session` or use `_with_session` methods (e.g. `collection.insert_one(&slot_doc).await`, `collection.update_one(filter, update).await`, `collection.find_one(...)`, `acquire_guard`, `increment_access_revision`).
  - **Impact:** All mutations execute outside the transaction session directly on the collection. If an error occurs halfway through a multi-record push, previously inserted or updated records will not roll back, violating atomicity and data consistency invariants.

### 3. Conflict review head falls back to unpartitioned row ID query without owner validation
- **`shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:276–282`**:
  ```kotlin
  private suspend fun reviewHead(taskRef: TaskRef): OutboxEntity? {
      val boundKey = boundBindingKey(taskRef.ownerUserId)
      val ops = store.dao.getOutboxByRow(boundKey, "todos", taskRef.rowId)
      val activeOps = if (ops.isEmpty()) store.dao.getOutboxByRowId(taskRef.rowId) else ops
      return activeOps.firstOrNull()?.takeIf {
          it.deliveryState == OutboxEntity.STATE_CONFLICT || it.deliveryState == OutboxEntity.STATE_REVIEW
      }
  }
  ```
  - **Impact:** When `ops` is empty (e.g., when the target owner has no local outbox entries for this rowId), the coordinator falls back to `getOutboxByRowId(taskRef.rowId)` and selects the first matching outbox entry regardless of its `ownerUserId`. If two different accounts/owners share the same `rowId`, a conflict action targeted at user A can mutate or discard user B's outbox entry. The fallback must either be removed or strictly enforce `it.ownerUserId == taskRef.ownerUserId`.

---

## Warnings ([5]): SHOULD FIX

### 1. Collaborative response validation permits null owner and unverified version echo
- **`shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt:108–138`**:
  In `validatePushResponse`, `synced.ownerUserId != null` is optional even when `response.collaborationVersion == COLLABORATION_VERSION_1`:
  ```kotlin
  if (response.collaborationVersion == COLLABORATION_VERSION_1 && synced.ownerUserId != null) {
      require(synced.ownerUserId.isNotBlank()) { "Invalid ownerUserId in syncedRecords" }
  }
  ```
  Similarly, in `validatePullResponse`, enforcement of `record.ownerUserId != null` depends on the server response including `collaborationVersion == COLLABORATION_VERSION_1`. If the client requested collaboration, the client must fail closed if the server omits the collaboration version or returns records lacking canonical `ownerUserId`.

### 2. N+1 redundant query overhead in `execute_access_checks`
- **`glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs:118-140` and `assignment_authorization.rs:94`**:
  In `execute_access_checks`, each canonical reference loops through `determine_effective_task_access`, which invokes `verify_user_app_membership(db, actor_user_id, app_id).await?` on every item. In a batch of 100 access checks, this issues 100 identical queries to the `users` collection for the same user and app ID (which was already verified at entry in `pull_sync_collaborative:1616`). In addition, direct and inherited slots are queried sequentially rather than in a batched `$in` query.

### 3. `EditorState` lacks `inheritedAssigneeUserId` tracking for child tasks
- **`shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt:3-25` and `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt:214`**:
  `EditorState` only tracks `assigneeUserId`, but does not track `inheritedAssigneeUserId`. In `todo-editor.kt:214`, `inheritedAssigneeUserId` is hardcoded to `null` (`inheritedAssigneeUserId = null`). Consequently, when an editor is opened for a child task that inherits an assignee from its root parent, `TaskAssigneePicker` cannot render the inherited assignee badge or distinguish between direct and inherited assignment, violating the requirement that child tasks clearly display inherited assignee status.

### 4. `CompletionChange.Missing` returned on authorization failure in `setCompletedLocally`
- **`shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt:949-951`**:
  When `!canEdit` in `setCompletedLocally`, the function returns `CompletionChange.Missing` rather than an explicit authorization failure representation. While safe from an execution perspective (mutations are blocked), the caller and logs cannot distinguish between an absent task and an authorization denial.

### 5. Compiler warnings in `glean-hub-sync-client`
- **`glean-hub-core/glean-hub-sync-client/src/reqwest_http.rs:88:13`**:
  Unused variable `client` triggers a rustc warning during test builds. Prefix with `_client` or remove.

---

## Suggestions ([4]): NICE TO HAVE

### 1. Batch direct assignment and root parent lookups in Hub access reconciliation
- `glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs`: Rather than looping through individual `find_one` calls, execute a single `$in` query for direct assignment slots and root parent tasks across all requested `accessChecks` keys.

### 2. Add automated CI check for Android compilation
- Add `./gradlew.bat :androidApp:compileDebugKotlin` and `./gradlew.bat :androidApp:testDebugUnitTest` to the continuous verification script. Because peers only ran JVM and Desktop suites, 21 broken files in `androidApp` remained undetected until this review.

### 3. Consolidate `ASSIGNMENT_TABLE_NAME` constant definitions
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt:8` defines `ASSIGNMENT_TABLE_NAME = "taskAssignees"` at the top level, while `task-data-contract.kt:5` defines `TaskDataContract.ASSIGNMENT_TABLE_NAME`. Use one canonical definition to avoid duplication.

### 4. Provide visual indicator on child task row in sticky desktop surfaces for inherited assignees
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-content.kt`: The desktop sticky row displays title, checkbox, and deadline, but does not display assignee badges. Adding a subtle assignee indicator would improve user awareness of collaborative ownership on desktop surfaces.

---

## Positive Observations
- **Pure Projections:** `TaskHierarchyProjector`, `PersistedScheduleProjector`, and `TaskSurfaceProjector` are completely decoupled from Room and SQLite entities, making their hierarchy rules, encounter ordering, and tie-breaking deterministic and fully unit-testable.
- **Room v5 Migration Integrity:** Migration from v4 to v5 handles schema alterations cleanly without destructive fallback, preserving local evidence, creating proper compound indices, and initializing two-stage owner bindings with explicit state machines.
- **Fail-Closed Privacy Guarantees:** On authoritative access loss (`unavailable`), local content is immediately invalidated and purged, preventing unauthorized display or stale notification triggers.
- **Desktop Windows DPAPI & Tray Lifecycle:** Desktop remote runtime and shutdown handling properly serialize cancellations without escaping progress transitions.
- **Admin Schema Provisioning:** `hub-schema-provision.mjs` enforces Windows ACL protections, strict CAS checks, and allowlist validation, preventing forbidden fields (`id`, `createdAt`, `updatedAt`, `parentTaskId`) from entering the editable fields array.

---

## Recommended Actions (Prioritized)
1. **Fix `androidApp` compilation errors immediately:**
   - Add `import dev.tabbyrush.shared.data.AndroidAccountFiles` in `tabbyrush-application.kt:12`.
   - In `account-runtime-factory.kt`, delete duplicate `establishedIdentity` declaration and import `RoomAccountStore`.
   - In `android-notification-adapter.kt:155`, insert missing closing brace `}` for `if (am != null)`.
   - In `main-activity.kt:38`, import `android.os.Bundle`; at line 226, insert closing brace `}` for `ACTION_OPEN_TASK` branch.
   - In `session-view-model.kt`, import `CancellationException`, complete exhaustive `when` branches on `AssignmentSlotResult`, remove `TaskMutationResult.OperationForbidden`, and fix parameter ordering for `SafeError.OperationForbidden`.
   - In `task-widget.kt:468`, add missing trailing comma.
   - Run `./gradlew.bat :androidApp:compileDebugKotlin` and `./gradlew.bat :androidApp:testDebugUnitTest`.
2. **Attach MongoDB session to collaborative push operations:**
   - In `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:926–1594`, pass `&mut session` to all `find`, `find_one`, `insert_one`, and `update_one` calls in `push_sync_collaborative`, or structure the mutations to participate in the transaction session.
3. **Remove unpartitioned outbox fallback in `account-sync-coordinator.kt:279`:**
   - Change `val activeOps = if (ops.isEmpty()) store.dao.getOutboxByRowId(taskRef.rowId) else ops` to require matching `ownerUserId`.
4. **Tighten collaborative sync response validation in `hub-sync-client.kt:108-138`:**
   - Fail closed if a negotiated collaborative request receives a response without `collaborationVersion == 1` or if `syncedRecords` omit `ownerUserId`.
5. **Add `inheritedAssigneeUserId` to `EditorState`:**
   - Populate `inheritedAssigneeUserId` from authorized root parent access metadata when editing a child task.

---

## Metrics
- **Passing Tests:** 565 tests passing across `:shared:jvmTest` (324), `:desktopApp:test` (145), `cargo test` (38), `pnpm test:unit` (50), `node --test` (12).
- **Failing Compilation:** `:androidApp:compileDebugKotlin` (11 distinct fatal compilation errors across 6 files).
- **Security Vulnerabilities Identified:** 1 (Cross-owner conflict head lookup leak in `account-sync-coordinator.kt`).
- **Data Integrity / Concurrency Flaws:** 1 (Detached MongoDB session in `push_sync_collaborative`).

---

## Unresolved Questions
1. Is MongoDB running in a replica-set configuration in all target staging/production environments? (Required for MongoDB multi-document transactions to succeed).
2. Should child tasks display the parent task's assignee in read-only mode if the child task itself has no direct assignee? (Recommended yes, via `inheritedAssigneeUserId`).
