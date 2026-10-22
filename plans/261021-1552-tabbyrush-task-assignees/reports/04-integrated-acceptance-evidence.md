# Integrated Acceptance Evidence: Collaborative Task Assignees

Date: 2026-10-22
Plan Directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`  
Branch: `feature/parent-child-tasks-deadline-surfaces`  
Status: Implementation and Phase 09 harness/documentation delivery completed; implementation approved with noted issues per user-provided disposition.
Release Verdict: **RELEASE BLOCKED. G2 is partially evidenced; G3–G6 runtime/audit acceptance remains pending or not run. Production migration unauthorized.**

---

## 1. Executive Summary & Policy Gates

### Policy Validation Gate (G0): CONFIRMED 2026-10-21
Confirmed by user policy review (see [Validation Interview Record](06-validation-interview.md)). G0 confirms three core architectural decisions:
1. **Privacy-First Purge**: Authoritative loss of shared task access immediately purges revoked remote payloads and unsent shared draft/outbox edits from active and review storage. Only canonical identity, local opId, base/submitted version, timestamps, and reason are retained in `access_loss_evidence` as non-replayable, content-free audit records. No automatic replay or quarantine recovery after regrant.
2. **Whole-App Capability Cutoff**: Sync requests for an opted-in collaborative app from clients lacking `collaborationVersion: 1` fail closed with HTTP 426 `COLLABORATION_REQUIRED` before any mutation or content read across `/push`, `/pull`, and `/delta`. No unsafe private-only downgrade.
3. **Simpler App-Wide Guard**: A single app-wide authorization fence (`_sync_assignment_fences`) in a transaction-capable MongoDB replica set serializes opted-in mutations and visibility revisions (`accessRevision`), preventing edit/revoke ordering races without complex per-tree locking.

### Reported suite results and runtime gate status
Automated test suites have been executed and are documented in the linked QA/review reports. Passing module/integration results do not by themselves establish end-to-end acceptance.

| Gate | Scope | Verification command / target | Current recorded status |
|---|---|---|---|
| **G0** | Policy decisions (purge, cutoff, app fence) | `reports/06-validation-interview.md` | **CONFIRMED** (document-only, 2026-10-21). |
| **G1** | Integrated construction and module suites | Shared JVM, desktop, Android, Hub/admin and provisioning suites in cited reports | **Reported passed:** earlier 625/625 regression run and selected 390/390 run are separate snapshots, not additive; Cycle 3 review 9.5/10, 0 critical. See [QA 625/625](../../reports/qa-261021-2225-critical-fix-regression-tests.md), [selected QA 390/390](../../reports/qa-261022-1521-selected-test-suite-results.md), [Cycle 3 review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md). |
| **G2** | Disposable replica-set Hub hostile/race smoke | `task_assignment_integration`, `schema_version_integration`; dedicated `scripts/task-assignees-live-smoke.mjs` | **PARTIAL:** Mongo targets passed 6/6 and 4/4 with the authorized disposable endpoint. Dedicated feature smoke, collision-recovery case, full pagination/race and guard-contention evidence are not recorded. |
| **G3** | Populated Room v5 coordinator runtime smoke | `./gradlew :desktopApp:runTaskAssigneesRuntimeSmoke` | **NOT RUN** (dedicated harness prepared; actual Hub/Room/offline restart/purge inspection pending). |
| **G4** | Connected Android runtime & Glance | `./gradlew :androidApp:connectedDebugAndroidTest` | **NOT RUN** (connected device and provider approval pending). |
| **G5** | Desktop native runtime & sticky surfaces | Interactive Compose desktop on supported Windows/Linux host | **NOT RUN** (interactive host/provider acceptance pending). |
| **G6** | Documentation, security & release audit | Release-security and evidence audit | **NOT RUN / pending.** |

Production authorization, separate existing release holds and required interactive approvals remain independent.

---

## 2. Traceability Matrix: All 12 Brainstorm Criteria

Every criterion from [Acceptance Traceability](03-acceptance-traceability.md) is mapped to its implemented contract, prepared verification harness, negative test boundaries, and gate status.

### Criterion 1: Root Assignment, Existing and Future Children
- **Contract**: Creator assigns Alice to root task via `taskAssignees` slot. Alice receives existing root and children, plus future children added by creator, via computed inherited grant (`hierarchyCoverage: "full-group"`). Zero duplicate child relations are materialized on Hub.
- **Implemented Code**: `TaskHierarchyProjector` (Phase 01), `task_assignment_integration.rs` (Phase 02), `RoomAccountStore.assignTask` (Phase 03), `staged-full-scan.kt` (Phase 05), `TaskAssigneePicker` (Phase 06).
- **Prepared Verification**:
  - API: `scripts/task-assignees-live-smoke.mjs` (Phase 5) verifies Alice receives root + 2 children via single creator assignment slot.
  - Runtime: `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/task-assignees-runtime-smoke.kt` (Step 4) exercises end-to-end sync.
- **Negative Boundaries**: Missing capability returns 426; outsider receives 0 tasks; assignment does not grant app membership.
- **Status**: **ACCEPTANCE INCOMPLETE.** Automated suites are reported passed; the independent-account root/backfill/future-child runtime scenario remains unproven (G2 partial, G3–G5 open).

### Criterion 2: Shared Deadline/Completion, Single Canonical Content
- **Contract**: Creator and Alice edit deadline, completed status, and title; independent local accounts converge on single creator-owned row in `todos`. Zero recipient-owned copies in `todos`.
- **Implemented Code**: `CanonicalRecordRef` & `TaskRef` (Phase 01), `RoomAccountStore.upsertTodoLocally` & intent masks (Phase 03), `task-json-merge.kt` (Phase 05).
- **Prepared Verification**:
  - API: `scripts/task-assignees-live-smoke.mjs` verifies convergent edits and asserts `ownerUserId == creator.id` on all returned records.
  - Storage: Asserts 1 `todos` row under creator, 0 under Alice in MongoDB.
- **Negative Boundaries**: Stale CAS is rejected with 409 conflict; unaccepted versions remain in outbox.
- **Status**: **ACCEPTANCE INCOMPLETE.** Module tests and Hub integration targets are reported passed; independent-client canonical row inspection remains unproven (G2 partial, G3–G5 open).

### Criterion 3: Child-Only Bob Privacy and Native Edit
- **Contract**: Bob assigned directly to child task edits child without parent or sibling exposure (`hierarchyCoverage: "child-only"`). Parent is private on Hub and omitted from Bob's response bodies and Room storage.
- **Implemented Code**: `task-hierarchy-projector.kt` (Phase 01), `live_root_authorization` (Phase 02), `DesktopTaskSurfaceAccess` (Phase 08).
- **Prepared Verification**:
  - API: `scripts/task-assignees-live-smoke.mjs` (Phase 6) asserts Bob receives only child 2; root and child 1 are absent from response.
  - Native: `DesktopTaskSurfacesTest.testChildOnlyTaskRenderedFlatWithoutParentContext`.
- **Negative Boundaries**: Server requires live root parent; orphan child cannot confer direct or inherited grant.
- **Status**: **ACCEPTANCE INCOMPLETE.** Android/Desktop unit results are reported; child-only privacy and actual native edit journeys remain unproven (G2 partial, G3–G5 open).

### Criterion 4: Full-Group-Only Parent Completion
- **Contract**: Parent completion requires completion of all actual group children on server, never Bob's visible single-child subset.
- **Implemented Code**: `TaskHierarchyProjector` (Phase 01), `RoomAccountStore.completeGroupAtomic` (Phase 03).
- **Prepared Verification**:
  - API: Bob completing child 2 leaves root parent incomplete; creator/Alice completing all children triggers parent completion.
- **Negative Boundaries**: Staged/partial scans or child-only subsets never trigger automatic parent completion writes.
- **Status**: **ACCEPTANCE INCOMPLETE.** Shared and Hub suites are reported passed; full-group-versus-child-only behavior is not evidenced end to end (G2 partial, G3–G5 open).

### Criterion 5: Creator-Only Privileged Operations on Every Path
- **Contract**: Assignees cannot delete tasks, restore tombstones, create children, mutate relationships, or edit non-editable fields (`createdAt`, `parentTaskId`, `ownerUserId`, unknown JSON).
- **Implemented Code**: Hub assignment policy validator (Phase 02), `RoomAccountStore` admission guards (Phase 03).
- **Prepared Verification**:
  - API: `scripts/task-assignees-live-smoke.mjs` (Phase 7) tests hostile assignee delete, child creation, and immutable field edits; verifies explicit `OPERATION_FORBIDDEN` or conflict.
- **Negative Boundaries**: Privileged action denial leaves read access intact (`OPERATION_FORBIDDEN`), whereas read access loss triggers purge (`ACCESS_DENIED`).
- **Status**: **ACCEPTANCE INCOMPLETE.** Module tests are reported; the full hostile API and stale-native-action matrix has not been recorded (G2 partial, G4/G5 open).

### Criterion 6: Explicit Conflicts and Unique Concurrent Reassignment
- **Contract**: Concurrent reassignment of the same task slot version results in exactly one winner and one conflict; stored slot count remains 1.
- **Implemented Code**: MongoDB unique index `(userId, tableName, rowId)` on `taskAssignees` and replica-set transaction guard (Phase 02).
- **Prepared Verification**:
  - API: `scripts/task-assignees-live-smoke.mjs` (Phase 8) tests concurrent CAS updates from identical version.
- **Negative Boundaries**: Stale CAS returns 409 conflict, never creates duplicate slot or overwrites newer assignment.
- **Status**: **ACCEPTANCE INCOMPLETE.** Hub integration tests passed, but Cycle 3 review records collision-recovery test coverage as missing; full concurrency/race acceptance remains open (G2 partial).

### Criterion 7: Backfill, Revocation, Partial-Scan Safety and No Replay
- **Contract**: Grant backfills existing tasks. Revocation immediately purges active base, draft, outbox payloads, and cancels reminders. Retains non-replayable content-free evidence in `access_loss_evidence`. Regrant does not auto-replay purged unsent edits.
- **Implemented Code**: `RoomAccountStore.purgeRevokedTaskAccess` (Phase 03), `staged-full-scan.kt` (Phase 05), `ReminderSchedulingCoordinator` (Phase 05).
- **Prepared Verification**:
  - Runtime: `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/task-assignees-runtime-smoke.kt` (Step 6) verifies DB rows purged and `access_loss_evidence` created with no task content.
- **Negative Boundaries**: Incomplete or interrupted scan does not imply revocation or deletion.
- **Status**: **ACCEPTANCE INCOMPLETE.** Reported automated tests do not establish offline revoke/restart/reconnect, full purge inspection or native reminder cancellation (G2 partial, G3–G5 open).

### Criterion 8: Grant Overlap and Same-Record Regrant
- **Contract**: Removing parent or direct grant preserves other effective grant (union). Regrant restores original live task and slot without synthetic deletion markers or replaying discarded drafts.
- **Implemented Code**: Access union logic in Hub (Phase 02) and `RoomAccountStore.hydrateTaskAccess` (Phase 03).
- **Prepared Verification**:
  - Runtime: `TaskAssigneesRuntimeSmoke` verifies regrant hydrates fresh server state.
- **Negative Boundaries**: Slot tombstones exempt from generic cleanup; stale owner devices cannot recreate past slots.
- **Status**: **ACCEPTANCE INCOMPLETE.** Automated tests are reported; overlapping grants and same-record regrant are not evidenced in an integrated runtime (G2 partial, G3–G5 open).

### Criterion 9: Forgery, Collision, Orphan, and Recipient Integrity
- **Contract**: Forged relations, duplicate task IDs across different owners, missing/deleted parents, and invalid recipients fail closed.
- **Implemented Code**: `CanonicalRecordRef(appId, ownerUserId, tableName, rowId)` enforcement (Phase 01, 02, 03).
- **Prepared Verification**:
  - API: `scripts/task-assignees-live-smoke.mjs` (Phase 4) creates duplicate `rowId` under Creator2; verifies complete partition isolation.
- **Negative Boundaries**: No global UUID probability lookup; ownerUserId is always verified against authenticated actor or canonical stored owner.
- **Status**: **ACCEPTANCE INCOMPLETE.** Mongo integration tests passed, but every forgery/collision/orphan scenario and its row-level inspection are not separately evidenced (G2 partial, G3–G5 open).

### Criterion 10: Lossless Upgrade, Unknown JSON, and Account Fencing
- **Contract**: Existing private tasks survive Room 4→5 upgrade unassigned. Two-stage binding holds legacy rows with `ownerBinding = LOCAL_ACCOUNT` until authenticated `AccountIdentity` matches partition. Unknown JSON preserved through edits. Account switch fences async results via `accessGeneration`.
- **Implemented Code**: `DatabaseMigrations.MIGRATION_4_5` (Phase 03), `task-json-merge.kt` (Phase 05), `DesktopTaskSurfaceAccess` (Phase 08).
- **Prepared Verification**:
  - Shared tests: `room-migration-to-v5-test.kt` exercises lossless v4→v5 schema migration.
  - Runtime: `TaskAssigneesRuntimeSmoke` tests account fencing and unknown JSON retention.
- **Negative Boundaries**: Unauthenticated store cannot send or guess ownership; invalid `AccountKey` refuses binding.
- **Status**: **ACCEPTANCE INCOMPLETE.** Shared JVM migration tests are reported; actual old-account binding and runtime account-fencing scenarios remain unproven (G3–G5 open).

### Criterion 11: Other Apps Remain Viewer-Only and Unchanged
- **Contract**: Apps without `assignmentPolicy` (e.g. Cham-lang) remain 100% byte- and behavior-compatible viewer-only. Standard sync without `collaborationVersion: 1` succeeds on un-opted-in apps.
- **Implemented Code**: Conditional policy branch in Hub sync engine (Phase 02), Hub admin schema DTOs (Phase 04).
- **Prepared Verification**:
  - Hub unit suites: `task_assignment_integration.rs` verifies non-opted-in apps accept legacy push/pull.
- **Negative Boundaries**: No global replica-set fence dependency or 426 cutoff for un-opted-in apps.
- **Status**: **ACCEPTANCE INCOMPLETE.** Admin and schema-version unit/integration tests are reported; live external viewer-only app compatibility remains unproven (G2/G6 incomplete).

### Criterion 12: Real Disposable Hub and Native UI Proof Before Release
- **Contract**: Requires real disposable Hub with Creator/Alice/Bob/Outsider, direct Mongo row inspection, and actual connected Android + desktop UI verification before release unblocking.
- **Implemented Harnesses**:
  - `desktopApp/build.gradle.kts`: registered `runTaskAssigneesRuntimeSmoke` JavaExec task.
  - `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/task-assignees-runtime-smoke.kt`: standalone Kotlin runner.
  - `scripts/task-assignees-live-smoke.mjs`: API smoke runner.
- **Status**: **NOT PASSED.** Harnesses are prepared and selected Mongo targets passed, but dedicated end-to-end feature smoke, actual coordinator, native Android/Desktop and G6 audit are not recorded as run.

---

## 3. Dedicated Verification Harnesses Delivered

### 1. JavaExec Desktop Runtime Smoke
- **Source**: `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/task-assignees-runtime-smoke.kt`
- **Gradle Task**: `./gradlew :desktopApp:runTaskAssigneesRuntimeSmoke`
- **Configuration**:
  - Main Class: `dev.tabbyrush.desktop.TaskAssigneesRuntimeSmoke`
  - Classpath: `sourceSets["test"].runtimeClasspath`
  - Depends On: `testClasses`
- **Environment Invariants**:
  - Requires `DISPOSABLE_RUN_AUTHORIZATION=true` or `TABBYRUSH_ALLOW_DISPOSABLE_SMOKE=1`.
  - Requires `APPROVED_HUB_ORIGIN` targeting local loopback (`localhost` / `127.0.0.1`).
  - Requires `TABBYRUSH_API_KEY`, `TABBYRUSH_SMOKE_CREATOR_EMAIL/PASSWORD`, `TABBYRUSH_SMOKE_ALICE_EMAIL/PASSWORD`, `TABBYRUSH_SMOKE_BOB_EMAIL/PASSWORD`.
  - Fails closed with exit code 1 if credentials or loopback validation fail.

### 2. Live API Feature Smoke
- **Source**: `scripts/task-assignees-live-smoke.mjs`
- **Execution**: `node scripts/task-assignees-live-smoke.mjs --origin <url> --app-id <appId> --confirm-app <appId>`
- **Coverage**:
  - Disposable user creation (Creator, Alice, Bob, Outsider, Creator2).
  - Whole-app capability cutoff (HTTP 426 `COLLABORATION_REQUIRED`).
  - Canonical task hierarchy creation with exact owner tuple acknowledgement.
  - Duplicate `rowId` under Creator2 proving owner namespace isolation.
  - Assignment slot CAS in `taskAssignees`.
  - Single canonical row assertions (1 creator-owned row, 0 recipient copies).
  - Child-only privacy boundary (parent and siblings unexposed to Bob).
  - Hostile assignee privilege rejections (delete, create child, immutable field edit).
  - Bounded `accessChecks` (<= 100 items, <= 256 KiB) and stable revision E.

---

## 4. Operational & Release Blockers

The following items remain active release holds:
1. **Interactive Native UI Verification**: Provider interactive approval timed out during earlier cycles; native Compose desktop and Android UI workflows have not been observed in an interactive session.
2. **Connected Android Device Matrix**: Instrumentation on physical/connected Android devices was not run.
3. **Multi-Node Replica Set Staging**: Local proof uses disposable single-node replica set; multi-instance cluster rehearsal remains pending.
4. **Production Migration Authorization**: No production database migration or Hub app schema enablement has been authorized.
