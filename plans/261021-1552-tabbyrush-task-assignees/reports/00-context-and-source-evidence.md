# Planning context and source evidence

Date: 2026-10-21. Scope: planning only; no implementation, build, test, API, deployment, or native UI validation.
Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`.
Branch observed: `feature/parent-child-tasks-deadline-surfaces`.

## Required context
- Input: [brainstorm](../../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md), supplied in session with source anchors.
- Read: TabbyRush `README.md`; `docs/codebase-summary.md`; `docs/code-standards.md` (all sections); `docs/system-architecture.md`; `docs/project-overview-pdr.md`; `docs/local-storage-and-sync.md`; relevant design-guideline sections.
- Summary generated 2026-10-20; filesystem last-write UTC 2026-10-20 16:18:23. Less than three days old. Conditional `/evc-cmd-scout` not run; no new scout artifact exists or is implied. `scout/` reserved and empty.
- Planning/research skills loaded directly from published `C:/Users/loidi/.omp/agent/skills/`; planning references loaded, including organization, output standards, architecture-first gate, research, codebase understanding and solution design. Mermaid instructions loaded for planned diagrams.
- Local `.omp` workflows absent; published AGENTS, primary/development/orchestration/documentation workflows read. Optional `docs/development-rules.md` absent; `docs/code-standards.md` and published development rules apply.
- Plan activation helper executed from published install. It reported `EVCRATE_SESSION_ID not set`; active-plan state does not persist. Explicit plan path supplied to every agent instead.

## Independently inspected source anchors
All TabbyRush paths below relative to `G:/ws/sharing/doggo`; Hub paths relative to `G:/ws/sharing/glean-hub`.

| Source | Observed constraint |
|---|---|
| Hub `glean-hub-core/glean-hub-sync-engine/src/sharing.rs:40-78` | Five mapping fields; viewer-only documentation; same parent-id field reused for relationship and child linkage. |
| Hub `glean-hub-core/glean-hub-sync-engine/src/protocol.rs:19-40,58-99,108-162` | No owner identity/access capability in standard envelopes; exact accepted tuple is table/row/version. |
| Hub `glean-hub-plugin/glean-hub-sync/src/models/record.rs:12-25,53-62` | Stored owner exists as `userId`, omitted from pull record; canonical records scoped by app/owner/table/row. |
| Hub `glean-hub-plugin/glean-hub-sync/src/models/app.rs:35-44` | Effective schema equality compares only current sharing fields; additive policies must participate in no-op/concurrency behavior. |
| Hub `glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:191-209,264-268,303-395` | Existing map owner-scoped; lookup/upsert selects caller; non-owner permission relaxation alone creates wrong identity; deletion bypasses current non-deleted guard. |
| Same Hub file `:492-557,559-658` | Grant read and content read separate; comment explicitly permits one-cycle stale grant. Pull ordering/checkpoint uses syncedAt+rowId: identity collisions require a complete unique pagination tie-breaker in collaboration-capable mode. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-dtos.kt:19-79` | Task-only table defaults; no owner identity. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/server-base-entity.kt:10-36` | Local base primary key is only rowId. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt:494-550` | Scan staging hardcodes todos; completed absence is stored as deleted; cannot support revocation/regrant unchanged. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt:249-285` | Deleted identities rejected; existing child edit requires local parent. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:37-58,107-128,150-156,191-204` | Full scan before writes already exists; incremental drain is not a permission proof; late-child cleanup and conflict reapply require permission-aware redesign. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector.kt:93-164` | Missing-parent children unresolved; root completion derives from locally grouped children. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-contracts.kt:7-43` | Account/generation-fenced actions but task identity only String. Owner-scoped collision protection must reach native intents/navigation/scheduling too. |
| Native `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt:5-19` | Existing public mutation/read interface to migrate cleanly; no compatibility aliases planned. |

## File-layout findings
- Desktop production Kotlin root: `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/`, not `src/jvmMain`.
- Shared JVM SQLite tests: `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/`.
- Shared behavioral tests: `shared/src/commonTest/kotlin/dev/tabbyrush/shared/{contract,sync,auth,ui}/`.
- Hub admin registration and editing are separate existing files: `glean-hub-app/src/pages/admin/app-registration.tsx`, `edit-app.tsx`; reuse those workflows.
- Existing native smoke/provision scripts: `scripts/hub-live-smoke.mjs`, `scripts/hub-schema-provision.mjs`; reuse established patterns, no separate task API.

## Planning invariants
Creator controls assignment/delete/restore/create-child/structure. Parent grant inherits to live children; child-only grant exposes no private parent/sibling content. Assignment does not grant app access. At most one direct assignment, version-conflicting concurrent owner devices, canonical shared content, explicit conflicts, unknown JSON retained, explicit non-destructive Room migration. Standard push/pull/delta only; no strict sync, invitations, notifications subsystem, general roles, Cham-lang migration, production actions.

## Unresolved questions
Technical wire/config/slot/access-boundary/older-client choices must be settled by the architecture decision record and phase contracts before implementation. No user/product question currently blocks planning.
