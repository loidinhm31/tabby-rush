# TabbyRush task assignees — brainstorm decision

Date: 2026-10-21
App ID: `tabby-rush`; repository directory remains `doggo`.
Status: brainstorm direction established; implementation not authorized or started.
Evidence: source inspection in TabbyRush, Glean Hub, Cham-lang. No runtime/API/UI validation performed.

## 1. Problem and final artifact

Add collaborative task assignment to TabbyRush Android and desktop. Creator retains ownership; one direct assignee can edit the same task. Parent assignment extends to children; child-only assignment does not expose the rest of the group.

This session's artifact is this report, not code or a detailed implementation plan. Future feature artifact: native assignee controls and permission-aware task surfaces, persisted offline assignment/task state, and matching server-enforced authorization within existing Hub standard sync.

User clarification: **reuse Hub's SharingConfig infrastructure as the base, customize it for tabby-rush. Do not copy/paste Cham-lang models, table names, or client code.** `collectionSharedUsers` is the reference relationship pattern, not a required TabbyRush table name.

## 2. Confirmed requirements and boundaries

- At most one direct assignee per task; no assignee means creator-only unless parent access applies.
- Creator and authorized assignees share one title/content, due date, and completed/incomplete state. No personal completion copies.
- Creator alone assigns, reassigns, unassigns, deletes tasks, and creates children.
- Assignees edit existing task content, deadline, completion, and existing supported reminder fields. Ownership, identity, assignment, and structural linkage are not ordinary editable content.
- A parent assignee controls the parent and all live children, including future children created by creator.
- A child can have a different direct assignee. Creator, parent assignee, and child assignee can all edit that child.
- A child-only assignee cannot edit/read private parent content or siblings. Show the assigned child without fabricating a full parent record.
- Existing two-level hierarchy and no-reparent behavior remain.
- Existing Hub users with TabbyRush app access only; assignment does not grant app access.
- Offline edits remain supported, with explicit version conflicts. Access revocation takes effect on server after the relationship change commits and on a disconnected client after reconciliation; remote erasure while offline is impossible.
- No invitations, presence, real-time collaborative typing, new assignment-notification subsystem, separate task API/service, or new sync protocol.
- Standard `/push`, `/pull`, `/delta` only. No strict-sync restoration.
- Keep other apps' viewer-only sharing behavior. No production deployment/data migration authorized here. No fixed delivery deadline supplied.

## 3. Existing implementation — verified source

Paths under Hub below are relative to `G:/ws/sharing/glean-hub`.

| Surface | Evidence | Consequence |
|---|---|---|
| Standard sync | TabbyRush `README.md:3–5`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt:25–55,79` | Extend the active protocol; no replacement. |
| Task schema | TabbyRush `contract/task-data-contract.kt:18–35`; `integration/glean-hub/tabbyrush-schema.json` | One `todos` table, self-referential `parentTaskId`; no assignment model today. |
| Native local state | TabbyRush `docs/local-storage-and-sync.md:15–26,34–38` | Preserve confirmed base/outbox/draft separation and account fencing. |
| Generic sharing descriptor | Hub `glean-hub-core/glean-hub-sync-engine/src/sharing.rs:40–50` | Existing configurable relationship table, recipient field, target table, and child tables. Reuse this extension point. |
| Cham-lang reference | Hub `embed-app/cham-lang/cham-lang-app-schema.json:3–8,159–166`; `src/adapters/web/IndexedDBCollectionAdapter.ts:133–205` under that app | Relationship rows are ordinary versioned sync data; unshare is soft deletion. Reuse concept, not React/Dexie implementation. |
| Viewer-only server | Hub `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:303–337`; `sharing.rs:8–18` | Existing configuration alone cannot grant editing. Legacy editor claims in some docs are stale. |
| Canonical row identity | Hub `sync_service.rs:191–209,360–375`; `models/record.rs:9–60` | Current upsert uses caller `userId`; relaxing permission alone would write an assignee-owned copy. |
| Flat hierarchy mapping | Hub `sharing.rs:60–78`; `sync_service.rs:594–629` | Same-table task inheritance and distinct relationship/hierarchy fields require configuration/handler extension. |
| Sharing Config admin surface | Hub `glean-hub-app/src/pages/admin/app-registration.tsx:89–105`; `src/services/sync-admin-service.ts:26–43` | Reuse existing admin configuration workflow and update its contract/help; no second configuration screen required. |
| Username lookup | Hub `glean-hub-server/src/routes/auth.rs:132–153` | Reuse JWT-authenticated `/api/v1/auth/lookup?username=...`; no admin user-list permission needed for assignment. |
| Missing-parent projection | TabbyRush `contract/task-hierarchy-projector.kt:121–131`; `data/room-account-store.kt:281–285` | Child-only tasks currently become unresolved and child edits require a locally available parent. Both paths need permission-aware behavior. |
| Scan absence | TabbyRush `data/tabbyrush-dao.kt:530–549` | Completed-scan absence currently becomes `deleted=true` and retained local evidence. Revocation must be distinguished from actual deletion and permit legitimate regrant. |

TabbyRush abbreviated `contract/`, `data/`, `sync/`, and `ui/` paths refer to `shared/src/commonMain/kotlin/dev/tabbyrush/shared/`.

## 4. Evaluated approaches

| Approach | Pros | Cons | Decision |
|---|---|---|---|
| Customize existing SharingConfig and relationship-based standard sync | Reuses registry/configuration, auth, versioning, outbox, pull, and conflict review; aligns with user intent | Requires safe canonical owner resolution, editable access, and same-table inheritance | Recommended |
| Separate collaboration API/plugin | Task rules isolated from generic sharing | Parallel mutation path, extra client integration, duplicated reconciliation; unnecessary for current scope | Rejected |
| Only native changes or copy Cham-lang sharing unchanged | Smaller apparent change; existing viewing works conceptually | Server rejects collaborative edits; copying collection model mismatches todos hierarchy and one-assignee rules | Not feasible for required behavior |

## 5. Recommended solution

### 5.1 Task-native relationship model

Keep content in `todos`. Add a task-native assignment relation, proposed name `taskAssignees`, with stable `id`, `taskId`, recipient `userId`, and normal sync/deletion metadata. Names are proposed, not existing API fields.

Use one stable assignment slot per canonical task. Creator reassigns by changing that slot, not by independently inserting competing active recipients. Hub must enforce uniqueness and version conflicts, including concurrent owner devices. Regrant must work after unassignment.

Do not duplicate the authoritative assignment as `todos.assigneeId`. Native UI may derive/cache the assignee from the relationship. Creator ownership comes from trusted stored Hub record ownership, not editable task JSON.

### 5.2 Extend the existing SharingConfig contract

Configure TabbyRush with its own relation/table/field mapping. Existing fields describe sharing relation → task and recipient. Add the smallest explicit configuration needed for:

- Editable shared content, defaulting to current viewer-only behavior when absent.
- Same-table hierarchy via `todos.parentTaskId`, separate from assignment relation's `taskId`.
- One direct recipient, creator-managed relationships, owner-only delete/create policies.

These are policy requirements, not a finalized JSON schema. Choose exact field names/validation in detailed planning. Do not build a general role engine or hardcode `if app_id == tabby-rush` inside sync. Reject unsupported/ambiguous mappings rather than silently treating a task-specific config as ordinary collection sharing.

Existing registration/schema-update API already accepts SharingConfig. Extend that contract, matching admin TypeScript types, JSON help/validation, provisioning input, and docs. Preserve existing viewer-only configurations.

### 5.3 Effective access

| Actor | Read/edit content | Assignment/deletion | Create children |
|---|---|---|---|
| Creator | Own parent and children | Allowed | Allowed |
| Parent assignee | Assigned parent and all its children | Denied | Denied |
| Child-only assignee | Assigned child only | Denied | Denied |
| Unrelated/former assignee | Denied unless another current grant applies | Denied | Denied |

Removing a direct child assignment does not remove access still inherited from a parent. Removing parent assignment does not remove a separately assigned child. Inheritance is computed, not materialized as duplicated child-share rows.

### 5.4 Canonical writes and identity

Resolve the stored creator-owned task before permission/version checks; all authorized edits update that same row. Child ownership remains with creator. Preserve standard schema validation, unknown JSON retention, exact-version acknowledgements, and explicit conflict review.

Hub identity is currently user/table/row scoped, not globally row-ID unique. Bind every relationship to the correct stored owner. Cross-user writes need an unambiguous canonical target/reference carried through client storage and sync; a caller-provided owner selector is never authority. Planning must choose the minimal envelope/metadata extension and prove duplicate row IDs across owners cannot leak or overwrite data. Do not assume UUID probability provides authorization.

Authorize relationship creation/update/deletion against target ownership; reject forged grants and protect assignment/identity/parent fields even when UI controls are hidden. Deletion and restore paths cannot bypass creator-only policy. Preserve access checks through delta, not just standalone push.

Source-derived risk: current generic grant lookup trusts active sharing rows naming a recipient, while the content-write guard does not cover sharing-table writes (`sync_service.rs:303–337,559–590`). **[INFERENCE]** This may permit self-granting access to a known target ID. Not runtime-demonstrated. Do not carry this trust assumption into the new configurable assignment policy; unrelated broad security remediation remains separate scope.

### 5.5 Native behavior and access lifecycle

- Editor/list: optional single-person assignment via existing username lookup; show direct/inherited access distinctly. Creator-only controls for assignment/delete/add child.
- Persist assignments and authoritative access/owner metadata within existing account partitions, base/outbox/draft workflow. Add explicit Room migrations; no destructive reset.
- Child-only assignment: dedicated partial-hierarchy projection; no private parent-title/sibling fetch merely to satisfy current projector/store checks. Do not compute parent completion from a visible subset.
- Gate all mutation entry points, including widgets/sticky surfaces and queued outbox operations; UI hiding alone is not authorization.
- Use existing full-scan flow to backfill older newly assigned records, extending task-only assumptions where required. No inference from incomplete pages.
- Treat access loss separately from task deletion. Hide/remove inaccessible active projections and cancel reminders after authoritative reconciliation; do not replay queued edits against lost access or resurrect the task. Keep only appropriate review evidence under defined retention rules.
- A later regrant must restore the original live task, not require a copied identity or appear permanently deleted.
- Existing completed scans are not server snapshots. Detailed planning must define a coherent grant/revocation reconciliation boundary; a multi-page scan alone is not proof of current permission. Revalidate access before replay/exposure as required.
- Retain existing reminder behavior for accessible tasks; no new assignment-specific notification policy. Offline cached information cannot be remotely erased instantly.

## 6. Likely touchpoints

### TabbyRush

- `integration/glean-hub/tabbyrush-schema.json`; `scripts/hub-schema-provision.mjs` schema/config input handling.
- Shared task/UI/action contracts, hierarchy projector, editor/list and application action handling.
- Room entities, DAO, account store, migration chain; keying must account for assignment tables/canonical identity.
- `sync/hub-sync-dtos.kt`, `hub-sync-client.kt`, `task-json-merge.kt`, `staged-full-scan.kt`, `account-sync-coordinator.kt` and existing dispatch/reconciliation paths.
- Current transport, DTO defaults, and page validation assume `todos`; relation sync requires a clean end-to-end extension, not only schema registration.
- Android task-widget actions/projection, desktop sticky-task surfaces, shared reminder scheduling/access invalidation.
- Existing domain/sync/Room/platform validation; API/schema/local-storage/architecture docs and changelog after implementation.

### Glean Hub

- `glean-hub-core/glean-hub-sync-engine/src/sharing.rs`; protocol metadata if canonical references require it.
- `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs`: owner resolution, relationship authorization, content edits, pull visibility, revocation, deletion/restore handling.
- Sync models, schema/SharingConfig validation and app-service persistence/version handling; narrowly scoped indexes if assignment uniqueness needs them.
- `glean-hub-app/src/services/sync-admin-service.ts`, existing Sharing Config registration/schema workflow.
- Existing auth lookup reused; validate recipient app eligibility server-side.
- Compatibility/security coverage, sharing/config/standard-sync documentation. No Cham-lang feature migration.

## 7. Acceptance and validation criteria

1. Creator assigns Alice to a root; Alice receives the existing root/children and creator-added future children.
2. Alice and creator change due date and complete/reopen tasks; independent accounts converge on the same canonical record, without recipient-owned duplicates.
3. Bob assigned a child can read/edit it with no parent/sibling content exposure. Alice retains inherited child editing. Child display/edit works on Android and desktop.
4. Parent completion remains based on the creator/group's actual children, never Bob's visible subset. Existing parent completion behavior remains for full-group users.
5. Assignees cannot assign/unassign/reassign/delete/restore/create children or alter ownership/parent linkage through direct API requests, tombstones, delta, widgets, or stale clients.
6. Conflicting creator/assignee edits are surfaced for review. Concurrent reassignment produces at most one direct assignee and no silent access escalation.
7. New assignment backfills old task records. Completed reconciliation after revocation removes active inaccessible tasks/reminders and prevents queued edits from replaying. Partial/failed scans do not imply access loss.
8. Parent/direct-child grant overlap is correct when either grant is removed. Regrant restores the same live record after earlier revocation.
9. Forged relationship rows, duplicate IDs across owners, missing/deleted parents, and invalid recipients cannot grant access or update another owner's row.
10. Existing private tasks survive Room/schema upgrade with no new assignee. Unknown JSON survives supported edits. Account switching fences stale async results.
11. Existing Cham-lang config remains viewer-only; unrelated app sync behavior remains unchanged.
12. Proof before release: disposable local Hub smoke with creator/Alice/Bob/outsider accounts, real canonical writes and revocation/conflict paths; native Android + desktop UI exercise including child-only editing and offline reconnect. These are future required checks, not completed evidence.

## 8. Risks, rollout, dependencies

- Highest risk: canonical identity and authorization correctness; implement server enforcement before enabling native assignment controls.
- Next: visibility transitions during paginated scans, revocation versus tombstones, partial hierarchy behavior, and offline outbox handling.
- Schema evolution does not generate native models/UI or Room migrations. Provisioning and client release need explicit coordinated updates.
- Additive config defaults preserve legacy viewer apps. Older TabbyRush clients are not assignment-capable; compatibility/enablement must be demonstrated, not inferred from unknown-JSON preservation.
- Required access: development copies of both repositories and disposable local Hub/database. No production credentials needed for feasibility work.
- No build/tests/live API calls were run during this brainstorm. Two read-only scouts completed; key findings independently inspected in source. Feasible as an extension, not available by flipping today's Sharing Config alone.

## 9. Next steps and unresolved questions

Next: ask whether to create a detailed cross-repository implementation plan. Carry this report into planning; no implementation until that separate step is authorized.

Unresolved user/product questions: none blocking this brainstorm direction.

Detailed planning must settle technical contracts: exact SharingConfig additions; canonical owner/reference wire shape; assignment slot/index enforcement; coherent access reconciliation; supported older-client enablement. Do not silently defer these to implementation.
