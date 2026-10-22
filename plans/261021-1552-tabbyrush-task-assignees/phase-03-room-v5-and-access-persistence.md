# Phase 03: Room v5 and access persistence

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave B with02/04; depends on01. Sole owner of RoomAccountStore, DAO/entities, all migrations, Android/JVM Room builders and SQLite tests. Phase05 cannot access implementation ownership by integration convenience. Construction uses frozen01 interfaces; sync/runtime gates wait for05/07/08.

## Overview
- Date: 2026-10-21. Description: Room v5 and access persistence. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Cycle 3 review reports `:shared:jvmTest` 179/179 with Room v5 migration/access coverage; selected QA reports 329/329 shared JVM tests ([review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md), [QA](../../reports/qa-261022-1521-selected-test-suite-results.md)).
- Boundary: module migration tests do not establish the unrun populated Room/coordinator runtime smoke or connected Android acceptance.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- Room v4 keys only rowId and scan absence becomes deleted. Rebuild identity keys and distinguish explicit unavailable from owner tombstone.
- AccountKey is a one-way origin/user hash. SQL migration has no authenticated user; two-stage binding preserves legacy rows rather than inventing owner/default or deleting offline data.
- Privacy-first access loss intentionally destroys unsent collaborative payloads; unrelated owner-private rows and private legacy archive remain intact.

## Requirements
- Room v5 explicit migration preserving populated old schemas/base/outbox/draft/evidence/ledger. Canonical keys include owner, table, row within existing account partition; preserve stable opId FIFO/localRevision behavior.
- Implement phase01 real storage operations: owner binding, capabilities/mutations, staged data+access, final atomic promotion, exact acknowledgement and content-free invalidation. No destructive reset or old schema edits.
- Authorized ChildOnly edit does not require a local private parent, but cannot bypass trusted server liveness/coverage; creator new child still requires live root and creator capability.
- Implement the confirmed privacy-first purge on authoritative shared access loss: remove revoked content and unsent collaborative draft/outbox/frozen-envelope/conflict/staging payloads; retain only canonical identity, opId, versions, timestamps and reason as content-free, non-replayable metadata. No recovery quarantine or auto-replay after regrant; preserve unrelated owner-private tasks/private legacy archives.

## Architecture
- Migrate legacy identity to ownerBinding=LOCAL_ACCOUNT with nullable ownerUserId. Use explicit non-null binding discriminator/key for unresolved SQL rows (SQLite nullable composite uniqueness cannot be trusted); unresolved is local-only, not a fake canonical owner. Keep row/table and dependencies intact.
- initializeOwnerBinding(AccountIdentity) validates AccountKey.create(origin,userId)==opened partition; transactionally binds base/outbox/drafts/staging/ledger/evidence references before any sync/shared actions. Wrong identity fails without mutation. Interrupted binding rolls back and is idempotent. Preserve private offline read projection before binding; no shared action or outgoing dispatch until complete.
- task_access stores canonical rights/coverage and reconciled revision; access_check_staging binds key decisions to scan/account generation/E. Relation rows retain raw JSON separately by table-aware base/outbox keys but are excluded from task/reminder queries.
- Storage exposes SyncStorage/CollaborationStore from01 rather than exposing DAO to05. Atomic promotion merges base+relationships+access and checkpoints while preserving newer local intents. Authoritative invalidation may commit separately on denial even if later content scan fails; unfinished content staging alone changes nothing.
- Persist accessGeneration in account sync metadata. Increment it atomically on authoritative access loss, retire unfinished staging and reject old-generation content or native publications within the same account. Regrant requires a fresh successful reconciliation. Record late exact acknowledgements only as metadata; never rebuild purged payloads. OPERATION_FORBIDDEN marks only the forbidden intent non-replayable and preserves valid task access.
- Purge revoked remote JSON/title/deadline/assignee and draft/outbox/envelope/conflict payloads; retain canonical ref/opId/base+submitted version/timestamps/reason only in access_loss_evidence, non-replayable. Ledger cancellation retains only non-sensitive occurrence identity. No generic archive of revoked snapshots.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-database.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/database-migrations.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/server-base-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/outbox-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/editor-draft-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/scan-staging-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/account-sync-meta-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/legacy-sync-evidence-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-delivery-ledger-entity.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/schedule-reconciliation-delta.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/persisted-schedule-snapshot.kt` — modify; Canonical Room v5 keys, owner binding, access staging/purge and reminder storage implementation.
- `G:/ws/sharing/doggo/shared/src/androidMain/kotlin/dev/tabbyrush/shared/data/android-database-builder.kt` — modify; Register complete non-destructive migration paths on both builders.
- `G:/ws/sharing/doggo/shared/src/jvmMain/kotlin/dev/tabbyrush/shared/data/jvm-database-builder.kt` — modify; Register complete non-destructive migration paths on both builders.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/task-access-entity.kt` — create; Authoritative access/revision and content-free evidence storage; new schema only (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/access-check-staging-entity.kt` — create; Authoritative access/revision and content-free evidence storage; new schema only (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/access-loss-evidence-entity.kt` — create; Authoritative access/revision and content-free evidence storage; new schema only (proposed new path).
- `G:/ws/sharing/doggo/shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/5.json` — create; Authoritative access/revision and content-free evidence storage; new schema only (proposed new path).
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v1-to-v2-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v2-to-v3-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-to-v4-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/native-sync-safety-sqlite-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/adversarial-sync-sqlite-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-store-sqlite-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-sync-sqlite-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-scheduling-coordinator-sqlite-smoke-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-teardown-barrier-sqlite-test.kt` — modify; Own SQLite behavioral fixtures and affected migration/store callers.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store-test.kt` — modify; Migrate ledger/store tests and real Android SQLite behavior.
- `G:/ws/sharing/doggo/shared/src/androidInstrumentedTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` — modify; Migrate ledger/store tests and real Android SQLite behavior.
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-to-v5-test.kt` — create; Populated old-schema migration, binding and atomic access/purge tests (proposed new path).
- `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/collaborative-access-sqlite-test.kt` — create; Populated old-schema migration, binding and atomic access/purge tests (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-database.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/database-migrations.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/server-base-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/outbox-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/editor-draft-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/scan-staging-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/account-sync-meta-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/legacy-sync-evidence-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-delivery-ledger-entity.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/schedule-reconciliation-delta.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/persisted-schedule-snapshot.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/androidMain/kotlin/dev/tabbyrush/shared/data/android-database-builder.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmMain/kotlin/dev/tabbyrush/shared/data/jvm-database-builder.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/task-access-entity.kt` | create (proposed) | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/access-check-staging-entity.kt` | create (proposed) | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/access-loss-evidence-entity.kt` | create (proposed) | 03 exclusively |
| `G:/ws/sharing/doggo/shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/5.json` | create (proposed) | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v1-to-v2-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v2-to-v3-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-to-v4-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/native-sync-safety-sqlite-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/adversarial-sync-sqlite-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-store-sqlite-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-sync-sqlite-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-scheduling-coordinator-sqlite-smoke-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-teardown-barrier-sqlite-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/data/reminder-occurrence-store-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/androidInstrumentedTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` | modify | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-to-v5-test.kt` | create (proposed) | 03 exclusively |
| `G:/ws/sharing/doggo/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/collaborative-access-sqlite-test.kt` | create (proposed) | 03 exclusively |

## Implementation Steps
1. Use LSP references for entity constructors, DAO methods and store API. Implement the01 interfaces fully in owned files, including all methods used by05; replace concrete DAO coupling with typed snapshots/results. Route source outside this manifest to its owner.
2. Design and implement explicit4→5 table rebuild with table-aware canonical key columns, unresolved binding discriminator, access/revision tables and content-free evidence. Copy each populated value losslessly; never mutate exported1–4.json. Retain v3→4 archival invariants and existing account settings/teardown evidence.
3. Register sequential1→2→3→4→5 and direct4→5 resolution in both Room builders; export only new5.json during authorized implementation schema generation. Verify SQL affinities/nullability/indexes through real populated SQLite migration tests, not exported text comparisons.
4. Implement validated owner initialization from restored/authenticated AccountIdentity: verify origin+user hash against opened key, atomically bind all dependent legacy rows, preserve key references and local intent versions. No default user fallback or guessed hash reversal. Unresolved partition remains local-only and offline private display survives; explicit restoration/binding unlocks real TaskRef actions.
5. Implement canonical reads/save/completion/delete/create-child and assignment desired-state operations under existing mutation/delivery admission. Enforce current local capabilities; Missing/AccessDenied/VersionConflict are typed, not retry exceptions. ChildOnly edits use trusted access metadata without loading private parent; unknown/unvalidated orphan has no shared edit path.
6. Persist assignment as independent ordinary relation intent with expected slot version, stable rowId=taskId and only recipient/target relation JSON. Wait for confirmed owner task create before initial relationship dispatch, keep pending desired choice durable, and never copy simultaneous content edits into relation data.
7. Implement table-aware staging, union identity enumeration (staged/previous access/draft/outbox/displayed), access result staging and final promotion. Require same account generation/E and final explicit empty validation confirmation. Reject duplicate/missing/wrong-owner results, stale generation/revision or incomplete pages without altering active state; scan absence is never deleted=true.
8. Implement exact accepted tuple reconciliation for frozen opId+owner/table/row/submittedVersion; newer local edit cannot be acknowledged. Unknown commit and pulled matching content preserve distinction between convergence and exact acknowledgement. Expose real storage transactions for05, no no-op placeholders.
9. Implement authoritative invalidateAccess and whole-app membership-loss scope; advance accessGeneration and retire affected staging while purging affected shared base/raw JSON/title/deadline/assignee, drafts, outbox/frozen envelopes and conflict/review payloads. Retain only canonical identity/opId/base+submitted version/timestamps/reason as non-replayable evidence; no payload quarantine. Cancel ledger eligibility and emit invalidation. Dismissal/account removal delete content-free evidence. Regrant freshly hydrates the same live canonical key without restoring discarded intents. Preserve unrelated owner-private rows/private legacy archives; capability loss hides/pauses shared data without converting it to owned private rows.
10. Adapt reminder occurrence store/ledger to canonical identity and pure projection inputs from01; cancellation and duplicate-owner suppression stay account-generation safe. Full-group automatic parent changes require reconciled coverage; partial/staged hierarchies do not enqueue parent writes.
11. Add populated migration1/2/3/4→5 and binding rollback/mismatch/restart tests, opaque JSON preservation, canonical collision/assignment tombstone, no-parent-local ChildOnly edits, unavailable purge/evidence inspection, denial-before-failed-scan, regrant/no-replay and ledger cancellation tests against real SQLite. Migrate all owned old store tests, keeping their behavior rather than deleting failing coverage.
12. Hand off implemented operations/persisted result shapes to05/06/07/08. Keep module tests with this owner; integration fixes return here, never grant phase09 ownership of DAO/store.

## Todo list
- [x] Implement numbered steps in assigned files, migrating LSP-referenced owned callers without shims.
- [x] Add/update the phase-owned behavioral/security/migration coverage specified above; no copied-source/default tests.
- [x] Implement and inspect the confirmed revoked-payload purge across active/review/staging/frozen storage; retain content-free metadata only, preserve unrelated private data and prove fresh regrant cannot replay discarded edits.
- [x] Hand off real interfaces/behavior and pending checks to main/integration owner; do not run mid-flight checks without explicit verification assignment.

## Success Criteria
- **Acceptance criterion (runtime evidence not recorded):** populated SQLite proves lossless upgrade and atomic two-stage owner binding without hash reversal; wrong account never sends/binds legacy data; offline private rows remain visible and unassigned. Reported JVM migration tests do not establish a populated runtime/coordinator inspection or every rollback/restart case.
- **Acceptance criterion (not fully evidenced):** canonical row/task+relation collisions remain distinct; incomplete scan retains base/access, authoritative denial purges only revoked shared payloads; exact ack cannot clear newer/other-owner edit; regrant does not replay. Shared suite results are reported in [Cycle 3 review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md), without per-case results here.
- **Device criterion not run:** actual Android Room instrumentation and JVM SQLite verify ChildOnly edit with parent absent locally, full-group-only completion and canceled reminder ledger. JVM SQLite/unit results do not substitute for connected-device acceptance. Schemas 1–4 remain unchanged.
- **Full runtime inspection not reported:** real SQLite inspection after authoritative denial finds no revoked content or unsent collaborative payload in base/draft/outbox/frozen/conflict/staging storage and no recovery quarantine; evidence contains only permitted identity/version/failure metadata. Dismissal/account removal clears that evidence; unrelated private rows/archives survive; restart/regrant cannot revive old intents.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Large table rebuild and nullable-key semantics can silently lose rows; explicit binding discriminator and populated-state assertions mitigate. Account restoration must deliver real identity before dispatch; do not weaken to default owner.
- Audit every active/review/staging path for revoked content, including frozen envelope/raw JSON. Retaining content-free failure evidence intentionally cannot recover unsent payloads.

## Security Considerations
- Partition hash match is mandatory. Owner-binding transaction cannot cross accounts. Capability state is local admission only; server still reauthorizes. Do not delete unrelated private tasks or private historical evidence; never log revoked payloads.

## Next steps
Deliver working storage handoff to05/06/07/08. Main runs authorized SQLite/module checks after owners migrate callers;09 receives migration behavior/evidence IDs, not source ownership.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
